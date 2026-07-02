package com.pulse.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import android.annotation.SuppressLint
import android.view.MotionEvent
import com.pulse.app.R
import com.pulse.app.databinding.FragmentPairBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        viewModel.loadSavedCoupleId()
        // Auto-generate a code if we are waiting for a partner
        viewModel.createInviteLink()
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
            val link = viewModel.inviteLink.value
            if (link != null) {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Join me on Pulse")
                    putExtra(android.content.Intent.EXTRA_TEXT, "Connect with me ❤️: \nCode: ${viewModel.inviteCode.value} \nLink: $link")
                }
                startActivity(android.content.Intent.createChooser(shareIntent, "Share Invite"))
            }
        }
        
        binding.joinButton.setOnClickListener {
            val code = binding.coupleIdField.text.toString().trim()
            if (code.isNotEmpty()) {
                viewModel.consumeInviteLink(code)
            }
        }
    }

    private fun observeState() {
        viewModel.inviteCode.observe(viewLifecycleOwner) { code ->
            if (code != null) {
                binding.myCodeText.text = code
            }
        }
        viewModel.status.observe(viewLifecycleOwner) { status ->
            if (status.contains("Connected", true)) {
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.statusLabel.text = "Connecting..."
                    delay(400)
                    binding.statusLabel.text = "Partner Found..."
                    delay(500)
                    binding.statusLabel.text = "Connected ❤️"
                    binding.statusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_status_green))
                }
            } else {
                binding.statusLabel.text = status.ifBlank { getString(R.string.status_waiting) }
                val color = when {
                    status.contains("Invalid", true) -> R.color.md_theme_status_red
                    else -> R.color.md_theme_status_amber
                }
                binding.statusLabel.setTextColor(ContextCompat.getColor(requireContext(), color))
            }
        }
        viewModel.busy.observe(viewLifecycleOwner) { busy ->
            binding.shareButton.isEnabled = !busy
            binding.joinButton.isEnabled = !busy
            binding.pairLoading.visibility = if (busy) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
