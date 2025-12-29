package com.example.pockettherapist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pockettherapist.databinding.FragmentVoiceJournalBinding

class VoiceJournalFragment : Fragment() {

    private lateinit var binding: FragmentVoiceJournalBinding
    private lateinit var audioHelper: AudioTextHelper

    private val micPermission = Manifest.permission.RECORD_AUDIO
    private val micRequestCode = 101

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentVoiceJournalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkMicPermission()

        audioHelper = AudioTextHelper(
            requireContext(),
            onResult = { text ->

                // APPEND INSTEAD OF REPLACE
                val current = binding.etVoiceText.text.toString()
                val appended = if (current.isEmpty()) text else "$current $text"

                binding.etVoiceText.setText(appended)
                binding.etVoiceText.setSelection(appended.length)
            },
            onError = { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        )

        binding.micButton.setOnClickListener {
            audioHelper.startListening()
            Toast.makeText(requireContext(), "Listening…", Toast.LENGTH_SHORT).show()
        }

        // CLEAR BUTTON
        binding.btnClear.setOnClickListener {
            binding.etVoiceText.setText("")
        }

        // ANALYZE BUTTON
        binding.btnAnalyze.setOnClickListener {
            Toast.makeText(requireContext(), "Analyze coming soon!", Toast.LENGTH_SHORT).show()
        }

        // BACK BUTTON
        binding.btnBack.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, JournalOptionsFragment())
                .addToBackStack(null)
                .commit()
        }
    }


    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), micPermission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(micPermission),
                micRequestCode
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioHelper.destroy()
    }
}
