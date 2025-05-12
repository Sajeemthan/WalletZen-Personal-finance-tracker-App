package com.example.personalfinancetrackerapp

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
            calculateTotalSpent()
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget)

        // Check if user is logged in
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

        progressBar.visibility = ProgressBar.GONE // 🧹 Remove spinner

        loadBudget()
        calculateTotalSpent()
        updateUI()

        // Set up toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
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

        // Set up navigation drawer
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
                    exportTransactions()
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
            getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
                .edit().putFloat("budget_$currentUser", budget).apply()

            updateUI()
            Toast.makeText(this, "Budget Saved ✅", Toast.LENGTH_SHORT).show()
        }

        btnReset.setOnClickListener {
            val prefs = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
            prefs.edit().remove("budget_$currentUser").apply()
            budget = 0f
            updateUI()
            Toast.makeText(this, "Budget has been reset", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBudget() {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null) ?: return
        val prefs = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        budget = prefs.getFloat("budget_$currentUser", 0f)
    }

    private fun calculateTotalSpent() {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null) ?: return
        val prefs = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("transactions_$currentUser", null)
        val type = object : TypeToken<List<Transaction>>() {}.type
        val transactions: List<Transaction> = if (json != null) {
            try {
                Gson().fromJson(json, type)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        totalSpent = transactions.sumOf { it.amount }.toFloat()
    }

    private fun updateUI() {
        tvCurrentBudget.text = if (budget == 0f) "No budget set" else "Current Budget: $${"%.2f".format(budget)}"
        tvTotalSpent.text = "Total Spent: $${"%.2f".format(totalSpent)}"

        val percentageUsed = if (budget > 0) (totalSpent / budget) * 100 else 0f
        progressBar.progress = percentageUsed.toInt()

        tvWarning.text = when {
            budget == 0f -> "⚠️ Budget not set!"
            percentageUsed > 100 -> "❌ You have exceeded your budget!"
            percentageUsed > 80 -> "⚠️ You're close to exceeding your budget!"
            else -> ""
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

        val prefs = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString("transactions_$currentUser", null)

        if (json == null) {
            Toast.makeText(this, "No transactions to export", Toast.LENGTH_SHORT).show()
            return
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
                Toast.makeText(this, "✅ Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "❌ Failed to export", Toast.LENGTH_SHORT).show()
        }
    }
}