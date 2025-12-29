package com.example.pockettherapist.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pockettherapist.RecommendationEngine
import com.example.pockettherapist.databinding.ItemEventBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TMEventAdapter(private val items: List<RecommendationEngine.EventDetail>) :
    RecyclerView.Adapter<TMEventAdapter.TMEventViewHolder>() {

    inner class TMEventViewHolder(val binding: ItemEventBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TMEventViewHolder {
        val binding = ItemEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TMEventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TMEventViewHolder, position: Int) {
        val item = items[position]

        // Set category badge
        holder.binding.eventCategory.text = item.category.uppercase()

        // Set title
        holder.binding.eventTitle.text = item.name

        // Set description
        holder.binding.eventDescription.text = item.description

        // Format and set date (convert YYYY-MM-DD to readable format)
        val formattedDate = try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val date = inputFormat.parse(item.date)
            date?.let { outputFormat.format(it) } ?: item.date
        } catch (e: Exception) {
            item.date
        }
        holder.binding.eventDate.text = formattedDate

        // Set time if available
        if (!item.time.isNullOrEmpty()) {
            holder.binding.eventTime.visibility = View.VISIBLE
            holder.binding.root.findViewById<View>(
                com.example.pockettherapist.R.id.timeIcon
            ).visibility = View.VISIBLE

            // Format time from HH:mm to h:mm AM/PM
            val formattedTime = try {
                val inputFormat = SimpleDateFormat("HH:mm", Locale.US)
                val outputFormat = SimpleDateFormat("h:mm a", Locale.US)
                val time = inputFormat.parse(item.time)
                time?.let { outputFormat.format(it) } ?: item.time
            } catch (e: Exception) {
                item.time
            }
            holder.binding.eventTime.text = formattedTime
        } else {
            holder.binding.eventTime.visibility = View.GONE
            holder.binding.root.findViewById<View>(
                com.example.pockettherapist.R.id.timeIcon
            ).visibility = View.GONE
        }

        // Set venue
        holder.binding.eventVenue.text = item.venue

        // Set address if available
        if (!item.address.isNullOrEmpty()) {
            holder.binding.eventAddress.visibility = View.VISIBLE
            holder.binding.eventAddress.text = item.address
        } else {
            holder.binding.eventAddress.visibility = View.GONE
        }

        // Make card clickable to open Google Maps or URL
        holder.binding.root.setOnClickListener {
            val context = holder.binding.root.context

            // Try Google Maps URL first, fallback to event URL
            val url = item.googleMapsUrl ?: item.url

            if (!url.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount() = items.size
}
