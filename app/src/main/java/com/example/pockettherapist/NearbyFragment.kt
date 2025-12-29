package com.example.pockettherapist

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pockettherapist.adapters.AmenityAdapter
import com.example.pockettherapist.adapters.TMEventAdapter
import com.example.pockettherapist.databinding.FragmentNearbyBinding
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Locale

class NearbyFragment : Fragment() {

    private lateinit var binding: FragmentNearbyBinding
    private lateinit var recommendationEngine: RecommendationEngine

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadUserLocation()
            else Toast.makeText(requireContext(), "Location permission required", Toast.LENGTH_SHORT).show()
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentNearbyBinding.inflate(inflater, container, false)

        binding.eventsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.amenitiesRecycler.layoutManager = LinearLayoutManager(requireContext())

        // Initialize Gemini-powered recommendation engine
        recommendationEngine = RecommendationEngine(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestPermission()
    }

    // -------------------------------------------------
    // PERMISSION HANDLING
    // -------------------------------------------------

    private fun requestPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        val granted = ContextCompat.checkSelfPermission(requireContext(), permission)

        if (granted == PackageManager.PERMISSION_GRANTED) {
            loadUserLocation()
        } else {
            permissionLauncher.launch(permission)
        }
    }



    @SuppressLint("MissingPermission")
    private fun loadUserLocation() {
        binding.loadingSpinner.visibility = View.VISIBLE

        val fused = LocationServices.getFusedLocationProviderClient(requireActivity())

        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    loadGeminiData(loc.latitude, loc.longitude)
                } else {
                    // fallback: Madison
                    loadGeminiData(43.0731, -89.4012)
                }
            }
            .addOnFailureListener {
                loadGeminiData(43.0731, -89.4012)
            }
    }

    private fun loadGeminiData(lat: Double, lng: Double) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("NearbyFragment", "Loading events and amenities together for lat=$lat, lng=$lng")

                // Convert coordinates to city name
                val locationString = getLocationString(lat, lng)
                android.util.Log.d("NearbyFragment", "Location string: $locationString")

                // Create emotion data
                val emotionData = RecommendationEngine.createEmotionData(
                    emotion = "neutral",
                    emotionScore = 0.5f,
                    sentiment = "neutral",
                    sentimentScore = 0.5f
                )

                // Load events and amenities in parallel using async
                val eventsDeferred = lifecycleScope.async {
                    recommendationEngine.getEventRecommendations(
                        location = locationString,
                        emotionData = emotionData
                    )
                }

                val amenitiesDeferred = lifecycleScope.async {
                    recommendationEngine.getNearbyHelp(
                        journalText = "Looking for nearby mental health resources and wellness amenities",
                        emotionData = emotionData,
                        location = locationString
                    )
                }

                // Wait for both to complete
                val eventRecs = eventsDeferred.await()
                val nearbyHelp = amenitiesDeferred.await()

                // Update UI with events
                if (eventRecs != null && eventRecs.events.isNotEmpty()) {
                    android.util.Log.d("NearbyFragment", "Loaded ${eventRecs.events.size} events")
                    binding.eventsRecycler.adapter = TMEventAdapter(eventRecs.events)
                } else {
                    android.util.Log.e("NearbyFragment", "getEventRecommendations returned null or empty")
                    binding.eventsRecycler.adapter = TMEventAdapter(emptyList())
                }

                // Update UI with amenities
                if (nearbyHelp != null && nearbyHelp.resources.isNotEmpty()) {
                    android.util.Log.d("NearbyFragment", "Loaded ${nearbyHelp.resources.size} amenities")
                    binding.amenitiesRecycler.adapter = AmenityAdapter(nearbyHelp.resources)
                } else {
                    android.util.Log.e("NearbyFragment", "getNearbyHelp returned null or empty")
                    binding.amenitiesRecycler.adapter = AmenityAdapter(emptyList())
                }

                binding.loadingSpinner.visibility = View.GONE

            } catch (e: Exception) {
                binding.loadingSpinner.visibility = View.GONE
                android.util.Log.e("NearbyFragment", "Exception loading data", e)
                Toast.makeText(requireContext(), "Error loading data: ${e.message}", Toast.LENGTH_LONG).show()
                binding.eventsRecycler.adapter = TMEventAdapter(emptyList())
            }
        }
    }

    // -------------------------------------------------
    // GEMINI-POWERED EVENTS
    // -------------------------------------------------

    private fun loadGeminiEvents(lat: Double, lng: Double) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("NearbyFragment", "Loading Gemini events for lat=$lat, lng=$lng")

                // Convert coordinates to city name
                val locationString = getLocationString(lat, lng)
                android.util.Log.d("NearbyFragment", "Location string: $locationString")

                // Create dummy emotion data for event search
                val emotionData = RecommendationEngine.createEmotionData(
                    emotion = "neutral",
                    emotionScore = 0.5f,
                    sentiment = "neutral",
                    sentimentScore = 0.5f
                )

                // Get events from Gemini
                android.util.Log.d("NearbyFragment", "Calling recommendationEngine.getEventRecommendations...")
                val eventRecs = recommendationEngine.getEventRecommendations(
                    location = locationString,
                    emotionData = emotionData
                )

                if (eventRecs != null) {
                    android.util.Log.d("NearbyFragment", "Loaded ${eventRecs.events.size} events from Gemini")
                    binding.eventsRecycler.adapter = TMEventAdapter(eventRecs.events)
                    println("Loaded ${eventRecs.events.size} events from Gemini")
                } else {
                    android.util.Log.e("NearbyFragment", "getEventRecommendations returned null")
                    Toast.makeText(requireContext(), "Failed to load events - check logs for details", Toast.LENGTH_LONG).show()
                    binding.eventsRecycler.adapter = TMEventAdapter(emptyList())
                }

            } catch (e: Exception) {
                android.util.Log.e("NearbyFragment", "Exception in loadGeminiEvents", e)
                println("Gemini events failure: ${e.message}")
                Toast.makeText(requireContext(), "Error loading events: ${e.message}", Toast.LENGTH_LONG).show()
                binding.eventsRecycler.adapter = TMEventAdapter(emptyList())
            }
        }
    }

    // -------------------------------------------------
    // GEMINI-POWERED AMENITIES
    // -------------------------------------------------

    private fun loadGeminiAmenities(lat: Double, lng: Double) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("NearbyFragment", "Loading Gemini amenities for lat=$lat, lng=$lng")

                // Convert coordinates to city name
                val locationString = getLocationString(lat, lng)
                android.util.Log.d("NearbyFragment", "Location string for amenities: $locationString")

                // Create dummy emotion data for amenity search
                val emotionData = RecommendationEngine.createEmotionData(
                    emotion = "neutral",
                    emotionScore = 0.5f,
                    sentiment = "neutral",
                    sentimentScore = 0.5f
                )

                // Get nearby resources from Gemini
                android.util.Log.d("NearbyFragment", "Calling recommendationEngine.getNearbyHelp...")
                val nearbyHelp = recommendationEngine.getNearbyHelp(
                    journalText = "Looking for nearby mental health resources and wellness amenities",
                    emotionData = emotionData,
                    location = locationString
                )

                if (nearbyHelp != null) {
                    android.util.Log.d("NearbyFragment", "Loaded ${nearbyHelp.resources.size} amenities from Gemini")
                    binding.amenitiesRecycler.adapter = AmenityAdapter(nearbyHelp.resources)
                    println("Loaded ${nearbyHelp.resources.size} amenities from Gemini")
                } else {
                    android.util.Log.e("NearbyFragment", "getNearbyHelp returned null")
                    Toast.makeText(requireContext(), "Failed to load amenities - check logs", Toast.LENGTH_LONG).show()
                }

                binding.loadingSpinner.visibility = View.GONE

            } catch (e: Exception) {
                binding.loadingSpinner.visibility = View.GONE
                android.util.Log.e("NearbyFragment", "Exception in loadGeminiAmenities", e)
                println("Gemini amenities failure: ${e.message}")
                Toast.makeText(requireContext(), "Error loading amenities: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getLocationString(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)

            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                val city = address.locality ?: address.subAdminArea ?: ""
                val state = address.adminArea ?: ""
                val country = address.countryName ?: ""

                when {
                    city.isNotEmpty() && state.isNotEmpty() -> "$city, $state"
                    city.isNotEmpty() -> city
                    state.isNotEmpty() -> state
                    country.isNotEmpty() -> country
                    else -> "latitude: $lat, longitude: $lng"
                }
            } else {
                "latitude: $lat, longitude: $lng"
            }
        } catch (e: Exception) {
            println("Geocoder error: ${e.message}")
            "latitude: $lat, longitude: $lng"
        }
    }
}
