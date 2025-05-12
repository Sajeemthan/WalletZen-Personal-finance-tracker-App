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
import com.google.android.material.navigation.NavigationView
import com.example.personalfinancetrackerapp.utils.NotificationHelper
import java.io.OutputStream
import java.util.*

class ReminderSettingsActivity : AppCompatActivity() {

    private lateinit var tvTime: TextView
    private lateinit var drawerLayout: DrawerLayout
    private val TAG = "ReminderSettingsActivity"

    // Permission request launcher for notifications on Android 13+
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, proceed with scheduling
            val prefs = getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
            val hour = prefs.getInt("hour", -1)
            val minute = prefs.getInt("minute", -1)

            if (hour != -1 && minute != -1) {
                scheduleReminder(hour, minute)
                Toast.makeText(this, "Reminder scheduled", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Notification permission denied. Reminders won't work.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder_settings)

        // Create notification channel early
        NotificationHelper.createNotificationChannel(this)

        // Setup Toolbar
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Setup DrawerLayout and NavigationView
        drawerLayout = findViewById(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Handle NavigationView item clicks
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
                    exportTransactions()
                    true
                }
                else -> false
            }
        }

        // SharedPreferences for reminders, feedback, and user data
        val reminderPrefs = getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val editor = userPrefs.edit()

        // Get username from SharedPreferences
        val username = userPrefs.getString("username", "Anonymous") ?: "Anonymous"
        Log.d(TAG, "Username retrieved: $username")

        // Reminder Section
        val btnSetTime = findViewById<Button>(R.id.btnSetTime)
        tvTime = findViewById(R.id.tvCurrentTime)
        checkNotificationPermission()
        loadSavedTime()

        btnSetTime.setOnClickListener {
            val now = Calendar.getInstance()
            val hour = now.get(Calendar.HOUR_OF_DAY)
            val minute = now.get(Calendar.MINUTE)

            TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                saveReminderTime(selectedHour, selectedMinute)
                tvTime.text = "Reminder set for: %02d:%02d".format(selectedHour, selectedMinute)

                if (hasNotificationPermission()) {
                    scheduleReminder(selectedHour, selectedMinute)
                    Toast.makeText(this, "Daily reminder set", Toast.LENGTH_SHORT).show()
                } else {
                    requestNotificationPermission()
                }
            }, hour, minute, true).show()
        }

        // Feedback Section
        val etFeedback = findViewById<EditText>(R.id.etFeedback)
        val btnSubmitFeedback = findViewById<Button>(R.id.btnSubmitFeedback)
        val reviewsContainer = findViewById<LinearLayout>(R.id.reviewsContainer)

        // Load existing reviews
        loadReviews(reviewsContainer, userPrefs)

        // Submit Feedback
        btnSubmitFeedback.setOnClickListener {
            val feedback = etFeedback.text.toString().trim()
            if (feedback.isEmpty()) {
                etFeedback.error = "Feedback cannot be empty"
                etFeedback.requestFocus()
                return@setOnClickListener
            }

            // Generate a unique key for the feedback
            val feedbackId = UUID.randomUUID().toString()
            val feedbackData = "$username||$feedback"
            editor.putString("feedback_$feedbackId", feedbackData)
            editor.apply()
            Log.d(TAG, "Feedback stored: feedback_$feedbackId = $feedbackData")

            // Clear feedback input
            etFeedback.text.clear()

            // Show success message
            Toast.makeText(this, "Feedback submitted!", Toast.LENGTH_SHORT).show()

            // Reload reviews
            loadReviews(reviewsContainer, userPrefs)
        }
    }

    private fun loadReviews(container: LinearLayout, sharedPreferences: android.content.SharedPreferences) {
        // Clear existing views
        container.removeAllViews()
        Log.d(TAG, "Cleared reviews container")

        // Get all feedback entries
        val allEntries = sharedPreferences.all
        Log.d(TAG, "All SharedPreferences entries: $allEntries")

        val feedbackEntries = allEntries.filterKeys { it.startsWith("feedback_") }
            .values
            .mapNotNull { it as? String }
        Log.d(TAG, "Feedback entries: $feedbackEntries")

        if (feedbackEntries.isEmpty()) {
            Log.d(TAG, "No feedback entries found")
            val noReviewsTextView = TextView(this).apply {
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
            return
        }

        // Add each review to the container
        feedbackEntries.forEach { entry ->
            Log.d(TAG, "Processing feedback entry: $entry")
            val parts = entry.split("||", limit = 2)
            if (parts.size != 2) {
                Log.e(TAG, "Invalid feedback format: $entry")
                return@forEach
            }
            val (username, feedback) = parts

            // Create a CardView for each review
            val cardView = androidx.cardview.widget.CardView(this).apply {
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

            // Create a LinearLayout inside the CardView
            val innerLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundResource(R.drawable.transparent_label_bg)
            }

            // Username TextView
            val usernameTextView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                var printfeed = "$username: $feedback"
                text = printfeed
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8)
            }


            // Add TextViews to inner layout
            innerLayout.addView(usernameTextView)

            // Add inner layout to CardView
            cardView.addView(innerLayout)

            // Add CardView to container
            container.addView(cardView)
            Log.d(TAG, "Added review: $username - $feedback")
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // For versions below Android 13, no runtime permission needed
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

    private fun saveReminderTime(hour: Int, minute: Int) {
        val prefs = getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("hour", hour).putInt("minute", minute).apply()
    }

    private fun loadSavedTime() {
        val prefs = getSharedPreferences("ReminderPrefs", Context.MODE_PRIVATE)
        val hour = prefs.getInt("hour", -1)
        val minute = prefs.getInt("minute", -1)

        if (hour != -1) {
            tvTime.text = "Reminder set for: %02d:%02d".format(hour, minute)

            if (hasNotificationPermission()) {
                scheduleReminder(hour, minute)
            }
        }
    }

    private fun scheduleReminder(hour: Int, minute: Int) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, ReminderReceiver::class.java)

            // Use unique request code based on time to avoid conflicts
            val requestCode = (hour * 60 + minute)

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Cancel any existing alarms with the same ID
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

            // Use EXACT scheduling where available
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
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting reminder: ${e.message}", Toast.LENGTH_SHORT).show()
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

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}