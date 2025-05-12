package com.example.personalfinancetrackerapp

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.personalfinancetrackerapp.model.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TransactionAdapter(
    private val transactions: MutableList<Transaction>,
    private val context: Context
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {

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

        holder.tvTitle.text = transaction.title
        holder.tvCategoryDate.text = "${transaction.category} - ${transaction.date}"
        holder.tvAmount.text = "Amount: $${transaction.amount}"

        // Get current user
        val userPrefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val currentUser = userPrefs.getString("username", null)
        if (currentUser == null) {
            Toast.makeText(context, "Please log in to manage transactions", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(context, LoginActivity::class.java))
            return
        }

        holder.btnEdit.setOnClickListener {
            val intent = Intent(context, TransactionActivity::class.java).apply {
                putExtra("editTransaction", Gson().toJson(transaction))
                putExtra("position", position)
            }
            context.startActivity(intent)
        }

        holder.btnDelete.setOnClickListener {
            val prefs = context.getSharedPreferences("FinancePrefs", Context.MODE_PRIVATE)
            val gson = Gson()
            val userTransactionKey = "transactions_$currentUser"

            // Load user-specific transactions
            val existingJson = prefs.getString(userTransactionKey, null)
            val type = object : TypeToken<MutableList<Transaction>>() {}.type
            val transactionList: MutableList<Transaction> = if (existingJson != null) {
                try {
                    gson.fromJson(existingJson, type)
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            // Ensure the transaction exists and belongs to the user
            if (position < transactionList.size && transactionList[position] == transaction) {
                transactionList.removeAt(position)
                transactions.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, transactions.size)

                // Save updated transactions
                val updatedJson = gson.toJson(transactionList)
                prefs.edit().putString(userTransactionKey, updatedJson).apply()

                Toast.makeText(context, "Transaction Deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Error deleting transaction", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount(): Int = transactions.size

    // Function to update the transaction list
    fun updateTransactions(newTransactions: List<Transaction>) {
        transactions.clear()
        transactions.addAll(newTransactions)
        notifyDataSetChanged()
    }
}
