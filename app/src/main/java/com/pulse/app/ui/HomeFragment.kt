package com.pulse.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pulse.app.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Basic placeholder for partner name as backend doesn't store partner's name yet
        binding.tvPartnerName.text = "Partner"

        mainViewModel.status.observe(viewLifecycleOwner) { statusText ->
            binding.tvConnectionStatus.text = statusText
        }

        mainViewModel.partnerOnline.observe(viewLifecycleOwner) { isOnline ->
            val colorRes = if (isOnline) com.pulse.app.R.color.md_theme_status_green else com.pulse.app.R.color.md_theme_accent
            binding.vStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)
            )
        }

        // Quick Signals
        binding.btnQuickLove.setOnClickListener { mainViewModel.sendEmotion("love") }
        binding.btnQuickHug.setOnClickListener { mainViewModel.sendEmotion("hug") }
        binding.btnQuickKiss.setOnClickListener { mainViewModel.sendEmotion("kiss") }
        binding.btnQuickAngry.setOnClickListener { mainViewModel.sendEmotion("angry") }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
