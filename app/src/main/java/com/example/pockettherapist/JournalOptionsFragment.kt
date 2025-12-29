package com.example.pockettherapist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.pockettherapist.databinding.FragmentJournalOptionsBinding

class JournalOptionsFragment : Fragment() {

    private lateinit var binding: FragmentJournalOptionsBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentJournalOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        // TEXT JOURNAL
        binding.btnTextJournal.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TextJournalFragment())
                .addToBackStack(null)
                .commit()
        }

        // VOICE JOURNAL
        binding.btnVoiceJournal.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, VoiceJournalFragment())
                .addToBackStack(null)
                .commit()
        }
    }
}
