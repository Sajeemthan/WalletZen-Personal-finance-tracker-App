package com.example.personalfinancetrackerapp

import android.Manifest
import android.app.DatePickerDialog
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
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class TransactionActivity : AppCompatActivity() {

    private lateinit var titleInput: EditText
    private lateinit var amountInput: EditText
    private lateinit var categorySpinner: Spinner
    private lateinit var dateText: TextView
    private lateinit var saveBtn: Button
    private lateinit var drawerLayout: DrawerLayout

    private var selectedDate: String = ""
    private var editId: Int = -1 // For updating existing transaction

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
        setContentView(R.layout.activity_transaction)

        titleInput = findViewById(R.id.inputTitle)
        amountInput = findViewById(R.id.inputAmount)
        categorySpinner = findViewById(R.id.spinnerCategory)
        dateText = findViewById(R.id.textDate)
        saveBtn = findViewById(R.id.btnSave)

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
                else -> false
            }
        }

        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to add transactions", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupCategorySpinner()
        setupDatePicker()

        val editJson = intent.getStringExtra("editTransaction")
        editId = intent.getIntExtra("id", -1)

        if (editJson != null) {
            val gson = Gson()
            val transaction = gson.fromJson(editJson, Transaction::class.java)
            titleInput.setText(transaction.title)
            amountInput.setText(transaction.amount.toString())
            selectedDate = transaction.date
            dateText.text = selectedDate

            val categories = arrayOf("Food", "Transport", "Bills", "Shopping", "Other")
            val index = categories.indexOf(transaction.category)
            if (index >= 0) categorySpinner.setSelection(index)
            editId = transaction.id
        }

        saveBtn.setOnClickListener {
            val title = titleInput.text.toString()
            val amount = amountInput.text.toString().toDoubleOrNull()
            val category = categorySpinner.selectedItem.toString()
            val date = selectedDate

            if (title.isBlank() || amount == null || date.isBlank()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val transaction = Transaction(
                id = if (editId == -1) 0 else editId,
                title = title,
                amount = amount,
                category = category,
                date = date,
                user = currentUser // Assign the current user
            )

            val db = FinanceDatabase.getDatabase(this)
            CoroutineScope(Dispatchers.IO).launch {
                if (editId == -1) {
                    db.financeDao().insertTransaction(transaction)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TransactionActivity, "Transaction Saved ✅", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    db.financeDao().updateTransaction(transaction)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@TransactionActivity, "Transaction Updated ✅", Toast.LENGTH_SHORT).show()
                    }
                }
                withContext(Dispatchers.Main) {
                    val resultIntent = Intent()
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
    }

    private fun setupCategorySpinner() {
        val categories = arrayOf("Food", "Transport", "Bills", "Shopping", "Other")
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        categorySpinner.adapter = adapter
    }

    private fun setupDatePicker() {
        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        selectedDate = formatter.format(calendar.time)
        dateText.text = selectedDate

        dateText.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    selectedDate = formatter.format(calendar.time)
                    dateText.text = selectedDate
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
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
            // Fetch transactions for the current user only
            val transactions = db.financeDao().getAllTransactions().filter { it.user == currentUser }
            val gson = Gson()
            val json = gson.toJson(transactions)
            withContext(Dispatchers.Main) {
                if (json.isEmpty() || transactions.isEmpty()) {
                    Toast.makeText(this@TransactionActivity, "No transactions to export", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@TransactionActivity, "✅ Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                    } ?: run {
                        Toast.makeText(this@TransactionActivity, "❌ Failed to export: Unable to open output stream", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@TransactionActivity, "❌ Failed to export: Unable to create file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}