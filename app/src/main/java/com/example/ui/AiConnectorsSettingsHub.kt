package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AiProvider
import com.example.data.ConnectorDispatchLog
import com.example.data.CustomWebhookConnector
import com.example.data.EmailDeliveryMode
import com.example.data.WebhookPresetType
import com.example.ui.theme.BotanicalAmber
import com.example.ui.theme.BotanicalSage
import com.example.ui.theme.FrauncesFontFamily
import com.example.ui.theme.StudioMonoFontFamily
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.TelegramAzure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AiProviderSettingsCard(
    viewModel: VoiceTaskViewModel,
    onOpenAiStudio: () -> Unit
) {
    val context = LocalContext.current
    val config by viewModel.aiStudioConfig.collectAsStateWithLifecycle()
    val activeProvider = config.activeProvider

    var apiKeyInput by remember(activeProvider, config.geminiApiKey, config.openAiApiKey, config.claudeApiKey) {
        mutableStateOf(config.apiKeyFor(activeProvider))
    }
    var modelInput by remember(activeProvider, config.geminiModel, config.openAiModel, config.claudeModel) {
        mutableStateOf(config.modelFor(activeProvider))
    }
    var revealKey by remember { mutableStateOf(false) }

    val verifiedAt = config.verifiedAtFor(activeProvider)
    val hasKey = config.apiKeyFor(activeProvider).isNotBlank()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (verifiedAt != null && hasKey) BotanicalSage else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_provider_settings_card")
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
                        color = BotanicalSage.copy(alpha = 0.25f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Engine",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AI Engine (BYOK)",
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Private on-device API keys",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        verifiedAt != null && hasKey -> StudioSuccess.copy(alpha = 0.16f)
                        hasKey -> BotanicalAmber.copy(alpha = 0.16f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when {
                            verifiedAt != null && hasKey -> "Verified"
                            hasKey -> "Key Saved"
                            else -> "Key Needed"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            verifiedAt != null && hasKey -> StudioSuccess
                            hasKey -> BotanicalAmber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiProvider.entries.forEach { provider ->
                    BotanicalFilterChip(
                        label = when (provider) {
                            AiProvider.GEMINI -> "Gemini"
                            AiProvider.OPENAI -> "OpenAI"
                            AiProvider.CLAUDE -> "Claude"
                        },
                        selected = activeProvider == provider,
                        onClick = { viewModel.setActiveAiProvider(provider) },
                        modifier = Modifier.testTag("ai_provider_chip_${provider.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = { Text("${activeProvider.displayName} API Key") },
                placeholder = { Text(activeProvider.keyHint) },
                singleLine = true,
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                textStyle = TextStyle(fontFamily = StudioMonoFontFamily, fontSize = 13.sp),
                trailingIcon = {
                    IconButton(onClick = { revealKey = !revealKey }) {
                        Icon(
                            imageVector = if (revealKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (revealKey) "Hide API Key" else "Show API Key"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_api_key_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "MODEL PRESETS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeProvider.modelPresets.forEach { preset ->
                    BotanicalFilterChip(
                        label = preset,
                        selected = modelInput == preset,
                        onClick = { modelInput = preset }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                label = { Text("Active Model ID") },
                singleLine = true,
                textStyle = TextStyle(fontFamily = StudioMonoFontFamily, fontSize = 12.sp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_model_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.saveAiCredentials(activeProvider, apiKeyInput, modelInput) {
                            Toast.makeText(context, "Saved ${activeProvider.displayName} key", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_save_key_btn")
                ) {
                    Text("Save Key", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        viewModel.testActiveAiConnection(activeProvider, apiKeyInput, modelInput) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !viewModel.isTestingAiKey && apiKeyInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.25f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_test_key_btn")
                ) {
                    if (viewModel.isTestingAiKey) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test AI Key", fontSize = 12.sp)
                    }
                }
            }

            if (!viewModel.aiStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (viewModel.aiStatusIsError) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = viewModel.aiStatusMessage!!,
                            fontSize = 12.sp,
                            color = if (viewModel.aiStatusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearAiStatusBanner() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onOpenAiStudio,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .testTag("settings_open_ai_studio_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open AI Studio Quick Sheet", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ConnectorsHubSection(viewModel: VoiceTaskViewModel) {
    val context = LocalContext.current
    val config by viewModel.aiStudioConfig.collectAsStateWithLifecycle()
    val connectorLogs by viewModel.connectorLogs.collectAsStateWithLifecycle()
    val notionDbs = viewModel.discoveredNotionDatabases

    var showLedgerDialog by remember { mutableStateOf(false) }
    var showAddWebhookDialog by remember { mutableStateOf(false) }

    if (showLedgerDialog) {
        ConnectorActivityLedgerDialog(
            logs = connectorLogs,
            onClear = { viewModel.clearConnectorLogs() },
            onDismiss = { showLedgerDialog = false }
        )
    }

    if (showAddWebhookDialog) {
        AddWebhookConnectorDialog(
            onSave = { webhook ->
                viewModel.addOrUpdateWebhook(webhook)
                showAddWebhookDialog = false
                Toast.makeText(context, "${webhook.name} added!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAddWebhookDialog = false }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. Email Connector Card
        var emailMode by remember(config.emailDeliveryMode) { mutableStateOf(config.emailDeliveryMode) }
        var resendKey by remember(config.resendApiKey) { mutableStateOf(config.resendApiKey) }
        var fromEmail by remember(config.emailFromAddress) { mutableStateOf(config.emailFromAddress) }
        var defaultRecipient by remember(config.emailDefaultRecipient) { mutableStateOf(config.emailDefaultRecipient) }
        var revealResend by remember { mutableStateOf(false) }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("email_connector_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Email Connector", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text("Mail App Draft or automated Resend API", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EmailDeliveryMode.entries.forEach { mode ->
                        BotanicalFilterChip(
                            label = mode.label,
                            selected = emailMode == mode,
                            onClick = { emailMode = mode },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = defaultRecipient,
                    onValueChange = { defaultRecipient = it },
                    label = { Text("Default Recipient Email") },
                    placeholder = { Text("teammate@company.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                AnimatedVisibility(visible = emailMode == EmailDeliveryMode.RESEND_API) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = resendKey,
                            onValueChange = { resendKey = it },
                            label = { Text("Resend API Key") },
                            placeholder = { Text("re_...") },
                            singleLine = true,
                            visualTransformation = if (revealResend) VisualTransformation.None else PasswordVisualTransformation(),
                            textStyle = TextStyle(fontFamily = StudioMonoFontFamily, fontSize = 12.sp),
                            trailingIcon = {
                                IconButton(onClick = { revealResend = !revealResend }) {
                                    Icon(
                                        imageVector = if (revealResend) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Resend Key Visibility"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = fromEmail,
                            onValueChange = { fromEmail = it },
                            label = { Text("Verified Sender (From Email)") },
                            placeholder = { Text("notes@yourdomain.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.saveEmailConfig(emailMode, resendKey, fromEmail, defaultRecipient) {
                            Toast.makeText(context, "Email connector saved!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_email_connector_btn")
                ) {
                    Text("Save Email Connector")
                }
            }
        }

        // 2. Notion Connector Card
        var notionToken by remember(config.notionToken) { mutableStateOf(config.notionToken) }
        var revealNotion by remember { mutableStateOf(false) }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = if (config.isNotionConfigured) BotanicalSage else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notion_connector_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Notion Workspace", fontFamily = FrauncesFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                            Text("Save memos & AI drafts as database pages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (config.isNotionConfigured) StudioSuccess.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (config.isNotionConfigured) "Ready" else "Setup",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (config.isNotionConfigured) StudioSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notionToken,
                    onValueChange = { notionToken = it },
                    label = { Text("Notion Integration Secret") },
                    placeholder = { Text("ntn_... or secret_...") },
                    singleLine = true,
                    visualTransformation = if (revealNotion) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = TextStyle(fontFamily = StudioMonoFontFamily, fontSize = 12.sp),
                    trailingIcon = {
                        IconButton(onClick = { revealNotion = !revealNotion }) {
                            Icon(
                                imageVector = if (revealNotion) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Notion Secret Visibility"
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notion_token_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.discoverNotionDatabases(notionToken) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !viewModel.isNotionDiscovering && notionToken.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("discover_notion_dbs_btn")
                    ) {
                        if (viewModel.isNotionDiscovering) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Searching...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Discover DBs", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.testNotionPage { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = config.isNotionConfigured && !viewModel.isConnectorDispatching,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("test_notion_btn")
                    ) {
                        Text("Test Page", fontSize = 12.sp)
                    }
                }

                if (notionDbs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "SELECT TARGET DATABASE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        notionDbs.forEach { db ->
                            BotanicalFilterChip(
                                label = db.title,
                                selected = config.notionDatabaseId == db.id,
                                onClick = { viewModel.selectNotionDatabase(notionToken, db) }
                            )
                        }
                    }
                } else if (config.notionDatabaseId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Linked DB: ${config.notionDatabaseTitle.ifBlank { config.notionDatabaseId.take(12) }} (title: ${config.notionTitleProperty})",
                            fontFamily = StudioMonoFontFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // 3. Slack, Discord & Custom Webhooks Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_webhooks_card")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Slack, Discord & Webhooks",
                            fontFamily = FrauncesFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Connect Zapier, Make, n8n, Slack, or Discord",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showAddWebhookDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_webhook_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (config.customWebhooks.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No webhooks added yet. Tap Add to connect Slack, Discord, Zapier, Make, or n8n.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        config.customWebhooks.forEach { webhook ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                            ) {
                                                Text(
                                                    text = webhook.presetType.name,
                                                    fontFamily = StudioMonoFontFamily,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = webhook.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = webhook.url,
                                            fontFamily = StudioMonoFontFamily,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = {
                                                viewModel.testWebhook(webhook) { _, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        ) {
                                            Text("Test", fontSize = 12.sp)
                                        }
                                        Switch(
                                            checked = webhook.isEnabled,
                                            onCheckedChange = { viewModel.toggleWebhookEnabled(webhook.id, it) }
                                        )
                                        IconButton(
                                            onClick = { viewModel.deleteWebhook(webhook.id) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Webhook",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = { showLedgerDialog = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Connector Dispatch Ledger (${connectorLogs.size})", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AddWebhookConnectorDialog(
    onSave: (CustomWebhookConnector) -> Unit,
    onDismiss: () -> Unit
) {
    var preset by remember { mutableStateOf(WebhookPresetType.SLACK) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var authHeader by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Add Webhook Connector", fontFamily = FrauncesFontFamily) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WebhookPresetType.entries.forEach { p ->
                        BotanicalFilterChip(
                            label = p.label,
                            selected = preset == p,
                            onClick = { preset = p }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Connector Name") },
                    placeholder = { Text("e.g. #team-updates or Zapier Flow") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Webhook Endpoint URL") },
                    placeholder = { Text("https://hooks.slack.com/services/...") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = StudioMonoFontFamily, fontSize = 11.sp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = authHeader,
                    onValueChange = { authHeader = it },
                    label = { Text("Authorization Header (Optional)") },
                    placeholder = { Text("Bearer ...") },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = StudioMonoFontFamily, fontSize = 11.sp),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUrl = url.trim()
                    if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                        onSave(
                            CustomWebhookConnector(
                                name = name.trim().ifBlank { preset.label },
                                presetType = preset,
                                url = cleanUrl,
                                authHeader = authHeader.trim(),
                                isEnabled = true
                            )
                        )
                    }
                },
                enabled = url.trim().startsWith("http://") || url.trim().startsWith("https://")
            ) {
                Text("Save Connector")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConnectorActivityLedgerDialog(
    logs: List<ConnectorDispatchLog>,
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
                        Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connector Dispatch Ledger", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (logs.isNotEmpty()) {
                            TextButton(onClick = onClear) {
                                Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No AI or connector dispatches recorded yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
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
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                                            color = if (log.channel == "TELEGRAM") TelegramAzure.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        ) {
                                            Text(
                                                text = log.channel,
                                                fontFamily = StudioMonoFontFamily,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (log.channel == "TELEGRAM") TelegramAzure else MaterialTheme.colorScheme.primary,
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
                                                "SENT", "EXTRACTED" -> StudioSuccess
                                                else -> MaterialTheme.colorScheme.primary
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(log.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(log.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = fmt.format(Date(log.timestamp)),
                                        fontFamily = StudioMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
