package com.pulse.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.pulse.app.R
import com.pulse.app.auth.AuthState
import com.pulse.app.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AuthGateFragment : Fragment() {

    private val authViewModel: AuthViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_auth_gate, container, false)

    private var lastStateType: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authViewModel.state.observe(viewLifecycleOwner) { state ->
            val type = state::class.simpleName
            if (type == lastStateType) return@observe
            lastStateType = type

            when (state) {
                is AuthState.Loading -> {
                    // stay on gate
                }
                is AuthState.Authenticated -> {
                    // If paired, go to signal, else pair
                    mainViewModel.loadSavedCoupleId()
                    val saved = mainViewModel.coupleId.value
                    val dest = if (saved.isNullOrBlank()) R.id.pairFragment else R.id.signalFragment
                    val nav = findNavController()
                    nav.popBackStack(R.id.authGateFragment, true)
                    nav.navigate(dest)
                }
                is AuthState.Unauthenticated -> {
                    val nav = findNavController()
                    nav.popBackStack(R.id.authGateFragment, true)
                    nav.navigate(R.id.loginFragment)
                }
                is AuthState.Error -> {
                    // route to login with an error toast via mainViewModel
                    mainViewModel.showToast("Auth error: ${state.message}")
                    val nav = findNavController()
                    nav.popBackStack(R.id.authGateFragment, true)
                    nav.navigate(R.id.loginFragment)
                }
            }
        }

        // Kick off check
        authViewModel.start()
    }
}
