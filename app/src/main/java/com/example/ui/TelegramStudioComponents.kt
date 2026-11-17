package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TelegramActivityLog
import com.example.data.TelegramConfig
import com.example.data.TelegramTokenSource
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.TelegramAzure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelegramSettingsCard(
    viewModel: VoiceTaskViewModel,
    onOpenActivityLog: () -> Unit
) {
    val context = LocalContext.current
    val config by viewModel.telegramConfig.collectAsStateWithLifecycle()
    val tokenSource = viewModel.getTelegramTokenSource(config)
    val deepLinkUrl = viewModel.pendingTelegramDeepLink
        ?: if (!config.botUsername.isNullOrBlank() && !config.pendingCode.isNullOrBlank()) {
            "https://t.me/${config.botUsername}?start=${config.pendingCode}"
        } else null

    TelegramSettingsCardContent(
        config = config,
        tokenSource = tokenSource,
        deepLinkUrl = deepLinkUrl,
        isTokenSaving = viewModel.isTelegramTokenSaving,
        isVerifying = viewModel.isTelegramVerifying,
        isSyncing = viewModel.isTelegramSyncing,
        isSendingFinance = viewModel.isSendingFinanceTelegram,
        statusMsg = viewModel.telegramStatusMessage,
        statusIsError = viewModel.telegramStatusIsError,
        onSaveCustomBotToken = { rawToken ->
            viewModel.saveCustomTelegramBotToken(rawToken)
        },
        onClearCustomBotToken = {
            viewModel.clearCustomTelegramBotToken()
        },
        onGenerateVerificationLink = { username ->
            viewModel.generateTelegramVerificationLink(username) { link ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                }.onFailure {
                    Toast.makeText(context, "Open this link in Telegram: $link", Toast.LENGTH_LONG).show()
                }
            }
        },
        onCheckVerification = { viewModel.checkTelegramVerification(silent = false) },
        onDisconnect = {
            viewModel.disconnectTelegram()
            Toast.makeText(context, "Telegram disconnected", Toast.LENGTH_SHORT).show()
        },
        onSetAutoSendReminders = { viewModel.setAutoSendTelegramReminders(it) },
        onSetAutoSendFinanceAlerts = { viewModel.setAutoSendFinanceAlerts(it) },
        onSetIncludeAudio = { viewModel.setIncludeAudioInTelegram(it) },
        onSetAiCommandsEnabled = { viewModel.setAiTelegramCommandsEnabled(it) },
        onSendTestPing = {
            viewModel.sendTelegramTestPing { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        onSyncNow = {
            viewModel.syncTelegramNow(silent = false) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        onSendFinanceSummary = {
            viewModel.sendFinanceSummaryToTelegram { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
        onDismissStatus = { viewModel.clearTelegramStatusBanner() },
        onOpenActivityLog = onOpenActivityLog
    )
}

@Composable
internal fun TelegramSettingsCardContent(
    config: TelegramConfig,
    tokenSource: TelegramTokenSource,
    deepLinkUrl: String?,
    isTokenSaving: Boolean,
    isVerifying: Boolean,
    isSyncing: Boolean,
    isSendingFinance: Boolean,
    statusMsg: String?,
    statusIsError: Boolean,
    onSaveCustomBotToken: (String) -> Unit,
    onClearCustomBotToken: () -> Unit,
    onGenerateVerificationLink: (String) -> Unit,
    onCheckVerification: () -> Unit,
    onDisconnect: () -> Unit,
    onSetAutoSendReminders: (Boolean) -> Unit,
    onSetAutoSendFinanceAlerts: (Boolean) -> Unit,
    onSetIncludeAudio: (Boolean) -> Unit,
    onSetAiCommandsEnabled: (Boolean) -> Unit,
    onSendTestPing: () -> Unit,
    onSyncNow: () -> Unit,
    onSendFinanceSummary: () -> Unit,
    onDismissStatus: () -> Unit,
    onOpenActivityLog: () -> Unit
) {
    val context = LocalContext.current
    val isBotConfigured = tokenSource != TelegramTokenSource.NONE
    var usernameInput by remember(config.username) { mutableStateOf(config.username) }
    var botTokenInput by remember(config.customBotToken) { mutableStateOf(config.customBotToken) }
    var showTokenPlaintext by remember { mutableStateOf(false) }
    var showGuide by remember(tokenSource) { mutableStateOf(tokenSource == TelegramTokenSource.NONE) }
    var showTokenEditor by remember(tokenSource) { mutableStateOf(tokenSource == TelegramTokenSource.NONE) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (config.isVerified) BotanicalSage else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("telegram_settings_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = TelegramAzure.copy(alpha = 0.14f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Telegram Notifications",
                                tint = TelegramAzure,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Telegram Notifications",
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Reminders, Finance SMS alerts & commands",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        !isBotConfigured -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        config.isVerified -> StudioSuccess.copy(alpha = 0.16f)
                        deepLinkUrl != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when {
                            !isBotConfigured -> "Token Needed"
                            config.isVerified -> "Connected"
                            deepLinkUrl != null -> "Pending Link"
                            else -> "Not Linked"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            !isBotConfigured -> MaterialTheme.colorScheme.error
                            config.isVerified -> StudioSuccess
                            deepLinkUrl != null -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("tg_status_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bot Token Source & Setup Bar
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    1.dp,
                    if (isBotConfigured) MaterialTheme.colorScheme.outlineVariant
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (tokenSource) {
                                    TelegramTokenSource.IN_APP -> "BOT TOKEN · IN-APP"
                                    TelegramTokenSource.BUILD_SECRET -> "BOT TOKEN · BUILD SECRET"
                                    TelegramTokenSource.NONE -> "BOT TOKEN · REQUIRED"
                                },
                                fontFamily = StudioMonoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBotConfigured) TelegramAzure else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = when (tokenSource) {
                                    TelegramTokenSource.IN_APP -> {
                                        val botHandle = config.botUsername?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Custom Bot"
                                        "Using your BotFather token ($botHandle)"
                                    }
                                    TelegramTokenSource.BUILD_SECRET -> "Using pre-configured app secret token"
                                    TelegramTokenSource.NONE -> "Create a free bot with @BotFather and paste its token below"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (isBotConfigured) {
                            TextButton(
                                onClick = { showTokenEditor = !showTokenEditor },
                                modifier = Modifier.testTag("tg_toggle_token_editor_btn")
                            ) {
                                Text(if (showTokenEditor) "Hide" else "Edit Bot", fontSize = 12.sp)
                            }
                        }
                    }

                    // Collapsible Step-by-Step BotFather Guide
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showGuide = !showGuide }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag("tg_setup_guide_toggle"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Step-by-step @BotFather setup guide",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = if (showGuide) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showGuide) "Collapse setup guide" else "Expand setup guide",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = showGuide,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .testTag("tg_setup_guide_panel"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BotFatherGuideStep(
                                step = "1",
                                text = "Open @BotFather in Telegram and send /newbot to name your personal bot."
                            )
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BotFather")))
                                    }.onFailure {
                                        Toast.makeText(context, "Search @BotFather inside Telegram.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, TelegramAzure.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 42.dp)
                                    .testTag("tg_open_botfather_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    tint = TelegramAzure,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open @BotFather in Telegram", fontSize = 12.sp, color = TelegramAzure)
                            }
                            BotFatherGuideStep(
                                step = "2",
                                text = "Copy the HTTP API token BotFather sends (e.g. 123456:AAH...) and paste it below."
                            )
                            BotFatherGuideStep(
                                step = "3",
                                text = "Tap Save & Verify Bot, then enter your @username below to generate a pairing link."
                            )
                            BotFatherGuideStep(
                                step = "4",
                                text = "Tap Start in your new bot inside Telegram, return here, and tap Check Verification."
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showTokenEditor || !isBotConfigured,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            OutlinedTextField(
                                value = botTokenInput,
                                onValueChange = { botTokenInput = it },
                                label = { Text("@BotFather HTTP API Token") },
                                placeholder = { Text("123456789:AAH...") },
                                singleLine = true,
                                visualTransformation = if (showTokenPlaintext) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showTokenPlaintext = !showTokenPlaintext },
                                        modifier = Modifier.testTag("tg_token_visibility_btn")
                                    ) {
                                        Icon(
                                            imageVector = if (showTokenPlaintext) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showTokenPlaintext) "Hide token" else "Show token"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("tg_bot_token_input")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onSaveCustomBotToken(botTokenInput) },
                                    enabled = !isTokenSaving && botTokenInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .testTag("tg_save_token_btn")
                                ) {
                                    if (isTokenSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Verifying...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Save & Verify Bot", fontSize = 12.sp)
                                    }
                                }

                                if (config.customBotToken.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            botTokenInput = ""
                                            onClearCustomBotToken()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .heightIn(min = 44.dp)
                                            .testTag("tg_clear_token_btn")
                                    ) {
                                        Text("Clear", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!config.isVerified) {
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = { Text("Your Telegram Username") },
                    placeholder = { Text("@your_username") },
                    leadingIcon = {
                        Text(
                            text = "@",
                            fontWeight = FontWeight.Bold,
                            color = TelegramAzure,
                            fontFamily = StudioMonoFontFamily
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("telegram_username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onGenerateVerificationLink(usernameInput) },
                    enabled = !isVerifying && usernameInput.isNotBlank() && isBotConfigured,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("telegram_verify_link_btn")
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preparing Bot Link...")
                    } else {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verify Username via Bot Link", fontWeight = FontWeight.SemiBold)
                    }
                }

                AnimatedVisibility(
                    visible = deepLinkUrl != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, TelegramAzure.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "STEP 2 · TAP START IN TELEGRAM",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    color = TelegramAzure
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (!config.pendingCode.isNullOrBlank()) {
                                    Text(
                                        text = "Pairing Code: ${config.pendingCode}",
                                        fontFamily = StudioMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                                Text(
                                    text = "Open @${config.botUsername ?: "bot"} in Telegram, press Start, then tap Check Verification below.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            deepLinkUrl?.let { url ->
                                                runCatching {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                }.onFailure {
                                                    Toast.makeText(context, "Unable to open Telegram app.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text("Open Bot", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = onCheckVerification,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier
                                            .weight(1.25f)
                                            .heightIn(min = 48.dp)
                                            .testTag("telegram_check_verify_btn")
                                    ) {
                                        Text("Check Verification", fontSize = 12.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, TelegramAzure.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "@${config.username}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TelegramAzure
                            )
                            Text(
                                text = "Linked to @${config.botUsername ?: "FinanceTaskBot"} · Chat #${config.chatId?.toString()?.takeLast(4) ?: "****"}",
                                fontFamily = StudioMonoFontFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onDisconnect,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = "Disconnect Telegram",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-send Reminder Alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Push Telegram alerts automatically when reminders fire.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.autoSendReminders,
                        onCheckedChange = onSetAutoSendReminders,
                        modifier = Modifier.testTag("tg_auto_reminders_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-send Finance SMS Alerts", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Send instant transaction alerts or batch summaries on new SMS imports.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.autoSendFinanceAlerts,
                        onCheckedChange = onSetAutoSendFinanceAlerts,
                        modifier = Modifier.testTag("tg_auto_finance_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Attach Voice Audio Files", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Upload recorded audio clips inside Telegram alerts.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.includeAudioAttachments,
                        onCheckedChange = onSetIncludeAudio,
                        modifier = Modifier.testTag("tg_attach_audio_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bot Commands (/finance, /ask & /digest)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Query your Finance balances, tasks, and AI assistant directly from chat.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.aiTelegramCommandsEnabled,
                        onCheckedChange = onSetAiCommandsEnabled,
                        modifier = Modifier.testTag("tg_ai_commands_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSendTestPing,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramAzure, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("tg_test_ping_btn")
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Ping", fontSize = 12.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("tg_sync_now_btn")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Two-Way", fontSize = 12.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSendFinanceSummary,
                    enabled = !isSendingFinance && !isSyncing,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BotanicalSage),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .testTag("tg_send_finance_summary_btn")
                ) {
                    if (isSendingFinance) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sending Finance Summary...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Finance Summary to Telegram", fontSize = 12.sp)
                    }
                }
            }

            if (!statusMsg.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (statusIsError) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusMsg,
                            fontSize = 12.sp,
                            color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissStatus,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onOpenActivityLog,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View Telegram Activity Log", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BotFatherGuideStep(
    step: String,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    fontFamily = StudioMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TelegramActivitySheet(
    logs: List<TelegramActivityLog>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 580.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = TelegramAzure,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Telegram Activity Ledger",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Bot Updates", fontSize = 12.sp)
                    }
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = onClear) {
                            Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Telegram dispatches yet",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scheduled reminders, Finance alerts, and manual sends appear here.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val fmt = remember { SimpleDateFormat("MMM d, hh:mm:ss a", Locale.getDefault()) }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (log.direction == "INBOUND") {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            } else {
                                                TelegramAzure.copy(alpha = 0.14f)
                                            }
                                        ) {
                                            Text(
                                                text = if (log.direction == "INBOUND") "TG → APP" else "APP → TG",
                                                fontFamily = StudioMonoFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (log.direction == "INBOUND") MaterialTheme.colorScheme.primary else TelegramAzure,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }

                                        Text(
                                            text = log.status,
                                            fontFamily = StudioMonoFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (log.status) {
                                                "FAILED" -> MaterialTheme.colorScheme.error
                                                "SYNCED" -> StudioSuccess
                                                else -> TelegramAzure
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = log.title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = log.detail,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = fmt.format(Date(log.timestamp)),
                                        fontFamily = StudioMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
