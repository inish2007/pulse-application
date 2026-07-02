# Android Integration Guide - Pulse Pairing System

## Step 1: Update PairFragment.kt UI

The current `PairFragment` already has the right structure. Update the method calls:

```kotlin
// In setupListeners()
binding.joinButton.setOnClickListener {
    val code = binding.coupleIdField.text.toString().trim().uppercase()
    if (code.isEmpty()) {
        viewModel.showToast("Please enter a code")
        return@setOnClickListener
    }
    // FIX: Changed from consumeInviteLink (token) to couple code
    viewModel.joinCoupleCodeUsingManualEntry(code)  // ✓ New method
}

binding.shareButton.setOnClickListener {
    val link = viewModel.inviteLink.value
    val code = viewModel.inviteCode.value
    if (link != null && code != null) {
        val shareText = """
            Connect with me on Pulse ❤️
            
            Quick Link: $link
            OR enter code: $code
            
            Code expires in 15 minutes
        """.trimIndent()
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join me on Pulse")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Invite"))
    }
}
```

---

## Step 2: Fix MainActivity Deep Link Handling

The current code is **almost correct** but needs improvement:

```kotlin
private fun handleDeepLink(intent: android.content.Intent?) {
    if (intent?.action == android.content.Intent.ACTION_VIEW) {
        val data = intent.data
        if (data?.scheme == "https" && data.host == "pulse.app" && data.path?.startsWith("/invite") == true) {
            val token = data.lastPathSegment
            if (token != null && token.isNotEmpty()) {
                // FIX: Extract token properly (remove any trailing slashes)
                val cleanToken = token.substringBefore("?")
                Log.d("DeepLink", "Extracted token: $cleanToken")
                
                // Navigate to Pair fragment and consume link
                viewModel.consumeInviteLink(cleanToken)
            } else {
                viewModel.showToast("Invalid invite link")
            }
        }
    }
}
```

**Add to AndroidManifest.xml** (Already configured, but verify):

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data 
        android:scheme="https" 
        android:host="pulse.app" 
        android:pathPrefix="/invite" />
</intent-filter>
```

---

## Step 3: Create SignalFragment.kt

Create a new fragment for the vibration/signal screen:

```kotlin
package com.pulse.app.ui

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.pulse.app.R
import com.pulse.app.databinding.FragmentSignalBinding
import kotlinx.coroutines.launch

class SignalFragment : Fragment() {

    private var _binding: FragmentSignalBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val vibrator: Vibrator by lazy {
        requireContext().getSystemService(Vibrator::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observePartnerSignals()
    }

    private fun setupListeners() {
        // Vibrate button
        binding.vibrateButton.setOnClickListener {
            sendSignal("vibrate")
        }

        // Heart button
        binding.heartButton?.setOnClickListener {
            sendSignal("heart")
        }

        // Kiss button
        binding.kissButton?.setOnClickListener {
            sendSignal("kiss")
        }

        // Thinking button
        binding.thinkingButton?.setOnClickListener {
            sendSignal("thinking")
        }

        // Disconnect button
        binding.disconnectButton?.setOnClickListener {
            // TODO: Implement unpair logic
            viewModel.showToast("Feature coming soon")
        }
    }

    private fun sendSignal(signalType: String) {
        val coupleId = viewModel.coupleId.value
        if (coupleId == null) {
            viewModel.showToast("Not paired yet")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.vibrateButton.isEnabled = false
                val response = viewModel.sendVibrationSignal(coupleId, signalType)
                if (response.success) {
                    vibrateDevice(500) // Local haptic feedback
                    showSentAnimation(signalType)
                    viewModel.showToast("Sent ❤️")
                }
            } catch (e: Exception) {
                viewModel.showToast("Failed to send: ${e.localizedMessage}")
            } finally {
                binding.vibrateButton.isEnabled = true
            }
        }
    }

    private fun observePartnerSignals() {
        // Listen from WebSocket
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.incomingSignal.collect { signal ->
                val signalType = signal.signal_type ?: "vibrate"
                vibrateDevice(300)
                showReceivedAnimation(signalType, signal.sender_name ?: "Partner")
                viewModel.showToast("${signal.sender_name} sent ${signalType}! 💕")
            }
        }
    }

    private fun vibrateDevice(milliseconds: Long) {
        try {
            val effect = VibrationEffect.createOneShot(
                milliseconds,
                VibrationEffect.DEFAULT_AMPLITUDE
            )
            vibrator.vibrate(effect)
        } catch (e: Exception) {
            // Device doesn't support vibration
        }
    }

    private fun showSentAnimation(signalType: String) {
        binding.statusLabel.text = "Sent ❤️"
        binding.statusLabel.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.md_theme_status_green)
        )
        binding.statusLabel.animate()
            .alpha(1f)
            .setDuration(300)
            .withEndAction {
                binding.statusLabel.animate()
                    .alpha(0.5f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    private fun showReceivedAnimation(signalType: String, partnerName: String) {
        binding.statusLabel.text = "$partnerName sent $signalType! 💕"
        binding.statusLabel.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.md_theme_status_green)
        )
        
        // Pulse animation
        binding.statusLabel.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(200)
            .withEndAction {
                binding.statusLabel.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()

        // Clear after 2 seconds
        binding.statusLabel.postDelayed({
            binding.statusLabel.text = "Waiting for partner..."
            binding.statusLabel.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.md_theme_status_amber)
            )
        }, 2000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

---

## Step 4: Add Signal Event to MainViewModel

Update `MainViewModel.kt` to handle incoming signals:

```kotlin
private val _incomingSignal = MutableLiveData<WsSignalEvent>()
val incomingSignal: LiveData<WsSignalEvent> = _incomingSignal

// In init block, add signal handling to WebSocket listener:
init {
    viewModelScope.launch(ioDispatcher) {
        webSocketClient.events.collectLatest { event ->
            when (event) {
                is WsEvent.PartnerConnected -> {
                    _paired.postValue(true)
                    _status.postValue("Connected ❤️")
                    _navEvents.postValue(Event(Destination.SIGNAL))
                }
                is WsEvent.SignalReceived -> {  // NEW
                    _incomingSignal.postValue(event)
                }
                else -> {}
            }
        }
    }
}

// Add method to send signal
fun sendVibrationSignal(coupleId: String, signalType: String = "vibrate") {
    viewModelScope.launch(ioDispatcher) {
        try {
            val response = repository.sendVibrationSignal(coupleId, signalType)
            if (!response.success) {
                _toast.postValue("Failed to send signal")
            }
        } catch (e: Exception) {
            _toast.postValue("Error: ${e.localizedMessage}")
        }
    }
}
```

---

## Step 5: Update WebSocketClient.kt

Add signal event handling:

```kotlin
sealed class WsEvent {
    data class PartnerConnected(val partnerId: String, val partnerName: String) : WsEvent()
    data class SignalReceived(
        val signalId: String,
        val senderId: String,
        val senderName: String,
        val signal_type: String
    ) : WsEvent()
    object Connected : WsEvent()
    object Disconnected : WsEvent()
}

// In onMessage handler:
override fun onMessage(webSocket: WebSocket, text: String) {
    Log.d("WebSocketClient", "Message: $text")
    try {
        val json = JSONObject(text)
        when (json.optString("type")) {
            "PARTNER_CONNECTED" -> {
                val partner = json.getJSONObject("partner")
                _events.tryEmit(
                    WsEvent.PartnerConnected(
                        partner.getString("id"),
                        partner.getString("name")
                    )
                )
            }
            "SIGNAL_RECEIVED" -> {  // NEW
                _events.tryEmit(
                    WsEvent.SignalReceived(
                        json.getString("signal_id"),
                        json.getString("sender_id"),
                        json.getString("sender_name"),
                        json.getString("signal_type")
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.e("WebSocketClient", "Parse error", e)
    }
}
```

---

## Step 6: Create fragment_signal.xml Layout

Create `res/layout/fragment_signal.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="24dp">

    <TextView
        android:id="@+id/partnerNameLabel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Connected ❤️"
        android:textSize="28sp"
        android:textStyle="bold"
        android:textColor="@color/md_theme_status_green"
        android:layout_marginBottom="40dp" />

    <TextView
        android:id="@+id/statusLabel"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Waiting for partner..."
        android:textSize="16sp"
        android:textColor="@color/md_theme_status_amber"
        android:layout_marginBottom="60dp" />

    <GridLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:columnCount="2"
        android:rowCount="2"
        android:layout_marginBottom="40dp">

        <!-- Vibrate Button -->
        <Button
            android:id="@+id/vibrateButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_rowWeight="1"
            android:layout_columnWeight="1"
            android:layout_row="0"
            android:layout_column="0"
            android:text="Vibrate 📳"
            android:layout_margin="8dp"
            android:backgroundTint="@color/md_theme_primary" />

        <!-- Heart Button -->
        <Button
            android:id="@+id/heartButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_rowWeight="1"
            android:layout_columnWeight="1"
            android:layout_row="0"
            android:layout_column="1"
            android:text="Heart ❤️"
            android:layout_margin="8dp"
            android:backgroundTint="@color/md_theme_primary" />

        <!-- Kiss Button -->
        <Button
            android:id="@+id/kissButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_rowWeight="1"
            android:layout_columnWeight="1"
            android:layout_row="1"
            android:layout_column="0"
            android:text="Kiss 💋"
            android:layout_margin="8dp"
            android:backgroundTint="@color/md_theme_primary" />

        <!-- Thinking Button -->
        <Button
            android:id="@+id/thinkingButton"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_rowWeight="1"
            android:layout_columnWeight="1"
            android:layout_row="1"
            android:layout_column="1"
            android:text="Thinking 💭"
            android:layout_margin="8dp"
            android:backgroundTint="@color/md_theme_primary" />

    </GridLayout>

    <Button
        android:id="@+id/disconnectButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Disconnect"
        android:backgroundTint="@android:color/darker_gray" />

</LinearLayout>
```

---

## Step 7: Update Navigation Graph

Add SignalFragment to your navigation graph (`res/navigation/nav_graph.xml`):

```xml
<fragment
    android:id="@+id/signalFragment"
    android:name="com.pulse.app.ui.SignalFragment"
    android:label="Signal">
    <action
        android:id="@+id/action_signalFragment_to_pairFragment"
        app:destination="@id/pairFragment" />
</fragment>

<!-- Update pairFragment action -->
<fragment
    android:id="@+id/pairFragment"
    android:name="com.pulse.app.ui.PairFragment"
    android:label="Pair">
    <action
        android:id="@+id/action_pairFragment_to_signalFragment"
        app:destination="@id/signalFragment" />
</fragment>
```

---

## Step 8: Update MainActivity Navigation Handler

Update `MainActivity.kt` to handle navigation events:

```kotlin
private fun observeNavigation() {
    viewModel.navEvents.observe(this) { event ->
        event.getContentIfNotHandled()?.let { destination ->
            val navController = findNavController()
            when (destination) {
                MainViewModel.Destination.SIGNAL -> {
                    navController.navigate(R.id.signalFragment)
                }
                MainViewModel.Destination.PAIR -> {
                    navController.navigate(R.id.pairFragment)
                }
                MainViewModel.Destination.LOGIN -> {
                    navController.navigate(R.id.loginFragment)
                }
                MainViewModel.Destination.SIGNUP -> {
                    navController.navigate(R.id.signupFragment)
                }
            }
        }
    }
}

private fun findNavController() =
    (supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment)
        .navController
```

---

## Step 9: Add to build.gradle (if needed)

Ensure you have vibrator support:

```gradle
dependencies {
    // Already included in framework
    // android.os.Vibrator is built-in
}
```

---

## Testing Checklist

- [ ] **Deep Link Test:** Share link → User clicks → Joins successfully
- [ ] **Code Test:** Generate code → Manual entry → Joins successfully
- [ ] **Navigation Test:** After pairing → Auto-navigates to SignalFragment
- [ ] **WebSocket Test:** Both users connected → Receive partner connection event
- [ ] **Signal Test:** Send vibrate → Partner device vibrates
- [ ] **Offline Test:** Kill app → WebSocket disconnects → FCM fallback
- [ ] **Edge Case:** Same user tries to join own code → Error response

---

## Debugging

Enable logging:
```kotlin
// In MainActivity or PulseApp
if (BuildConfig.DEBUG) {
    HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
}
```

Check logs:
```bash
adb logcat | grep "WebSocketClient\|DeepLink\|MainViewModel"
```

