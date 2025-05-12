package com.example.personalfinancetrackerapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SignupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup)

        // Find views by their IDs
        val etSignupUsername = findViewById<EditText>(R.id.etSignupUsername)
        val etSignupEmail = findViewById<EditText>(R.id.etSignupEmail)
        val etSignupPassword = findViewById<EditText>(R.id.etSignupPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        val btnBackToLogin = findViewById<Button>(R.id.btnBackToLogin)

        // SharedPreferences to store user data
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        // Sign Up button click listener
        btnSignup.setOnClickListener {
            val username = etSignupUsername.text.toString().trim()
            val email = etSignupEmail.text.toString().trim()
            val password = etSignupPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            // Validate inputs
            when {
                username.isEmpty() -> {
                    etSignupUsername.error = "Username is required"
                    etSignupUsername.requestFocus()
                }
                email.isEmpty() -> {
                    etSignupEmail.error = "Email is required"
                    etSignupEmail.requestFocus()
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    etSignupEmail.error = "Enter a valid email address"
                    etSignupEmail.requestFocus()
                }
                password.isEmpty() -> {
                    etSignupPassword.error = "Password is required"
                    etSignupPassword.requestFocus()
                }
                password.length < 6 -> {
                    etSignupPassword.error = "Password must be at least 6 characters"
                    etSignupPassword.requestFocus()
                }
                confirmPassword.isEmpty() -> {
                    etConfirmPassword.error = "Please confirm your password"
                    etConfirmPassword.requestFocus()
                }
                password != confirmPassword -> {
                    etConfirmPassword.error = "Passwords do not match"
                    etConfirmPassword.requestFocus()
                }
                else -> {
                    // Save user details to SharedPreferences
                    editor.putString("username", username)
                    editor.putString("email", email)
                    editor.putString("password", password)
                    editor.apply()

                    // Show success message
                    Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show()

                    // Navigate to LoginActivity (assuming you have a LoginActivity)
                    val intent = Intent(this, LoginActivity::class.java)
                    startActivity(intent)
                    finish() // Close SignupActivity
                }
            }
        }

        // Back to Login button click listener
        btnBackToLogin.setOnClickListener {
            // Navigate to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Close SignupActivity
        }
    }
}