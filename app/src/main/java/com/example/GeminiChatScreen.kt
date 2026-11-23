package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeminiChatScreen(
    portfolioSummary: PortfolioSummaryData?,
    isDark: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    var selectedModel by remember { mutableStateOf(GeminiModel.FLASH) }
    var useSearchGrounding by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                role = "model",
                content = "Hello! I am your Quantitative Portfolio AI Advisor powered by Google Gemini. " +
                        "Ask me about your portfolio XIRR, factor risk (alpha/beta/Sharpe), " +
                        "broad market index benchmarks, or live market developments with Google Search Grounding.",
                modelUsed = "gemini-3.5-flash"
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showAuthDialog by remember { mutableStateOf(false) }

    // Audio recording & transcription states
    val audioRecorder = remember { AudioRecordingManager(context) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var isTranscribingAudio by remember { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val started = audioRecorder.startRecording()
            if (started) {
                isRecordingAudio = true
                Toast.makeText(context, "Listening... Speak your investment query", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Could not start audio recorder", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Microphone permission required for voice audio transcription", Toast.LENGTH_SHORT).show()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse"
    )

    fun sendMessage(text: String, isAudio: Boolean = false) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isLoading) return

        val userMsg = ChatMessage(
            role = "user",
            content = trimmed,
            isAudioTranscribed = isAudio
        )
        messages.add(userMsg)
        inputText = ""
        isLoading = true

        coroutineScope.launch {
            listState.animateScrollToItem(messages.size - 1)
            val result = GeminiService.sendChatMessage(
                history = messages.dropLast(1),
                userMessage = trimmed,
                model = selectedModel,
                useSearchGrounding = useSearchGrounding
            )

            result.onSuccess { modelReply ->
                messages.add(modelReply)
                isLoading = false
                listState.animateScrollToItem(messages.size - 1)
            }.onFailure { err ->
                val errorMsg = ChatMessage(
                    role = "model",
                    content = "⚠️ ${err.message ?: "Failed to generate AI response. Please check network or API key."}",
                    modelUsed = selectedModel.modelId
                )
                messages.add(errorMsg)
                isLoading = false
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI",
                            tint = Color(0xFF388E3C),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Gemini AI Advisor",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (useSearchGrounding) "Google Search Grounded • ${selectedModel.displayName}" else selectedModel.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (useSearchGrounding) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAuthDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Cloud & Auth",
                            tint = if (FirebaseAuthFirestoreManager.isFirebaseInitialized(context)) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Quick Suggestion Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (portfolioSummary != null) {
                            SuggestionChip(
                                onClick = {
                                    val summaryPrompt = "Please analyze my current mutual fund portfolio stats: " +
                                            "Total Invested: ₹${String.format(Locale.US, "%.0f", portfolioSummary.totalInvested)}, " +
                                            "Current Value: ₹${String.format(Locale.US, "%.0f", portfolioSummary.totalCurrentValue)}, " +
                                            "Overall Total P&L: ₹${String.format(Locale.US, "%.0f", portfolioSummary.overallTotalPlAmount)} (${String.format(Locale.US, "%.2f", portfolioSummary.overallTotalPlPercent)}%), " +
                                            "Daily P&L: ₹${String.format(Locale.US, "%.0f", portfolioSummary.overallDailyPlAmount)} (${String.format(Locale.US, "%.2f", portfolioSummary.overallDailyPlPercent)}%), " +
                                            "Portfolio XIRR: ${String.format(Locale.US, "%.2f", portfolioSummary.xirrVal)}%. " +
                                            "Provide a quantitative assessment of my risk-adjusted returns and recommendations."
                                    sendMessage(summaryPrompt)
                                },
                                label = { Text("Attach Portfolio Stats", fontSize = 11.sp) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                )
                            )
                        }

                        SuggestionChip(
                            onClick = {
                                sendMessage("Explain how XIRR compounding is calculated and how sequence of returns impacts long-term wealth.")
                            },
                            label = { Text("XIRR & Compounding", fontSize = 11.sp) }
                        )

                        SuggestionChip(
                            onClick = {
                                sendMessage("Compare Small Cap vs Mid Cap funds in terms of volatility, drawdown, and risk-adjusted alpha.")
                            },
                            label = { Text("Small vs Mid Cap Risk", fontSize = 11.sp) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Input Row with Voice Microphone & Send
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Microphone Voice Recording Button
                        IconButton(
                            onClick = {
                                if (isRecordingAudio) {
                                    val audioBytes = audioRecorder.stopRecording()
                                    isRecordingAudio = false
                                    if (audioBytes != null && audioBytes.isNotEmpty()) {
                                        isTranscribingAudio = true
                                        coroutineScope.launch {
                                            val transcribeRes = GeminiService.transcribeAudio(audioBytes)
                                            isTranscribingAudio = false
                                            transcribeRes.onSuccess { transcribedText ->
                                                inputText = transcribedText
                                                Toast.makeText(context, "Transcribed via gemini-3.5-transcribe", Toast.LENGTH_SHORT).show()
                                            }.onFailure { err ->
                                                Toast.makeText(context, "Audio transcribe error: ${err.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "No audio detected", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .then(if (isRecordingAudio) Modifier.scale(pulseScale) else Modifier)
                                .background(
                                    color = if (isRecordingAudio) Color(0xFFD32F2F) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                )
                        ) {
                            if (isTranscribingAudio) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = if (isRecordingAudio) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = "Voice Input",
                                    tint = if (isRecordingAudio) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    if (isRecordingAudio) "Recording audio... Tap mic to stop"
                                    else if (isTranscribingAudio) "Transcribing audio with Gemini..."
                                    else "Ask Gemini about your portfolio..."
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { sendMessage(inputText) },
                            enabled = inputText.isNotBlank() && !isLoading,
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape
                                )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Model Selector & Grounding Bar
            Surface(
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Model choice chips
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            GeminiModel.values().forEach { model ->
                                FilterChip(
                                    selected = selectedModel == model,
                                    onClick = { selectedModel = model },
                                    label = {
                                        Text(
                                            text = when (model) {
                                                GeminiModel.FLASH -> "3.5 Flash"
                                                GeminiModel.PRO -> "3.1 Pro"
                                                GeminiModel.LITE -> "3.1 Lite"
                                            },
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selectedModel == model) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (selectedModel == model) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        // Search Grounding toggle
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "Google Search Grounding",
                                tint = if (useSearchGrounding) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Search",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (useSearchGrounding) FontWeight.Bold else FontWeight.Normal,
                                    color = if (useSearchGrounding) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = useSearchGrounding,
                                onCheckedChange = { useSearchGrounding = it },
                                modifier = Modifier.scale(0.75f)
                            )
                        }
                    }
                }
            }

            // Message Thread
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }

                items(messages, key = { it.id }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        isDark = isDark,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(msg.content))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onOpenUrl = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (isLoading) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (useSearchGrounding) "Querying Gemini with Google Search Grounding..." else "Generating quantitative analysis...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(10.dp)) }
            }
        }
    }

    // Auth & Cloud Sync Dialog
    if (showAuthDialog) {
        val syncStatus = remember { FirebaseAuthFirestoreManager.getSyncStatus(context) }
        var userEmailInput by remember { mutableStateOf(syncStatus.userEmail ?: "") }
        var isSigningIn by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cloud Sync & Firebase Auth")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = syncStatus.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (syncStatus.isAuthenticated) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Active Account",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = syncStatus.userEmail ?: "Authenticated User",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val note = UserPortfolioNote(
                                        title = "Portfolio Snapshot",
                                        content = "Invested: ${portfolioSummary?.totalInvested ?: 0}, XIRR: ${portfolioSummary?.xirrVal ?: 0}%"
                                    )
                                    val res = FirebaseAuthFirestoreManager.syncPortfolioNotesToFirestore(context, listOf(note))
                                    res.onSuccess {
                                        Toast.makeText(context, "Synced snapshot to Firestore / Local storage", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sync Current Portfolio to Cloud")
                        }

                        TextButton(
                            onClick = {
                                FirebaseAuthFirestoreManager.signOut(context)
                                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                showAuthDialog = false
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Sign Out", color = Color(0xFFD32F2F))
                        }
                    } else {
                        OutlinedTextField(
                            value = userEmailInput,
                            onValueChange = { userEmailInput = it },
                            label = { Text("Investor Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (userEmailInput.isNotBlank()) {
                                    isSigningIn = true
                                    coroutineScope.launch {
                                        FirebaseAuthFirestoreManager.signInWithEmail(context, userEmailInput)
                                        isSigningIn = false
                                        showAuthDialog = false
                                        Toast.makeText(context, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = userEmailInput.isNotBlank() && !isSigningIn,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            } else {
                                Text("Sign In with Google / Email")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAuthDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    isDark: Boolean,
    onCopy: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF388E3C), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Gemini",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                }
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser && message.modelUsed != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = message.modelUsed,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        )

                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy message",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                SelectionContainer {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                // If message had voice audio input
                if (message.isAudioTranscribed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice transcribed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Transcribed audio",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // Grounding sources
                if (message.sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Sources:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    message.sources.take(3).forEach { source ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenUrl(source.url) }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = Color(0xFF1976D2),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = source.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF1976D2),
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
