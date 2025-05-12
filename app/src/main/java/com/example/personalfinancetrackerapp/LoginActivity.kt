package com.example.personalfinancetrackerapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_page)

        // Find views by their IDs
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnCreateAccount = findViewById<Button>(R.id.btnCreateAccount)

        // SharedPreferences to retrieve user data
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        // Login button click listener
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Retrieve stored credentials
            val storedUsername = sharedPreferences.getString("username", null)
            val storedPassword = sharedPreferences.getString("password", null)

            // Validate inputs
            when {
                username.isEmpty() -> {
                    etUsername.error = "Username is required"
                    etUsername.requestFocus()
                }
                password.isEmpty() -> {
                    etPassword.error = "Password is required"
                    etPassword.requestFocus()
                }
                storedUsername == null || storedPassword == null -> {
                    Toast.makeText(this, "No account found. Please sign up.", Toast.LENGTH_SHORT).show()
                }
                username != storedUsername -> {
                    etUsername.error = "Invalid username"
                    etUsername.requestFocus()
                }
                password != storedPassword -> {
                    etPassword.error = "Invalid password"
                    etPassword.requestFocus()
                }
                else -> {
                    // Successful login
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                    // Navigate to MainActivity (or your desired activity after login)
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish() // Close LoginActivity
                }
            }
        }

        // Create Account button click listener
        btnCreateAccount.setOnClickListener {
            // Navigate to SignupActivity
            val intent = Intent(this, SignupActivity::class.java)
            startActivity(intent)
            finish() // Close LoginActivity
        }
    }
}