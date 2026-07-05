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
        
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.viewEmptyState.visibility = View.GONE
        binding.historyLoading.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = true

        binding.swipeRefresh.setOnRefreshListener {
            loadHistory()
        }

        loadHistory()
    }

    private fun loadHistory() {
        mainViewModel.getPendingSignals { signals ->
            binding.swipeRefresh.isRefreshing = false
            
            // Premium animation for empty state
            if (binding.viewEmptyState.visibility == View.GONE) {
                binding.viewEmptyState.alpha = 0f
                binding.viewEmptyState.translationY = 50f
                binding.viewEmptyState.visibility = View.VISIBLE
                
                binding.viewEmptyState.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.OvershootInterpolator())
                    .start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.viewEmptyState.animate().cancel()
        _binding = null
    }
}
