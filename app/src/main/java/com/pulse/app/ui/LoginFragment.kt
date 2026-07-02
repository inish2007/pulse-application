package com.pulse.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.pulse.app.R
import com.pulse.app.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private val authViewModel: com.pulse.app.auth.AuthViewModel by activityViewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
            authViewModel.loginWithGoogle(credential)
        } catch (e: ApiException) {
            // Handle error, e.g., show toast
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGoogleSignIn()
        setupListeners()
        observeState()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso)
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
            authViewModel.login(
                binding.emailField.text.toString(),
                binding.passwordField.text.toString()
            )
        }

        binding.googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.goToSignup.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_signup)
        }
    }

    private var lastAuthType: String? = null

    private fun observeState() {
        authViewModel.state.observe(viewLifecycleOwner) { state ->
            val type = state::class.simpleName
            if (type == lastAuthType) return@observe
            lastAuthType = type

            when (state) {
                is com.pulse.app.auth.AuthState.Loading -> {
                    binding.loginProgress.visibility = View.VISIBLE
                    binding.loginButton.isEnabled = false
                    binding.googleSignInButton.isEnabled = false
                }
                is com.pulse.app.auth.AuthState.Authenticated -> {
                    binding.loginProgress.visibility = View.GONE
                    // Navigate to pairing (clear login from backstack)
                    val nav = findNavController()
                    nav.popBackStack(R.id.loginFragment, true)
                    nav.navigate(R.id.action_login_to_pair)
                }
                is com.pulse.app.auth.AuthState.Error -> {
                    binding.loginProgress.visibility = View.GONE
                    mainViewModel.showToast(state.message)
                }
                is com.pulse.app.auth.AuthState.Unauthenticated -> {
                    binding.loginProgress.visibility = View.GONE
                }
            }
            binding.loginButton.isEnabled = fieldsValid()
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
