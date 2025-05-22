package com.example.personalfinancetrackerapp

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.personalfinancetrackerapp.data.FinanceDatabase
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var tvWarningMain: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var logoutButton: Button

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                importJsonFromUri(uri)
            }
        }
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            exportTransactions()
        } else {
            Toast.makeText(this, "Storage permission denied. Cannot export transactions.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to continue", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawerLayout)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val welcomeText = findViewById<TextView>(R.id.tvWelcome)
        tvWarningMain = findViewById(R.id.tvBudgetWarningMain)
        logoutButton = findViewById(R.id.btnLogout)
        welcomeText.text = "Welcome Back, $currentUser"

        val fabAddTransaction = findViewById<FloatingActionButton>(R.id.fabAddTransaction)
        fabAddTransaction.setOnClickListener {
            startActivity(Intent(this, TransactionActivity::class.java))
        }

        logoutButton.setOnClickListener {
            performLogout()
        }

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_home -> {
                    true
                }
                R.id.nav_transactions -> {
                    startActivity(Intent(this, TransactionListActivity::class.java))
                    true
                }
                R.id.nav_budget -> {
                    startActivity(Intent(this, BudgetActivity::class.java))
                    true
                }
                R.id.nav_chart -> {
                    startActivity(Intent(this, CategoryChartActivity::class.java))
                    true
                }
                R.id.nav_reminder -> {
                    startActivity(Intent(this, ReminderSettingsActivity::class.java))
                    true
                }
                R.id.menu_export -> {
                    checkStoragePermissionAndExport()
                    true
                }
                R.id.menu_import -> {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    importJsonLauncher.launch(intent)
                    true
                }
                else -> false
            }
        }

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_export -> {
                checkStoragePermissionAndExport()
                true
            }
            R.id.menu_import -> {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                importJsonLauncher.launch(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        showBudgetWarning(tvWarningMain)
    }

    private fun showBudgetWarning(warningTextView: TextView) {
        val db = FinanceDatabase.getDatabase(this)
        val currentUser = getCurrentUsername() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val budgetEntity = db.financeDao().getBudget(currentUser)
                val transactions = db.financeDao().getTransactionsByUser(currentUser)
                val budget = budgetEntity?.amount ?: 0f
                val totalSpent = transactions.sumOf { it.amount }.toFloat()
                val percentageUsed = if (budget > 0) (totalSpent / budget) * 100 else 0f

                withContext(Dispatchers.Main) {
                    val warningText = when {
                        budget == 0f -> "⚠️ Budget not set!"
                        percentageUsed > 100 -> {
                            sendBudgetNotification("❌ You've exceeded your budget!")
                            "❌ You have exceeded your budget!"
                        }
                        percentageUsed > 80 -> {
                            sendBudgetNotification("⚠️ You're close to exceeding your budget.")
                            "⚠️ You're close to exceeding your budget!"
                        }
                        else -> ""
                    }
                    warningTextView.text = warningText
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading budget data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendBudgetNotification(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(this, "budget_alerts")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("💰 Budget Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        with(NotificationManagerCompat.from(this)) {
            notify(1001, builder.build())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "budget_alerts",
                "Budget Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for approaching or exceeded budgets"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun checkStoragePermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportTransactions()
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                exportTransactions()
            } else {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun exportTransactions() {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to export transactions", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val db = FinanceDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactions = db.financeDao().getTransactionsByUser(currentUser)
                val gson = Gson()
                val json = gson.toJson(transactions)
                withContext(Dispatchers.Main) {
                    if (json.isEmpty() || transactions.isEmpty()) {
                        Toast.makeText(this@MainActivity, "No transactions to export", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val fileName = "transactions_${currentUser}_backup.json"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }

                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                        outputStream?.use {
                            it.write(json.toByteArray())
                            it.flush()
                            Toast.makeText(this@MainActivity, "✅ Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                        } ?: run {
                            Toast.makeText(this@MainActivity, "❌ Failed to export: Unable to open output stream", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "❌ Failed to export: Unable to create file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error exporting transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importJsonFromUri(uri: Uri) {
        val currentUser = getCurrentUsername() ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val json = inputStream?.bufferedReader().use { it?.readText() }

                if (!json.isNullOrEmpty()) {
                    val gson = Gson()
                    val type = object : TypeToken<List<Transaction>>() {}.type
                    val transactions = gson.fromJson<List<Transaction>>(json, type)
                    val db = FinanceDatabase.getDatabase(this@MainActivity)

                    val userTransactions = transactions.map { it.copy(user = currentUser) }
                    userTransactions.forEach { db.financeDao().insertTransaction(it) }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "✅ Transactions imported!", Toast.LENGTH_SHORT).show()
                        showBudgetWarning(tvWarningMain)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "⚠️ File is empty", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "❌ Failed to import: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getCurrentUsername(): String? {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        return userPrefs.getString("username", null)
    }

    private fun performLogout() {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        userPrefs.edit().remove("username").apply()
        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}