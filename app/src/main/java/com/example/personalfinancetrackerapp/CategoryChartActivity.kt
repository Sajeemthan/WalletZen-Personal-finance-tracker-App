package com.example.personalfinancetrackerapp

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.personalfinancetrackerapp.data.FinanceDatabase
import com.example.personalfinancetrackerapp.model.Transaction
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class CategoryChartActivity : AppCompatActivity() {

    private lateinit var pieChart: PieChart
    private lateinit var drawerLayout: DrawerLayout

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
        setContentView(R.layout.activity_category_chart)

        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to view spending chart", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        pieChart = findViewById(R.id.pieChart)

        val db = FinanceDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactions = db.financeDao().getAllTransactions().filter { it.user == currentUser }
                val categoryTotals = calculateTotals(transactions)
                withContext(Dispatchers.Main) {
                    setupPieChart(categoryTotals)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CategoryChartActivity, "Error loading transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
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
                    startActivity(Intent(this, TransactionActivity::class.java))
                    true
                }
                R.id.nav_budget -> {
                    startActivity(Intent(this, BudgetActivity::class.java))
                    true
                }
                R.id.nav_chart -> {
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
    }

    private fun calculateTotals(transactions: List<Transaction>): Map<String, Float> {
        val categoryMap = mutableMapOf<String, Float>()
        for (t in transactions) {
            val current = categoryMap[t.category] ?: 0f
            categoryMap[t.category] = current + t.amount.toFloat()
        }
        return categoryMap
    }

    private fun setupPieChart(categoryTotals: Map<String, Float>) {
        if (categoryTotals.isEmpty()) {
            pieChart.data = null
            pieChart.centerText = "No Transactions Available"
            pieChart.description.isEnabled = false
            pieChart.invalidate()
            return
        }

        val entries = ArrayList<PieEntry>()
        for ((category, amount) in categoryTotals) {
            entries.add(PieEntry(amount, category))
        }

        val dataSet = PieDataSet(entries, "Category-wise Spending")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextColor = Color.BLACK
        dataSet.valueTextSize = 14f

        val data = PieData(dataSet)

        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.centerText = "Spending by Category"
        pieChart.setEntryLabelColor(Color.BLACK)
        pieChart.animateY(1000)
        pieChart.invalidate()
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
                        Toast.makeText(this@CategoryChartActivity, "No transactions to export", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this@CategoryChartActivity, "✅ Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                        } ?: run {
                            Toast.makeText(this@CategoryChartActivity, "❌ Failed to export: Unable to open output stream", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@CategoryChartActivity, "❌ Failed to export: Unable to create file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CategoryChartActivity, "Error exporting transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}