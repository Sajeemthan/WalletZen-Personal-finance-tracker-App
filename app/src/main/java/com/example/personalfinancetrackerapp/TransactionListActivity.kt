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
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.personalfinancetrackerapp.data.FinanceDatabase
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

class TransactionListActivity : AppCompatActivity() {

    private lateinit var transactionRecycler: RecyclerView
    private lateinit var transactionList: MutableList<Transaction>
    private lateinit var adapter: TransactionAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyStateView: TextView
    private val TAG = "TransactionListActivity"

    private val transactionResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            reloadTransactions()
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
        setContentView(R.layout.activity_transaction_list)

        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to view transactions", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        transactionRecycler = findViewById(R.id.recyclerTransactions)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        emptyStateView = findViewById(R.id.tvEmptyState)
        transactionRecycler.layoutManager = LinearLayoutManager(this)

        transactionList = mutableListOf()
        adapter = TransactionAdapter(transactionList, this)
        transactionRecycler.adapter = adapter

        swipeRefreshLayout.setOnRefreshListener {
            reloadTransactions()
            swipeRefreshLayout.isRefreshing = false
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

        reloadTransactions()
    }

    override fun onResume() {
        super.onResume()
        reloadTransactions()
    }

    private fun reloadTransactions() {
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Log.w(TAG, "No logged-in user found, redirecting to LoginActivity")
            Toast.makeText(this, "Please log in to view transactions", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val normalizedUser = currentUser.trim().lowercase()
        Log.d(TAG, "Fetching transactions for normalized user: $normalizedUser")

        val db = FinanceDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Debug: Fetch all transactions to see what's in the database
                val allTransactions = db.financeDao().getAllTransactions()
                Log.d(TAG, "All transactions in database: $allTransactions")

                // Fetch transactions for the current user
                val transactions = db.financeDao().getTransactionsByUser(normalizedUser)
                Log.d(TAG, "Fetched transactions for user $normalizedUser: $transactions")

                withContext(Dispatchers.Main) {
                    transactionList.clear()
                    transactionList.addAll(transactions)
                    adapter.updateTransactions(transactionList)
                    Log.d(TAG, "Updated transaction list with ${transactionList.size} items")

                    if (transactions.isEmpty()) {
                        Log.w(TAG, "No transactions found for user $normalizedUser")
                        emptyStateView.visibility = View.VISIBLE
                        transactionRecycler.visibility = View.GONE
                    } else {
                        emptyStateView.visibility = View.GONE
                        transactionRecycler.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error fetching transactions: ${e.message}")
                    Toast.makeText(this@TransactionListActivity, "Error fetching transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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

        val normalizedUser = currentUser.trim().lowercase()
        val db = FinanceDatabase.getDatabase(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactions = db.financeDao().getTransactionsByUser(normalizedUser)
                val gson = Gson()
                val json = gson.toJson(transactions)
                withContext(Dispatchers.Main) {
                    if (json.isEmpty() || transactions.isEmpty()) {
                        Toast.makeText(this@TransactionListActivity, "No transactions to export", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    val fileName = "transactions_${normalizedUser}_backup.json"
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
                            Toast.makeText(this@TransactionListActivity, "✅ Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                        } ?: run {
                            Toast.makeText(this@TransactionListActivity, "❌ Failed to export: Unable to open output stream", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@TransactionListActivity, "❌ Failed to export: Unable to create file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e(TAG, "Error exporting transactions: ${e.message}")
                    Toast.makeText(this@TransactionListActivity, "Error exporting transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}