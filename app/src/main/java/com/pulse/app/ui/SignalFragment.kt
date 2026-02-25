package com.pulse.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pulse.app.R
import com.pulse.app.databinding.FragmentSignalBinding
import com.pulse.app.util.Emotions

class SignalFragment : Fragment() {

    private var _binding: FragmentSignalBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClicks()
        observeState()
    }

    private fun setupClicks() {
        binding.btnLove.setOnClickListener { send(Emotions.EMOTION_LOVE, it) }
        binding.btnMiss.setOnClickListener { send(Emotions.EMOTION_MISS, it) }
        binding.btnHug.setOnClickListener { send(Emotions.EMOTION_HUG, it) }
        binding.btnAngry.setOnClickListener { send(Emotions.EMOTION_ANGRY, it) }
        binding.goodNightButton.setOnClickListener { send(Emotions.EMOTION_GOOD_NIGHT, it) }
    }

    private fun observeState() {
        viewModel.status.observe(viewLifecycleOwner) { status ->
            binding.statusText.text = status.ifBlank { getString(R.string.status_waiting) }
        }
        viewModel.paired.observe(viewLifecycleOwner) { paired ->
            setButtonsEnabled(paired)
            val color = if (paired) R.color.md_theme_status_green else R.color.md_theme_status_amber
            binding.statusRow.getChildAt(0)
                .background = ContextCompat.getDrawable(requireContext(), R.drawable.status_dot)
            binding.statusRow.getChildAt(0).background?.setTint(
                ContextCompat.getColor(requireContext(), color)
            )
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnLove.isEnabled = enabled
        binding.btnMiss.isEnabled = enabled
        binding.btnHug.isEnabled = enabled
        binding.btnAngry.isEnabled = enabled
        binding.goodNightButton.isEnabled = enabled
        val alpha = if (enabled) 1f else 0.4f
        binding.emotionGrid.alpha = alpha
        binding.goodNightButton.alpha = alpha
    }

    private fun send(emotionId: String, view: View) {
        if (!binding.btnLove.isEnabled) return
        glow(view)
        viewModel.sendEmotion(emotionId)
    }

    private fun glow(target: View) {
        val anim = AlphaAnimation(0.5f, 1f).apply {
            duration = 250
            repeatCount = 1
            repeatMode = AlphaAnimation.REVERSE
        }
        target.startAnimation(anim)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
