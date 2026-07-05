package com.pulse.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pulse.app.databinding.FragmentHistoryBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.rvHistory.visibility = View.GONE
        binding.viewEmptyState.visibility = View.GONE
        binding.historyLoading.visibility = View.VISIBLE

        // Simulate fetching history. Since there is no HistoryAdapter in the 
        // existing architecture, we simply display the premium empty state 
        // as instructed ("Do NOT fabricate history").
        mainViewModel.getPendingSignals { signals ->
            binding.historyLoading.visibility = View.GONE
            binding.viewEmptyState.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
