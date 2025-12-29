package com.example.pockettherapist.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pockettherapist.RecommendationEngine
import com.example.pockettherapist.databinding.ItemAmenityBinding

class AmenityAdapter(private val items: List<RecommendationEngine.ResourceDetail>) :
    RecyclerView.Adapter<AmenityAdapter.AmenityViewHolder>() {

    inner class AmenityViewHolder(val binding: ItemAmenityBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AmenityViewHolder {
        val binding = ItemAmenityBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AmenityViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AmenityViewHolder, position: Int) {
        val item = items[position]

        holder.binding.amenityName.text = item.name
        holder.binding.amenityType.text = item.type.uppercase()
        holder.binding.amenityDescription.text = item.description

        // Show phone if available
        val phoneLayout = holder.binding.root.findViewById<View>(
            com.example.pockettherapist.R.id.phoneLayout
        )
        if (!item.phone.isNullOrEmpty()) {
            phoneLayout?.visibility = View.VISIBLE
            holder.binding.amenityPhone.text = item.phone

            // Make phone number clickable to open dialer
            holder.binding.amenityPhone.setOnClickListener {
                val context = it.context
                val phoneNumber = item.phone.replace("[^0-9+]".toRegex(), "")
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                context.startActivity(intent)
            }
        } else {
            phoneLayout?.visibility = View.GONE
        }

        // Show address if available
        val addressLayout = holder.binding.root.findViewById<View>(
            com.example.pockettherapist.R.id.addressLayout
        )
        if (!item.address.isNullOrEmpty()) {
            addressLayout?.visibility = View.VISIBLE
            holder.binding.amenityAddress.text = item.address

            // Make address clickable to open Google Maps
            holder.binding.amenityAddress.setOnClickListener {
                val context = it.context
                val url = item.googleMapsUrl
                if (!url.isNullOrEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            }
        } else {
            addressLayout?.visibility = View.GONE
        }

        // Make the card clickable to open Google Maps
        holder.binding.root.setOnClickListener {
            val context = holder.binding.root.context

            // Try Google Maps URL first, fallback to website
            val url = item.googleMapsUrl ?: item.website

            if (!url.isNullOrEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        }
    }

    override fun getItemCount() = items.size
}
