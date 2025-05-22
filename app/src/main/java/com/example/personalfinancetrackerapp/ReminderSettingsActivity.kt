package com.example.personalfinancetrackerapp

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.example.personalfinancetrackerapp.data.FinanceDatabase
import com.example.personalfinancetrackerapp.model.Feedback
import com.example.personalfinancetrackerapp.model.Preference
import com.example.personalfinancetrackerapp.utils.NotificationHelper
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.*

class ReminderSettingsActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var drawerLayout: DrawerLayout
    private val TAG = "ReminderSettingsActivity"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val currentUser = userPrefs.getString("username", null) ?: return@registerForActivityResult
            val db = FinanceDatabase.getDatabase(this)
            CoroutineScope(Dispatchers.IO).launch {
                val pref = db.financeDao().getPreference(currentUser)
                if (pref != null && pref.reminderHour != -1 && pref.reminderMinute != -1) {
                    scheduleReminder(pref.reminderHour, pref.reminderMinute)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ReminderSettingsActivity, "Reminder scheduled", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Notification permission denied. Reminders won't work.", Toast.LENGTH_LONG).show()
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
        setContentView(R.layout.activity_reminder_settings)

        NotificationHelper.createNotificationChannel(this)

        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to set reminders", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

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
                    startActivity(Intent(this, CategoryChartActivity::class.java))
                    true
                }
                R.id.nav_reminder -> {
                    true
                }
                R.id.menu_export -> {
                    checkStoragePermissionAndExport()
                    true
                }
                else -> false
            }
        }

        val db = FinanceDatabase.getDatabase(this)
        val btnSetTime = findViewById<Button>(R.id.btnSetTime)
        tvTime = findViewById(R.id.tvCurrentTime)
        checkNotificationPermission()
        loadSavedTime(db, currentUser)

        btnSetTime.setOnClickListener {
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val minute = now.get(Calendar.MINUTE)

            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                saveReminderTime(db, currentUser, selectedHour, selectedMinute)
                tvTime.text = "Reminder set for: %02d:%02d".format(selectedHour, selectedMinute)

                if (hasNotificationPermission()) {
                    scheduleReminder(selectedHour, selectedMinute)
                    Toast.makeText(this, "Daily reminder set", Toast.LENGTH_SHORT).show()
                } else {
                    requestNotificationPermission()
                }
            }, hour, minute, true).show()
        }

        val etFeedback = findViewById<EditText>(R.id.etFeedback)
        val btnSubmitFeedback = findViewById<Button>(R.id.btnSubmitFeedback)
        val reviewsContainer = findViewById<LinearLayout>(R.id.reviewsContainer)

        loadReviews(db, reviewsContainer)

        btnSubmitFeedback.setOnClickListener {
            val feedback = etFeedback.text.toString().trim()
            if (feedback.isEmpty()) {
                etFeedback.error = "Feedback cannot be empty"
                etFeedback.requestFocus()
                return@setOnClickListener
            }

            val feedbackEntity = Feedback(username = currentUser, comment = feedback)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.financeDao().insertFeedback(feedbackEntity)
                    withContext(Dispatchers.Main) {
                        etFeedback.text.clear()
                        Toast.makeText(this@ReminderSettingsActivity, "Feedback submitted!", Toast.LENGTH_SHORT).show()
                        loadReviews(db, reviewsContainer)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ReminderSettingsActivity, "Error submitting feedback: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun loadReviews(db: FinanceDatabase, container: LinearLayout) {
        container.removeAllViews()
        Log.d(TAG, "Cleared reviews container")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val feedbackList = db.financeDao().getAllFeedback()
                withContext(Dispatchers.Main) {
                    if (feedbackList.isEmpty()) {
                        val noReviewsTextView = TextView(this@ReminderSettingsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            text = "No reviews yet"
                            textSize = 14f
                            setTextColor(android.graphics.Color.WHITE)
                            setPadding(0, 0, 0, 8)
                        }
                        container.addView(noReviewsTextView)
                        return@withContext
                    }

                    feedbackList.forEach { feedback ->
                        val cardView = androidx.cardview.widget.CardView(this@ReminderSettingsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, 16)
                            }
                            radius = 12f
                            cardElevation = 8f
                            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }

                        val innerLayout = LinearLayout(this@ReminderSettingsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            orientation = LinearLayout.VERTICAL
                            setPadding(16, 16, 16, 16)
                            setBackgroundResource(R.drawable.transparent_label_bg)
                        }

                        val usernameTextView = TextView(this@ReminderSettingsActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            text = "${feedback.username}: ${feedback.comment}"
                            textSize = 16f
                            setTextColor(android.graphics.Color.WHITE)
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setPadding(0, 0, 0, 8)
                        }

                        innerLayout.addView(usernameTextView)
                        cardView.addView(innerLayout)
                        container.addView(cardView)
                        Log.d(TAG, "Added review: ${feedback.username} - ${feedback.comment}")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReminderSettingsActivity, "Error loading reviews: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission()) {
                requestNotificationPermission()
            }
        }
    }

    private fun saveReminderTime(db: FinanceDatabase, username: String, hour: Int, minute: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pref = db.financeDao().getPreference(username) ?: Preference(username)
                val updatedPref = pref.copy(reminderHour = hour, reminderMinute = minute)
                db.financeDao().insertPreference(updatedPref)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReminderSettingsActivity, "Error saving reminder time: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadSavedTime(db: FinanceDatabase, username: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val pref = db.financeDao().getPreference(username)
                withContext(Dispatchers.Main) {
                    if (pref != null && pref.reminderHour != -1) {
                        tvTime.text = "Reminder set for: %02d:%02d".format(pref.reminderHour, pref.reminderMinute)
                        if (hasNotificationPermission()) {
                            scheduleReminder(pref.reminderHour, pref.reminderMinute)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReminderSettingsActivity, "Error loading reminder time: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun scheduleReminder(hour: Int, minute: Int) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, ReminderReceiver::class.java)
            val requestCode = (hour * 60 + minute)

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            alarmManager.cancel(pendingIntent)

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            }
            Log.d(TAG, "Reminder scheduled for $hour:$minute")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling reminder: ${e.message}")
            Toast.makeText(this, "Error setting reminder: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@ReminderSettingsActivity, "No transactions to export", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this@ReminderSettingsActivity, "✅ Exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
                        } ?: run {
                            Toast.makeText(this@ReminderSettingsActivity, "❌ Failed to export: Unable to open output stream", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@ReminderSettingsActivity, "❌ Failed to export: Unable to create file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ReminderSettingsActivity, "Error exporting transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
}