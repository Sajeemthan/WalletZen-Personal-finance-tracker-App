package com.example.personalfinancetrackerapp

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private var editPosition: Int = -1 // for updating existing transaction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)

        titleInput = findViewById(R.id.inputTitle)
        amountInput = findViewById(R.id.inputAmount)
        categorySpinner = findViewById(R.id.spinnerCategory)
        dateText = findViewById(R.id.textDate)
        saveBtn = findViewById(R.id.btnSave)

        //-----------------------------
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

        // Set up navigation drawer
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener { item ->
            // Close drawer when item is tapped
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
                    exportTransactions()
                    true
                }

                else -> false
            }
        }


        //------------------------------

        setupCategorySpinner()
        setupDatePicker()

        // ✅ Check if this is an Edit operation
        val editJson = intent.getStringExtra("editTransaction")
        editPosition = intent.getIntExtra("position", -1)

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
                id = Random().nextInt(9999),
                title = title,
                amount = amount,
                category = category,
                date = date
            )

            // ✅ Save or Update in SharedPreferences
            val sharedPref = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
            val gson = Gson()

            val existingJson = sharedPref.getString("transactions", null)
            val type = object : TypeToken<MutableList<Transaction>>() {}.type
            val transactionList: MutableList<Transaction> = if (existingJson != null) {
                gson.fromJson(existingJson, type)
            } else {
                mutableListOf()
            }

            if (editPosition != -1 && editPosition < transactionList.size) {
                transactionList[editPosition] = transaction
                Toast.makeText(this, "Transaction Updated ✅", Toast.LENGTH_SHORT).show()
            } else {
                transactionList.add(transaction)
                Toast.makeText(this, "Transaction Saved ✅", Toast.LENGTH_SHORT).show()
            }

            val updatedJson = gson.toJson(transactionList)
            sharedPref.edit().putString("transactions", updatedJson).apply()

            finish()
        }
    }

    private fun setupCategorySpinner() {
        val categories = arrayOf("Food", "Transport", "Bills", "Shopping", "Other")
        
        // Use custom layouts for spinner items to ensure text visibility
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
            DatePickerDialog(this,
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


}
