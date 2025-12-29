package com.example.pockettherapist

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class JournalListItem {
    data class DateHeader(val date: String) : JournalListItem()
    data class Entry(val entry: JournalEntry) : JournalListItem()
}

class JournalAdapter(
    private val onEditClick: ((JournalEntry) -> Unit)? = null,
    private val onDeleteClick: ((JournalEntry) -> Unit)? = null
) : ListAdapter<JournalListItem, RecyclerView.ViewHolder>(JournalDiffCallback()) {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_ENTRY = 1
        private const val PAYLOAD_EXPAND_CHANGE = "expand_change"
    }

    private var expandedEntryId: String? = null

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is JournalListItem.DateHeader -> TYPE_DATE_HEADER
            is JournalListItem.Entry -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DATE_HEADER -> DateHeaderViewHolder(
                inflater.inflate(R.layout.item_date_header, parent, false)
            )
            TYPE_ENTRY -> EntryViewHolder(
                inflater.inflate(R.layout.item_journal_entry, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is JournalListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.date)
            is JournalListItem.Entry -> (holder as EntryViewHolder).bind(item.entry)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == PAYLOAD_EXPAND_CHANGE) {
            // Only update expansion state without full rebind
            val item = getItem(position)
            if (item is JournalListItem.Entry && holder is EntryViewHolder) {
                holder.updateExpansion(item.entry.id)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtDate: TextView = itemView.findViewById(R.id.txtDateHeader)

        fun bind(date: String) {
            txtDate.text = date
        }
    }

    inner class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtTitle: TextView = itemView.findViewById(R.id.txtEntryTitle)
        private val txtMood: TextView = itemView.findViewById(R.id.txtMood)
        private val txtPreview: TextView = itemView.findViewById(R.id.txtEntryPreview)
        private val txtTime: TextView = itemView.findViewById(R.id.txtEntryTime)
        private val layoutActions: LinearLayout = itemView.findViewById(R.id.layoutActions)
        private val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        private var currentEntry: JournalEntry? = null

        fun bind(entry: JournalEntry) {
            currentEntry = entry
            val isExpanded = expandedEntryId == entry.id

            // Show title if available
            if (entry.title.isNotEmpty()) {
                txtTitle.text = entry.title
                txtTitle.visibility = View.VISIBLE
            } else {
                txtTitle.visibility = View.GONE
            }

            // Show mood emoji if available
            if (entry.mood.isNotEmpty()) {
                txtMood.text = entry.mood
                txtMood.visibility = View.VISIBLE
            } else {
                txtMood.visibility = View.GONE
            }

            txtPreview.text = entry.text
            txtPreview.maxLines = if (isExpanded) Integer.MAX_VALUE else 3
            layoutActions.visibility = if (isExpanded) View.VISIBLE else View.GONE

            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            txtTime.text = timeFormat.format(Date(entry.timestamp))

            itemView.setOnClickListener {
                val wasExpanded = expandedEntryId == entry.id
                val previousExpandedId = expandedEntryId

                // Toggle expansion
                expandedEntryId = if (wasExpanded) null else entry.id

                // Notify changes with payload to avoid default animation
                if (previousExpandedId != null && previousExpandedId != entry.id) {
                    val prevPos = findPositionById(previousExpandedId)
                    if (prevPos != -1) {
                        notifyItemChanged(prevPos, PAYLOAD_EXPAND_CHANGE)
                    }
                }
                notifyItemChanged(bindingAdapterPosition, PAYLOAD_EXPAND_CHANGE)
            }

            btnEdit.setOnClickListener {
                currentEntry?.let { onEditClick?.invoke(it) }
            }

            btnDelete.setOnClickListener {
                currentEntry?.let { onDeleteClick?.invoke(it) }
            }
        }

        fun updateExpansion(entryId: String) {
            val isExpanded = expandedEntryId == entryId
            txtPreview.maxLines = if (isExpanded) Integer.MAX_VALUE else 3
            layoutActions.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
    }

    private fun findPositionById(id: String): Int {
        for (i in 0 until itemCount) {
            val item = getItem(i)
            if (item is JournalListItem.Entry && item.entry.id == id) {
                return i
            }
        }
        return -1
    }
}

class JournalDiffCallback : DiffUtil.ItemCallback<JournalListItem>() {
    override fun areItemsTheSame(oldItem: JournalListItem, newItem: JournalListItem): Boolean {
        return when {
            oldItem is JournalListItem.DateHeader && newItem is JournalListItem.DateHeader ->
                oldItem.date == newItem.date
            oldItem is JournalListItem.Entry && newItem is JournalListItem.Entry ->
                oldItem.entry.id == newItem.entry.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: JournalListItem, newItem: JournalListItem): Boolean {
        return oldItem == newItem
    }
}
