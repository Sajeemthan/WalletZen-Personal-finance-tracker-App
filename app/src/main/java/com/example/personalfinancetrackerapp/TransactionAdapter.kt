package com.example.personalfinancetrackerapp

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.personalfinancetrackerapp.data.FinanceDatabase
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionAdapter(
    private val transactions: MutableList<Transaction>,
    private val context: Context
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

    private val TAG = "TransactionAdapter"

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvCategoryDate: TextView = itemView.findViewById(R.id.tvCategoryDate)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaction = transactions[position]

        Log.d(TAG, "Binding transaction at position $position: $transaction")

        holder.tvTitle.text = transaction.title
        holder.tvCategoryDate.text = "${transaction.category} - ${transaction.date}"
        holder.tvAmount.text = "Amount: $${"%.2f".format(transaction.amount)}"

        val userPrefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Log.w(TAG, "No logged-in user found, redirecting to LoginActivity")
            Toast.makeText(context, "Please log in to manage transactions", Toast.LENGTH_SHORT).show()
            if (context is ComponentActivity) {
                context.startActivity(Intent(context, LoginActivity::class.java))
                context.finish()
            }
            return
        }

        // Normalize usernames for comparison
        val normalizedUser = currentUser.trim().lowercase()
        val transactionUser = transaction.user.trim().lowercase()
        Log.d(TAG, "Transaction user: $transactionUser, Current user: $normalizedUser")

        // Ensure the transaction belongs to the current user
        // For debugging: Comment out the if block below to bypass filtering and confirm data presence
        if (transactionUser != normalizedUser) {
            Log.d(TAG, "Transaction at position $position does not belong to current user ($normalizedUser), hiding item")
            holder.itemView.visibility = View.GONE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
            return
        } else {
            holder.itemView.visibility = View.VISIBLE
            holder.itemView.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
        }

        holder.btnEdit.setOnClickListener {
            Log.d(TAG, "Edit button clicked for transaction: ${transaction.id}")
            val intent = Intent(context, TransactionActivity::class.java).apply {
                putExtra("editTransaction", Gson().toJson(transaction))
                putExtra("id", transaction.id)
            }
            if (context is ComponentActivity) {
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Unable to edit transaction: Invalid context", Toast.LENGTH_SHORT).show()
            }
        }

        holder.btnDelete.setOnClickListener {
            Log.d(TAG, "Delete button clicked for transaction: ${transaction.id}")
            val db = FinanceDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.financeDao().deleteTransaction(transaction)
                    withContext(Dispatchers.Main) {
                        transactions.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, transactions.size)
                        Toast.makeText(context, "Transaction Deleted", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error deleting transaction: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        val count = transactions.size
        Log.d(TAG, "Transaction count: $count")
        if (count == 0) {
            Log.w(TAG, "No transactions available to display after filtering")
            Toast.makeText(context, "No transactions available", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, "Displaying $count transactions")
        }
        return count
    }

    fun updateTransactions(newTransactions: List<Transaction>) {
        Log.d(TAG, "Updating transactions with new list: $newTransactions")
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }
}