package com.example.personalfinancetrackerapp

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.OutputStream

class TransactionListActivity : AppCompatActivity() {

    private lateinit var transactionRecycler: RecyclerView
    private lateinit var transactionList: MutableList<Transaction>
    private lateinit var adapter: TransactionAdapter
    private lateinit var drawerLayout: DrawerLayout

    private val transactionResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            reloadTransactions() // Refresh the transaction list
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_list)

        // Check if user is logged in
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to view transactions", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        transactionRecycler = findViewById(R.id.recyclerTransactions)
        transactionRecycler.layoutManager = LinearLayoutManager(this)

        transactionList = loadTransactions()
        adapter = TransactionAdapter(transactionList, this)
        transactionRecycler.adapter = adapter

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
    }

    override fun onResume() {
        super.onResume()
        reloadTransactions()
    }

    private fun reloadTransactions() {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null) ?: return

        val sharedPref = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        val userTransactionKey = "transactions_$currentUser"
        val json = sharedPref.getString(userTransactionKey, null)
        val type = object : TypeToken<MutableList<Transaction>>() {}.type

        val updatedList: MutableList<Transaction> = if (json != null) {
            try {
                Gson().fromJson(json, type)
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
        }

        transactionList.clear()
        transactionList.addAll(updatedList)
        adapter.updateTransactions(updatedList)
    }

    private fun loadTransactions(): MutableList<Transaction> {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            return mutableListOf()
        }

        val sharedPref = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        val userTransactionKey = "transactions_$currentUser"
        val json = sharedPref.getString(userTransactionKey, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<Transaction>>() {}.type
            try {
                Gson().fromJson(json, type)
            } catch (e: Exception) {
                mutableListOf()
            }
        } else {
            mutableListOf()
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

        val sharedPref = getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
        val userTransactionKey = "transactions_$currentUser"
        val json = sharedPref.getString(userTransactionKey, null)

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