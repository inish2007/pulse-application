package com.pulse.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.pulse.app.R
import com.pulse.app.databinding.FragmentSignalBinding
import com.pulse.app.util.Emotions
import com.pulse.app.util.VibrationManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignalFragment : Fragment() {

    private var _binding: FragmentSignalBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    
    private var selectedEmotion: String = Emotions.EMOTION_LOVE
    private lateinit var vibrationManager: VibrationManager
    
    private val emotionViews by lazy {
        mapOf(
            Emotions.EMOTION_LOVE to binding.btnLove,
            Emotions.EMOTION_MISS to binding.btnMiss,
            Emotions.EMOTION_HUG to binding.btnHug,
            Emotions.EMOTION_ANGRY to binding.btnAngry,
            Emotions.EMOTION_GOOD_NIGHT to binding.goodNightButton
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignalBinding.inflate(inflater, container, false)
        vibrationManager = VibrationManager(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClicks()
        observeState()
        updateSelectionUI() // Initial selection state
    }

    private fun setupClicks() {
        // Selection clicks
        emotionViews.forEach { (emotionId, view) ->
            view.setOnClickListener {
                if (!view.isEnabled) return@setOnClickListener
                selectedEmotion = emotionId
                updateSelectionUI()
                playHaptic(emotionId)
            }
        }
        
        // Send click
        binding.fabSend.setOnClickListener {
            if (!binding.fabSend.isEnabled || viewModel.busy.value == true) return@setOnClickListener
            sendCurrentEmotion()
        }
    }

    private fun observeState() {
        viewModel.status.observe(viewLifecycleOwner) { status ->
            binding.statusText.text = status.ifBlank { getString(R.string.status_waiting) }
        }
        
        viewModel.paired.observe(viewLifecycleOwner) { paired ->
            setButtonsEnabled(paired)
            val color = if (paired) R.color.md_theme_status_green else R.color.md_theme_status_amber
            binding.statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), color)
            )
        }
        
        viewModel.busy.observe(viewLifecycleOwner) { isBusy ->
            binding.sendLoading.visibility = if (isBusy) View.VISIBLE else View.GONE
            binding.ivSendIcon.visibility = if (isBusy) View.INVISIBLE else View.VISIBLE
            binding.fabSend.isEnabled = !isBusy && viewModel.paired.value == true
            
            if (isBusy) {
                binding.fabSendGlow.visibility = View.VISIBLE
                pulseGlow()
            } else {
                binding.fabSendGlow.visibility = View.INVISIBLE
                binding.fabSendGlow.clearAnimation()
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        emotionViews.values.forEach { it.isEnabled = enabled }
        binding.fabSend.isEnabled = enabled && viewModel.busy.value != true
        val alpha = if (enabled) 1f else 0.4f
        binding.cardSignals.alpha = alpha
        binding.fabSend.alpha = alpha
    }

    private fun updateSelectionUI() {
        emotionViews.forEach { (emotionId, view) ->
            val isSelected = emotionId == selectedEmotion
            
            // Animate scale
            val scale = if (isSelected) 1.05f else 0.95f
            val alpha = if (isSelected) 1.0f else 0.6f
            
            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .alpha(alpha)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator())
                .start()
                
            // Update glassmorphic border/background dynamically if desired
            if (isSelected) {
                view.setBackgroundResource(R.drawable.bg_button_gradient)
                // Set inner icon color to white for contrast against gradient
                (view.getChildAt(0) as? android.widget.LinearLayout)?.let { layout ->
                    (layout.getChildAt(1) as? TextView)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onPrimary))
                }
            } else {
                view.setBackgroundResource(R.drawable.bg_glass_card)
                (view.getChildAt(0) as? android.widget.LinearLayout)?.let { layout ->
                    (layout.getChildAt(1) as? TextView)?.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onSurface))
                }
            }
        }
    }

    private fun sendCurrentEmotion() {
        playHaptic(selectedEmotion)
        animateSendButton()
        viewModel.sendEmotion(selectedEmotion)
        playParticleBurst()
    }
    
    private fun playHaptic(emotionId: String) {
        try {
            vibrationManager.play(emotionId)
        } catch (e: Exception) {
            // Permission might not be granted
        }
    }

    private fun animateSendButton() {
        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            binding.fabSend,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f)
        ).apply { duration = 100 }

        val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
            binding.fabSend,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f)
        ).apply { 
            duration = 300
            interpolator = OvershootInterpolator()
        }

        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
            start()
        }
    }
    
    private fun pulseGlow() {
        val anim = android.view.animation.AlphaAnimation(0.2f, 0.8f).apply {
            duration = 600
            repeatCount = android.view.animation.Animation.INFINITE
            repeatMode = android.view.animation.Animation.REVERSE
        }
        binding.fabSendGlow.startAnimation(anim)
    }
    
    private fun playParticleBurst() {
        val iconRes = when (selectedEmotion) {
            Emotions.EMOTION_LOVE -> "❤️"
            Emotions.EMOTION_MISS -> "🥺"
            Emotions.EMOTION_HUG -> "🤗"
            Emotions.EMOTION_ANGRY -> "😤"
            Emotions.EMOTION_GOOD_NIGHT -> "🌙"
            else -> "✨"
        }
        
        // Simple particle animation overlay
        val particle = TextView(requireContext()).apply {
            text = iconRes
            textSize = 48f
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 250 // Align roughly with the FAB
            }
        }
        
        binding.animationOverlay.addView(particle)
        
        val flyUp = ObjectAnimator.ofFloat(particle, View.TRANSLATION_Y, 0f, -800f)
        val fadeOut = ObjectAnimator.ofFloat(particle, View.ALPHA, 1f, 0f)
        val scaleX = ObjectAnimator.ofFloat(particle, View.SCALE_X, 0.5f, 1.5f)
        val scaleY = ObjectAnimator.ofFloat(particle, View.SCALE_Y, 0.5f, 1.5f)
        
        AnimatorSet().apply {
            playTogether(flyUp, fadeOut, scaleX, scaleY)
            duration = 1500
            interpolator = android.view.animation.DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Null-safe: binding may be null if fragment destroyed during animation
                    _binding?.animationOverlay?.removeView(particle)
                }
            })
            start()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel all infinite animations to prevent leaks
        binding.fabSendGlow.clearAnimation()
        // Cancel all running ViewPropertyAnimators on emotion cards
        emotionViews.values.forEach { it.animate().cancel() }
        // Remove any in-flight particle views from overlay
        binding.animationOverlay.removeAllViews()
        _binding = null
    }
}
