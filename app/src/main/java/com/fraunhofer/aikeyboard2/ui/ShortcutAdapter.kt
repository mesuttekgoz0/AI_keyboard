package com.fraunhofer.aikeyboard2.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fraunhofer.aikeyboard2.R
import com.fraunhofer.aikeyboard2.data.ShortcutRepository

/**
 * RecyclerView adapter — Fn kısayol listesini gösterir.
 * Her satırda "Fn + X → Aksiyon" + sil butonu.
 */
class ShortcutAdapter(
    private val onDelete: (ShortcutRepository.Shortcut) -> Unit
) : RecyclerView.Adapter<ShortcutAdapter.ShortcutViewHolder>() {

    private val items = mutableListOf<ShortcutRepository.Shortcut>()

    fun submitList(shortcuts: Collection<ShortcutRepository.Shortcut>) {
        items.clear()
        items.addAll(shortcuts.sortedBy { it.key })
        notifyDataSetChanged()
    }

    fun addItem(shortcut: ShortcutRepository.Shortcut) {
        // Aynı harf varsa güncelle
        val existing = items.indexOfFirst { it.key == shortcut.key }
        if (existing >= 0) {
            items[existing] = shortcut
            notifyItemChanged(existing)
        } else {
            val insertIndex = items.indexOfFirst { it.key > shortcut.key }
                .takeIf { it >= 0 } ?: items.size
            items.add(insertIndex, shortcut)
            notifyItemInserted(insertIndex)
        }
    }

    fun removeItem(shortcut: ShortcutRepository.Shortcut) {
        val idx = items.indexOfFirst { it.key == shortcut.key }
        if (idx >= 0) {
            items.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun getCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shortcut, parent, false)
        return ShortcutViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(items[position], onDelete)
    }

    override fun getItemCount(): Int = items.size

    class ShortcutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvKey:    TextView    = itemView.findViewById(R.id.tv_shortcut_key)
        private val tvAction: TextView    = itemView.findViewById(R.id.tv_shortcut_action)
        private val btnDel:   ImageButton = itemView.findViewById(R.id.btn_delete_shortcut)

        fun bind(shortcut: ShortcutRepository.Shortcut, onDelete: (ShortcutRepository.Shortcut) -> Unit) {
            tvKey.text    = shortcut.key.uppercaseChar().toString()
            tvAction.text = shortcut.actionLabel()
            btnDel.setOnClickListener { onDelete(shortcut) }
        }
    }
}
