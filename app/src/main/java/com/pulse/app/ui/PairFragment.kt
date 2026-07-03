package com.pulse.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import android.annotation.SuppressLint
import android.content.Intent
import android.view.MotionEvent
import com.pulse.app.R
import com.pulse.app.databinding.FragmentPairBinding

class PairFragment : Fragment() {

    private var _binding: FragmentPairBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPairBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.loadInitialData()
        setupListeners()
        observeState()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun applyHoverEffect(button: View) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
            }
            false
        }
    }

    private fun setupListeners() {
        applyHoverEffect(binding.shareButton)
        applyHoverEffect(binding.joinButton)

        binding.shareButton.setOnClickListener {
            val code = viewModel.personalCode.value
            if (code != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Connect on Pulse")
                    putExtra(Intent.EXTRA_TEXT, "Hey! Use my personal code to connect on Pulse: $code")
                }
                startActivity(Intent.createChooser(shareIntent, "Share Code"))
            }
        }
        
        binding.joinButton.setOnClickListener {
            val code = binding.coupleIdField.text.toString().trim().uppercase()
            if (code.isNotEmpty()) {
                viewModel.connectCouple(code)
            } else {
                viewModel.showToast("Please enter a code")
            }
        }
    }

    private fun observeState() {
        viewModel.personalCode.observe(viewLifecycleOwner) { code ->
            binding.myCodeText.text = code ?: "------"
        }

        viewModel.status.observe(viewLifecycleOwner) { status ->
            binding.statusLabel.text = status
            val color = when {
                status.contains("Connected", true) -> R.color.md_theme_status_green
                status.contains("failed", true) || status.contains("Invalid", true) -> R.color.md_theme_status_red
                else -> R.color.md_theme_status_amber
            }
            binding.statusLabel.setTextColor(ContextCompat.getColor(requireContext(), color))
        }

        viewModel.busy.observe(viewLifecycleOwner) { busy ->
            binding.joinButton.isEnabled = !busy
            binding.pairLoading.visibility = if (busy) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
