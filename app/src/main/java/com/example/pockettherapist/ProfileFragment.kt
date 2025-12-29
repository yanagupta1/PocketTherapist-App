package com.example.pockettherapist

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import coil.load
import coil.transform.CircleCropTransformation
import com.example.pockettherapist.databinding.FragmentProfileBinding
import android.text.Editable
import android.text.TextWatcher
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import coil.request.CachePolicy

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    // Edit mode state
    private var isEditMode = false

    // Cached original values for cancel operation
    private var originalProfile: UserProfile? = null

    // Temporary URI for camera capture
    private var tempImageUri: Uri? = null

    // Activity Result Launchers
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { handleSelectedImage(it) }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempImageUri?.let { handleSelectedImage(it) }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGenderDropdown()
        setupClickListeners()
        setupAISettings()
        loadProfileData()
    }

    private fun setupAISettings() {
        // Initialize AIConsentManager
        AIConsentManager.init(requireContext())

        // Set initial switch state
        binding.switchAIFeatures.isChecked = AIConsentManager.isAIEnabled()
        updateAIStatusText(AIConsentManager.isAIEnabled())

        // Handle switch toggle
        binding.switchAIFeatures.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // User wants to enable AI - show consent dialog if they haven't consented before
                if (!AIConsentManager.hasUserConsented()) {
                    // Show full consent dialog
                    AIConsentManager.showConsentDialog(
                        context = requireContext(),
                        onConsent = {
                            updateAIStatusText(true)
                            Toast.makeText(requireContext(), "AI features enabled", Toast.LENGTH_SHORT).show()
                        },
                        onDecline = {
                            // User declined in dialog, revert switch
                            binding.switchAIFeatures.isChecked = false
                            updateAIStatusText(false)
                        }
                    )
                } else {
                    // User has consented before, just enable
                    AIConsentManager.setAIFeaturesEnabled(true)
                    updateAIStatusText(true)
                    Toast.makeText(requireContext(), "AI features enabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                // User wants to disable AI
                AIConsentManager.setAIFeaturesEnabled(false)
                updateAIStatusText(false)
                Toast.makeText(requireContext(), "AI features disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // View privacy info button - shows the consent dialog again
        binding.btnViewAIPrivacy.setOnClickListener {
            showAIPrivacyInfo()
        }
    }

    private fun updateAIStatusText(enabled: Boolean) {
        if (enabled) {
            binding.txtAIStatus.text = "AI features are enabled. Your journal entries are analyzed by Google's Gemini AI to provide personalized wellness tips and music recommendations."
        } else {
            binding.txtAIStatus.text = "AI features are disabled. Enable to get personalized wellness recommendations and music suggestions based on your journal entries."
        }
    }

    private fun showAIPrivacyInfo() {
        // Show the consent dialog in info mode (user can see details)
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_ai_consent, null)

        val dialog = AlertDialog.Builder(requireContext(), R.style.Theme_PocketTherapist_AlertDialog)
            .setView(dialogView)
            .create()

        val btnEnable = dialogView.findViewById<android.widget.Button>(R.id.btnEnableAI)
        val btnDecline = dialogView.findViewById<android.widget.Button>(R.id.btnDeclineAI)

        // Update button text based on current state
        if (AIConsentManager.isAIEnabled()) {
            btnEnable.text = "Keep Enabled"
            btnDecline.text = "Disable AI Features"
        } else {
            btnEnable.text = "Enable AI Features"
            btnDecline.text = "Keep Disabled"
        }

        btnEnable.setOnClickListener {
            AIConsentManager.enableAIFeatures()
            binding.switchAIFeatures.isChecked = true
            updateAIStatusText(true)
            dialog.dismiss()
        }

        btnDecline.setOnClickListener {
            AIConsentManager.disableAIFeatures()
            binding.switchAIFeatures.isChecked = false
            updateAIStatusText(false)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupGenderDropdown() {
        val genderOptions = resources.getStringArray(R.array.gender_options)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            genderOptions
        )
        binding.dropdownGender.setAdapter(adapter)
    }

    private fun setupClickListeners() {
        // Edit toggle button
        binding.btnEditToggle.setOnClickListener {
            if (isEditMode) {
                // Already in edit mode - this becomes cancel
                cancelEdit()
            } else {
                enterEditMode()
            }
        }

        // Profile picture click (only works in edit mode)
        binding.cameraOverlay.setOnClickListener {
            if (isEditMode) {
                showImagePickerDialog()
            }
        }

        // Birthdate picker
        binding.editBirthdate.setOnClickListener {
            if (isEditMode) {
                showDatePicker()
            }
        }

        // Save button
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }

        // Cancel button
        binding.btnCancelEdit.setOnClickListener {
            cancelEdit()
        }

        // Logout button
        binding.btnLogout.setOnClickListener {
            UserStore.signOut()
            val intent = Intent(requireContext(), SignInActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }

        // Update helper text when interests field changes
        binding.editInterests.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isEditMode) {
                    updateInterestsHelperText(true)
                }
            }
        })
    }

    private fun loadProfileData() {
        // Show cached data immediately (offline support)
        displayCachedData()

        // Then fetch fresh data from Firebase
        UserStore.loadProfile(
            onSuccess = { profile ->
                originalProfile = profile
                displayProfile(profile)
            },
            onFailure = { _ ->
                // Already showing cached data, just log the error
                Toast.makeText(requireContext(), "Could not refresh profile", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun displayCachedData() {
        binding.txtUsername.text = "@${UserStore.loggedInUser ?: ""}"
        binding.txtDisplayName.text = UserStore.displayName?.ifEmpty { UserStore.loggedInUser } ?: UserStore.loggedInUser ?: ""
        binding.editDisplayName.setText(UserStore.displayName ?: "")
        binding.editAge.setText(UserStore.age ?: "")
        binding.dropdownGender.setText(UserStore.gender ?: "", false)
        binding.editBirthdate.setText(UserStore.birthdate ?: "")
        binding.editLocation.setText(UserStore.location ?: "")
        binding.editBio.setText(UserStore.bio ?: "")
        binding.editInterests.setText(UserStore.interests ?: "")

        // Load profile picture with Coil (from local file)
        UserStore.profilePictureUrl?.let { path ->
            if (path.isNotEmpty()) {
                val file = File(path)
                if (file.exists()) {
                    binding.imgProfile.load(file) {
                        crossfade(true)
                        placeholder(R.drawable.ic_profile_placeholder)
                        error(R.drawable.ic_profile_placeholder)
                        transformations(CircleCropTransformation())
                        memoryCachePolicy(CachePolicy.DISABLED)
                        diskCachePolicy(CachePolicy.DISABLED)
                    }
                }
            }
        }
    }

    private fun displayProfile(profile: UserProfile) {
        binding.txtUsername.text = "@${profile.username}"
        binding.txtDisplayName.text = profile.displayName.ifEmpty { profile.username }
        binding.editDisplayName.setText(profile.displayName)
        binding.editAge.setText(profile.age)
        binding.dropdownGender.setText(profile.gender, false)
        binding.editBirthdate.setText(profile.birthdate)
        binding.editLocation.setText(profile.location)
        binding.editBio.setText(profile.bio)
        binding.editInterests.setText(profile.interests)

        // Load profile picture with Coil (from local file)
        if (profile.profilePictureUrl.isNotEmpty()) {
            val file = File(profile.profilePictureUrl)
            if (file.exists()) {
                binding.imgProfile.load(file) {
                    crossfade(true)
                    placeholder(R.drawable.ic_profile_placeholder)
                    error(R.drawable.ic_profile_placeholder)
                    transformations(CircleCropTransformation())
                    memoryCachePolicy(CachePolicy.DISABLED)
                    diskCachePolicy(CachePolicy.DISABLED)
                }
            }
        }
    }

    private fun enterEditMode() {
        isEditMode = true

        // Change edit button to close icon
        binding.btnEditToggle.setImageResource(R.drawable.ic_close)

        // Show camera overlay on profile picture
        binding.cameraOverlay.visibility = View.VISIBLE

        // Show display name input
        binding.tilDisplayName.visibility = View.VISIBLE

        // Enable all input fields
        setFieldsEnabled(true)

        // Show save and cancel buttons
        binding.btnSaveProfile.visibility = View.VISIBLE
        binding.btnCancelEdit.visibility = View.VISIBLE
    }

    private fun exitEditMode() {
        isEditMode = false

        // Change close icon back to edit
        binding.btnEditToggle.setImageResource(R.drawable.ic_edit)

        // Hide camera overlay
        binding.cameraOverlay.visibility = View.GONE

        // Hide display name input (show text view instead)
        binding.tilDisplayName.visibility = View.GONE

        // Disable all input fields
        setFieldsEnabled(false)

        // Hide save and cancel buttons
        binding.btnSaveProfile.visibility = View.GONE
        binding.btnCancelEdit.visibility = View.GONE
    }

    private fun setFieldsEnabled(enabled: Boolean) {
        binding.editDisplayName.isEnabled = enabled
        binding.editAge.isEnabled = enabled
        binding.dropdownGender.isEnabled = enabled
        binding.tilGender.isEnabled = enabled
        binding.editBirthdate.isEnabled = enabled
        binding.editLocation.isEnabled = enabled
        binding.editBio.isEnabled = enabled
        binding.editInterests.isEnabled = enabled

        // Show helper text only in edit mode and when field is empty
        updateInterestsHelperText(enabled)
    }

    private fun updateInterestsHelperText(inEditMode: Boolean) {
        val hasContent = !binding.editInterests.text.isNullOrBlank()
        binding.tilInterests.helperText = if (inEditMode && !hasContent) {
            "e.g., meditation, yoga, journaling"
        } else {
            null
        }
    }

    private fun cancelEdit() {
        // Restore original values
        originalProfile?.let { displayProfile(it) } ?: displayCachedData()
        exitEditMode()
    }

    private fun saveProfile() {
        val displayName = binding.editDisplayName.text.toString().trim()
        val age = binding.editAge.text.toString().trim()
        val gender = binding.dropdownGender.text.toString().trim()
        val birthdate = binding.editBirthdate.text.toString().trim()
        val location = binding.editLocation.text.toString().trim()
        val bio = binding.editBio.text.toString().trim()
        val interests = binding.editInterests.text.toString().trim()

        // Validation
        val ageInt = age.toIntOrNull()
        if (age.isNotEmpty() && (ageInt == null || ageInt < 0 || ageInt > 150)) {
            Toast.makeText(requireContext(), "Invalid age", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSaveProfile.isEnabled = false

        UserStore.updateFullProfile(
            displayName = displayName,
            bio = bio,
            age = age,
            gender = gender,
            birthdate = birthdate,
            location = location,
            interests = interests,
            onSuccess = {
                binding.btnSaveProfile.isEnabled = true
                Toast.makeText(requireContext(), "Profile saved successfully", Toast.LENGTH_SHORT).show()

                // Update display name text view
                binding.txtDisplayName.text = displayName.ifEmpty { UserStore.loggedInUser ?: "" }

                // Update cached original profile
                originalProfile = UserProfile(
                    username = UserStore.loggedInUser ?: "",
                    displayName = displayName,
                    bio = bio,
                    age = age,
                    gender = gender,
                    birthdate = birthdate,
                    location = location,
                    interests = interests,
                    profilePictureUrl = originalProfile?.profilePictureUrl ?: ""
                )

                exitEditMode()
            },
            onFailure = { error ->
                binding.btnSaveProfile.isEnabled = true
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndLaunch()
                    1 -> launchGalleryPicker()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        val photoFile = File.createTempFile(
            "profile_",
            ".jpg",
            requireContext().cacheDir
        )
        tempImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )
        takePictureLauncher.launch(tempImageUri)
    }

    private fun launchGalleryPicker() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun handleSelectedImage(uri: Uri) {
        // Show loading state
        binding.imgProfile.alpha = 0.5f

        // Save to local storage
        UserStore.uploadProfilePicture(
            imageUri = uri,
            onSuccess = { localPath ->
                binding.imgProfile.alpha = 1.0f
                // Update image from local file
                val file = File(localPath)
                binding.imgProfile.load(file) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    memoryCachePolicy(CachePolicy.DISABLED)
                    diskCachePolicy(CachePolicy.DISABLED)
                }
                Toast.makeText(requireContext(), "Profile picture updated", Toast.LENGTH_SHORT).show()
            },
            onFailure = { error ->
                binding.imgProfile.alpha = 1.0f
                Toast.makeText(requireContext(), "Failed to save: $error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        // Try to parse existing date
        binding.editBirthdate.text?.toString()?.let { dateStr ->
            try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                format.parse(dateStr)?.let { date ->
                    calendar.time = date
                }
            } catch (_: Exception) {
                // Use current date if parsing fails
            }
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                binding.editBirthdate.setText(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // Prevent future dates
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
