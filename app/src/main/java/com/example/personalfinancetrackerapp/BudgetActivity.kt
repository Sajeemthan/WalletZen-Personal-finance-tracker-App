package com.example.personalfinancetrackerapp

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.personalfinancetrackerapp.data.FinanceDatabase
import com.example.personalfinancetrackerapp.model.Budget
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class BudgetActivity : AppCompatActivity() {

    private lateinit var inputBudget: EditText
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var tvCurrentBudget: TextView
    private lateinit var tvTotalSpent: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvWarning: TextView
    private lateinit var drawerLayout: DrawerLayout

    private var budget: Float = 0f
    private var totalSpent: Float = 0f

    private val transactionResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadTransactions()
            updateUI()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
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
        setContentView(R.layout.activity_budget)

        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to manage budget", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        inputBudget = findViewById(R.id.inputBudget)
        btnSave = findViewById(R.id.btnSaveBudget)
        btnReset = findViewById(R.id.btnResetBudget)
        tvCurrentBudget = findViewById(R.id.tvCurrentBudget)
        tvTotalSpent = findViewById(R.id.tvTotalSpent)
        progressBar = findViewById(R.id.progressBar)
        tvWarning = findViewById(R.id.tvWarning)

        progressBar.visibility = ProgressBar.GONE

        loadBudget()
        loadTransactions()
        updateUI()

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

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.nav_transactions -> {
                    val intent = Intent(this, TransactionActivity::class.java)
                    transactionResultLauncher.launch(intent)
                    true
                }
                R.id.nav_budget -> {
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
                else -> false
            }
        }

        btnSave.setOnClickListener {
            val input = inputBudget.text.toString().toFloatOrNull()
            if (input == null || input <= 0) {
                Toast.makeText(this, "Enter a valid budget", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            budget = input
            val db = FinanceDatabase.getDatabase(this)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val budgetEntity = Budget(currentUser, budget)
                    db.financeDao().insertBudget(budgetEntity)
                    withContext(Dispatchers.Main) {
                        updateUI()
                        Toast.makeText(this@BudgetActivity, "Budget Saved ✅", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BudgetActivity, "Error saving budget: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnReset.setOnClickListener {
            val db = FinanceDatabase.getDatabase(this)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val budgetEntity = Budget(currentUser, 0f)
                    db.financeDao().insertBudget(budgetEntity)
                    withContext(Dispatchers.Main) {
                        budget = 0f
                        updateUI()
                        Toast.makeText(this@BudgetActivity, "Budget has been reset", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BudgetActivity, "Error resetting budget: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadBudget() {
        val db = FinanceDatabase.getDatabase(this)
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val budgetEntity = db.financeDao().getBudget(currentUser)
                withContext(Dispatchers.Main) {
                    budget = budgetEntity?.amount ?: 0f
                    updateUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BudgetActivity, "Error loading budget: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTransactions() {
        val db = FinanceDatabase.getDatabase(this)
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactions = db.financeDao().getAllTransactions().filter { it.user == currentUser }
                withContext(Dispatchers.Main) {
                    totalSpent = transactions.sumOf { it.amount }.toFloat()
                    updateUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BudgetActivity, "Error loading transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUI() {
        tvCurrentBudget.text = if (budget == 0f) "No budget set" else "Current Budget: $${"%.2f".format(budget)}"
        tvTotalSpent.text = "Total Spent: $${"%.2f".format(totalSpent)}"

        if (budget == 0f) {
            progressBar.visibility = ProgressBar.GONE
            tvWarning.text = "⚠️ Budget not set!"
        } else {
            progressBar.visibility = ProgressBar.VISIBLE
            val percentageUsed = (totalSpent / budget) * 100
            progressBar.progress = percentageUsed.toInt().coerceIn(0, 100) // Ensure progress stays within 0-100

            tvWarning.text = when {
                percentageUsed > 100 -> "❌ You have exceeded your budget!"
                percentageUsed > 80 -> "⚠️ You're close to exceeding your budget!"
                else -> ""
            }
        }
    }

    private fun checkStoragePermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, MediaStore doesn't require storage permission
            exportTransactions()
        } else {
            // For Android 9 and below, check WRITE_EXTERNAL_STORAGE permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                exportTransactions()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
                val transactions = db.financeDao().getAllTransactions().filter { it.user == currentUser }
                val gson = Gson()
                val json = gson.toJson(transactions)
                withContext(Dispatchers.Main) {
                    if (json.isEmpty() || transactions.isEmpty()) {
                        Toast.makeText(this@BudgetActivity, "No transactions to export", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this@BudgetActivity, "✅ Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                        } ?: run {
                            Toast.makeText(this@BudgetActivity, "❌ Failed to export: Unable to open output stream", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@BudgetActivity, "❌ Failed to export: Unable to create file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BudgetActivity, "Error exporting transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}