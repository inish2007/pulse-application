package com.pulse.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.pulse.app.R
import com.pulse.app.databinding.FragmentSignupBinding

class SignupFragment : Fragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                toggleButtonState()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.signupEmail.addTextChangedListener(watcher)
        binding.signupPassword.addTextChangedListener(watcher)

        binding.signupButton.setOnClickListener {
            viewModel.signUp(
                binding.signupEmail.text.toString(),
                binding.signupPassword.text.toString()
            )
        }

        binding.goToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_signup_to_login)
        }
    }

    private fun observeState() {
        viewModel.busy.observe(viewLifecycleOwner) { busy ->
            binding.signupProgress.visibility = if (busy) View.VISIBLE else View.GONE
            binding.signupButton.isEnabled = !busy && fieldsValid()
        }
    }

    private fun fieldsValid(): Boolean =
        binding.signupEmail.text.isNullOrBlank().not() && binding.signupPassword.text.isNullOrBlank().not()

    private fun toggleButtonState() {
        binding.signupButton.isEnabled = fieldsValid()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
