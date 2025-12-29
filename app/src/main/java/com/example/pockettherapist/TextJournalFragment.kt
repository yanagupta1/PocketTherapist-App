package com.example.pockettherapist

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import com.example.pockettherapist.databinding.FragmentTextJournalBinding

class TextJournalFragment : Fragment() {

    private lateinit var binding: FragmentTextJournalBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentTextJournalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // BACK BUTTON → go back to JournalOptionsFragment
        binding.btnBack.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, JournalOptionsFragment())
                .addToBackStack(null)
                .commit()
        }

        // Analyse My Mood click (if you want to add functionality later)
        binding.btnAnalyseMood.setOnClickListener {
            // TODO: add your ML / analysis code
        }
    }
}
