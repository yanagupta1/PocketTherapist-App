package com.example.pockettherapist

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation

class SongAdapter(
    private val songs: MutableList<SpotifyIntegration.SpotifyTrack> = mutableListOf()
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgAlbumArt: ImageView = itemView.findViewById(R.id.imgAlbumArt)
        val txtSongTitle: TextView = itemView.findViewById(R.id.txtSongTitle)
        val txtArtist: TextView = itemView.findViewById(R.id.txtArtist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song_card, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]

        holder.txtSongTitle.text = song.name
        holder.txtArtist.text = song.artist

        // Load album art with Coil
        if (!song.albumArt.isNullOrEmpty()) {
            holder.imgAlbumArt.load(song.albumArt) {
                crossfade(true)
                placeholder(R.drawable.ic_music_placeholder)
                error(R.drawable.ic_music_placeholder)
            }
        } else {
            holder.imgAlbumArt.setImageResource(R.drawable.ic_music_placeholder)
        }

        // Click to open in Spotify
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            try {
                // Try to open in Spotify app first
                val spotifyIntent = Intent(Intent.ACTION_VIEW, Uri.parse(song.uri))
                spotifyIntent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
                context.startActivity(spotifyIntent)
            } catch (e: Exception) {
                // Fallback to web URL
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(song.url))
                context.startActivity(webIntent)
            }
        }
    }

    override fun getItemCount(): Int = songs.size

    fun updateSongs(newSongs: List<SpotifyIntegration.SpotifyTrack>) {
        songs.clear()
        songs.addAll(newSongs)
        notifyDataSetChanged()
    }
}
