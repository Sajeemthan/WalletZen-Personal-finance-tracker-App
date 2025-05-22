package com.example.personalfinancetrackerapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.personalfinancetrackerapp.data.FinanceDatabase
import com.example.personalfinancetrackerapp.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SignupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup)

        val etSignupUsername = findViewById<EditText>(R.id.etSignupUsername)
        val etSignupEmail = findViewById<EditText>(R.id.etSignupEmail)
        val etSignupPassword = findViewById<EditText>(R.id.etSignupPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        val btnBackToLogin = findViewById<Button>(R.id.btnBackToLogin)

        val db = FinanceDatabase.getDatabase(this)
        val userDao = db.financeDao()

        btnSignup.setOnClickListener {
            val username = etSignupUsername.text.toString().trim()
            val email = etSignupEmail.text.toString().trim()
            val password = etSignupPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

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
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val existingUser = userDao.getUser(username)
                            withContext(Dispatchers.Main) {
                                if (existingUser != null) {
                                    etSignupUsername.error = "Username already exists"
                                    etSignupUsername.requestFocus()
                                } else {
                                    val newUser = User(username, email, password)
                                    userDao.insertUser(newUser)

                                    Toast.makeText(this@SignupActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                    val intent = Intent(this@SignupActivity, LoginActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@SignupActivity, "Error creating account: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        btnBackToLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}