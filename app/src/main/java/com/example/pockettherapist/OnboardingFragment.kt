package com.example.pockettherapist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.pockettherapist.databinding.FragmentOnboardingBinding

class OnboardingFragment : Fragment() {

    private lateinit var binding: FragmentOnboardingBinding
    private var username: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve the username passed from HomeFragment
        username = arguments?.getString("username") ?: ""

        // Continue button → finish onboarding, then show AI consent
        binding.btnContinue.setOnClickListener {
            if (username.isNotEmpty()) {
                // Mark this user as no longer first-time
                FirstTimeStore.setNotFirstTime(requireContext(), username)
            }

            // Show AI consent dialog after onboarding
            showAIConsentThenNavigateHome()
        }
    }

    private fun showAIConsentThenNavigateHome() {
        val ctx = requireActivity()

        // Initialize and show AI consent dialog
        AIConsentManager.init(ctx)

        // Only show if user hasn't made a decision yet
        if (!AIConsentManager.hasUserMadeConsentDecision()) {
            AIConsentManager.showConsentDialog(
                context = ctx,
                onConsent = {
                    // User enabled AI, navigate to home
                    navigateToHome()
                },
                onDecline = {
                    // User declined AI, still navigate to home
                    navigateToHome()
                }
            )
        } else {
            // Already made decision, just go home
            navigateToHome()
        }
    }

    private fun navigateToHome() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment())
            .commit()
    }

    companion object {
        fun newInstance(username: String): OnboardingFragment {
            val f = OnboardingFragment()
            val args = Bundle()
            args.putString("username", username)
            f.arguments = args
            return f
        }
    }
}
