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
import com.pulse.app.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
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
        binding.emailField.addTextChangedListener(watcher)
        binding.passwordField.addTextChangedListener(watcher)

        binding.loginButton.setOnClickListener {
            viewModel.signIn(
                binding.emailField.text.toString(),
                binding.passwordField.text.toString()
            )
        }

        binding.goToSignup.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_signup)
        }
    }

    private fun observeState() {
        viewModel.busy.observe(viewLifecycleOwner) { busy ->
            binding.loginProgress.visibility = if (busy) View.VISIBLE else View.GONE
            binding.loginButton.isEnabled = !busy && fieldsValid()
        }
    }

    private fun fieldsValid(): Boolean =
        binding.emailField.text.isNullOrBlank().not() && binding.passwordField.text.isNullOrBlank().not()

    private fun toggleButtonState() {
        binding.loginButton.isEnabled = fieldsValid()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
