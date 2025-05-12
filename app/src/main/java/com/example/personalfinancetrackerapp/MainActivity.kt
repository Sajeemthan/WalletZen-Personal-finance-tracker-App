package com.example.personalfinancetrackerapp

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.OutputStream

import android.content.pm.PackageManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView


class MainActivity : AppCompatActivity() {

    private lateinit var tvWarningMain: TextView
    private lateinit var drawerLayout: DrawerLayout

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Set up drawer layout
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
        welcomeText.text = "Welcome Back"

        // Set up floating action button for adding transactions
        val fabAddTransaction = findViewById<FloatingActionButton>(R.id.fabAddTransaction)
        fabAddTransaction.setOnClickListener {
            startActivity(Intent(this, TransactionActivity::class.java))
        }

        // Set up navigation drawer
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener { item ->
            // Close drawer when item is tapped
            drawerLayout.closeDrawer(GravityCompat.START)

            when (item.itemId) {
                R.id.nav_home -> {
                    // Already on home screen
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
                    exportTransactions()
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

        createNotificationChannel() // Create notification channel on app start

        // Ask for notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    // Handle back press to close drawer first if it's open
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
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
                exportTransactions()
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
        val prefs = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        val budget = prefs.getFloat("budget", 0f)

        val json = prefs.getString("transactions", null)
        val type = object : TypeToken<List<Transaction>>() {}.type
        val transactions: List<Transaction> = if (json != null) Gson().fromJson(json, type) else emptyList()

        val totalSpent = transactions.sumOf { it.amount }.toFloat()
        val percentageUsed = if (budget > 0) (totalSpent / budget) * 100 else 0f

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

    private fun sendBudgetNotification(message: String) {
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

    private fun exportTransactions() {
        val prefs = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("transactions", null)

        if (json == null) {
            Toast.makeText(this, "No transactions to export", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "transactions_backup.json"

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
                Toast.makeText(this, "✅ Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "❌ Failed to export", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importJsonFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val json = inputStream?.bufferedReader().use { it?.readText() }

            if (!json.isNullOrEmpty()) {
                getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
                    .edit().putString("transactions", json).apply()

                Toast.makeText(this, "✅ Transactions imported!", Toast.LENGTH_SHORT).show()
                showBudgetWarning(tvWarningMain)
            } else {
                Toast.makeText(this, "⚠️ File is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Failed to import: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
