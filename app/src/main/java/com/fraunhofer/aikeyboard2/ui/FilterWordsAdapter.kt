package com.fraunhofer.aikeyboard2.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fraunhofer.aikeyboard2.R

/**
 * RecyclerView adapter — yasaklı kelime listesini gösterir.
 * Her satırda kelime ve sil butonu bulunur.
 */
class FilterWordsAdapter(
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<FilterWordsAdapter.WordViewHolder>() {

    private val items = mutableListOf<String>()

    /** Listeyi tamamen yeniler ve RecyclerView'ı günceller. */
    fun submitList(words: List<String>) {
        items.clear()
        items.addAll(words.sorted())
        notifyDataSetChanged()
    }

    /** Tek kelime ekler (animasyonlu). */
    fun addItem(word: String) {
        val insertIndex = items.indexOfFirst { it > word }.takeIf { it >= 0 } ?: items.size
        items.add(insertIndex, word)
        notifyItemInserted(insertIndex)
    }

    /** Tek kelime kaldırır (animasyonlu). */
    fun removeItem(word: String) {
        val idx = items.indexOf(word)
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun getCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word, parent, false)
        return WordViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        holder.bind(items[position], onDelete)
    }

    override fun getItemCount(): Int = items.size

    class WordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvWord: TextView = itemView.findViewById(R.id.tv_word)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_word)

        fun bind(word: String, onDelete: (String) -> Unit) {
            tvWord.text = word
            btnDelete.setOnClickListener { onDelete(word) }
        }
    }
}
