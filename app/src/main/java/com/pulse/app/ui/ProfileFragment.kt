package com.pulse.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pulse.app.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val user = mainViewModel.getCurrentUser()
        binding.tvUserName.text = user?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "User"
        binding.tvUserEmail.text = user?.email ?: "Unknown Email"

        mainViewModel.personalCode.observe(viewLifecycleOwner) { code ->
            binding.tvMyCode.text = code ?: "------"
        }

        mainViewModel.paired.observe(viewLifecycleOwner) { isPaired ->
            binding.tvCoupleStatus.text = if (isPaired) "Paired" else "Not Paired"
            val colorRes = if (isPaired) com.pulse.app.R.color.md_theme_status_green else com.pulse.app.R.color.md_theme_accent
            binding.tvCoupleStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), colorRes))
            binding.btnDisconnect.visibility = if (isPaired) View.VISIBLE else View.GONE
        }

        binding.btnLogout.setOnClickListener { mainViewModel.logout() }
        binding.btnDisconnect.setOnClickListener { mainViewModel.disconnectCouple() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
