package com.example.pockettherapist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.pockettherapist.databinding.ActivitySignupBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Locale

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            captureLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Auto-capture location on start
        requestLocationPermissionAndCapture()

        // Location icon click to refresh location
        binding.tilLocation.setEndIconOnClickListener {
            requestLocationPermissionAndCapture()
        }

        // Sign Up Button Logic
        binding.btnSignup.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val fullName = binding.etFullName.text.toString().trim()
            val location = binding.etLocation.text.toString().trim()
            val ageText = binding.etAge.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username and Password required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (fullName.isEmpty()) {
                Toast.makeText(this, "Full name is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val ageInt = ageText.toIntOrNull()
            if (ageInt == null || ageInt < 0) {
                Toast.makeText(this, "Invalid age", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSignup.isEnabled = false

            UserStore.signUp(
                username = username,
                password = password,
                fullName = fullName,
                location = location,
                age = ageText,
                onSuccess = {
                    Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show()
                    // Store active account
                    UserStore.setCurrentUsername(username)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                },
                onFailure = { error ->
                    binding.btnSignup.isEnabled = true
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Redirect to Sign In
        binding.txtLoginRedirect.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
        }
    }

    private fun requestLocationPermissionAndCapture() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                captureLocation()
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun captureLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val geocoder = Geocoder(this, Locale.getDefault())
                    try {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
                            val country = address.countryName ?: ""
                            val locationText = if (city.isNotEmpty() && country.isNotEmpty()) {
                                "$city, $country"
                            } else {
                                city.ifEmpty { country }
                            }
                            binding.etLocation.setText(locationText)
                        }
                    } catch (e: Exception) {
                        // Geocoding failed, leave location empty
                    }
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }
}
