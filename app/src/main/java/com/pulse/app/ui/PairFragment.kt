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
        viewModel.loadSavedCoupleId()
        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                toggleButton()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.coupleIdField.addTextChangedListener(watcher)

        binding.pairButton.setOnClickListener {
            viewModel.pair(binding.coupleIdField.text.toString(), partnerId = null)
        }
    }

    private fun observeState() {
        viewModel.coupleId.observe(viewLifecycleOwner) { id ->
            if (id != null && binding.coupleIdField.text.isNullOrBlank()) {
                binding.coupleIdField.setText(id)
                binding.coupleIdField.setSelection(id.length)
            }
        }
        viewModel.status.observe(viewLifecycleOwner) { status ->
            binding.statusLabel.text = status.ifBlank { getString(R.string.status_waiting) }
            val color = when {
                status.contains("Connected", true) -> R.color.md_theme_status_green
                status.contains("Invalid", true) -> R.color.md_theme_status_red
                else -> R.color.md_theme_status_amber
            }
            binding.statusLabel.setTextColor(ContextCompat.getColor(requireContext(), color))
        }
        viewModel.busy.observe(viewLifecycleOwner) { busy ->
            binding.pairButton.isEnabled = !busy && binding.coupleIdField.text.isNullOrBlank().not()
            binding.pairLoading.visibility = if (busy) View.VISIBLE else View.GONE
        }
    }

    private fun toggleButton() {
        binding.pairButton.isEnabled = binding.coupleIdField.text.isNullOrBlank().not()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
