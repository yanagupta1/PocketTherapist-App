//package com.example.pockettherapist
//
//import android.os.Bundle
//import androidx.fragment.app.Fragment
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//
//class JournalFragment : Fragment() {
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?
//    ): View? {
//        return inflater.inflate(R.layout.fragment_journal, container, false)
//    }
//}
package com.example.pockettherapist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class JournalFragment : Fragment() {

    private lateinit var audioHelper: AudioTextHelper
    private lateinit var micButton: ImageView
    private lateinit var transcribedText: EditText

    private val micPermission = Manifest.permission.RECORD_AUDIO
    private val micRequestCode = 101

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_journal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Connect XML elements
        micButton = view.findViewById(R.id.micButton)
        transcribedText = view.findViewById(R.id.transcribedText)

        // Ask for mic permission
        checkMicPermission()

        // Initialize the speech helper
        audioHelper = AudioTextHelper(
            requireContext(),
            onResult = { text ->
                transcribedText.setText(text)
            },
            onError = { errorMsg ->
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            }
        )

        // Tap mic to start listening
        micButton.setOnClickListener {
            audioHelper.startListening()
            Toast.makeText(requireContext(), "Listening…", Toast.LENGTH_SHORT).show()
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
        audioHelper.destroy()  // Prevent memory leaks
    }
}
