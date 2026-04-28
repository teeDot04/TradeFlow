package com.tradeflow.journal.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradeflow.journal.ui.components.AvoidedTradesDialog
import com.tradeflow.journal.ui.components.PdfFilterDialog
import com.tradeflow.journal.ui.components.PdfFilters
import com.tradeflow.journal.utils.CsvExporter
import com.tradeflow.journal.utils.PdfGenerator
import com.tradeflow.journal.viewmodel.TradeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TradeViewModel, navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trades by viewModel.allTrades.collectAsState()

    val savedMakerFee by viewModel.makerFee.collectAsState()
    val savedTakerFee by viewModel.takerFee.collectAsState()
    val gitUrl by viewModel.gitSyncUrl.collectAsState()

    var makerFee by remember(savedMakerFee) { mutableStateOf(savedMakerFee.toString()) }
    var takerFee by remember(savedTakerFee) { mutableStateOf(savedTakerFee.toString()) }


    var showPdfDialog by remember { mutableStateOf(false) }
    var showAvoidedDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.importTradesFromCsv(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- HEADER ---
            item {
                Text(
                    "Control Panel",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // --- BOT AUTOMATION (HERO) ---
            item {
                SettingsSection(title = "Automation") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.SmartToy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "TradeFlow Agent",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Status: Active",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            // Git URL is now hardcoded in UserPreferencesRepository

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Linked to Git Repository",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }

                            // Private Repo Support
                            val savedToken by viewModel.gitToken.collectAsState()
                            var tokenInput by remember(savedToken) { mutableStateOf(savedToken ?: "") }

                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = {
                                    tokenInput = it
                                    viewModel.saveGitToken(it)
                                },
                                label = { Text("GitHub Token (Private Repo)") },
                                placeholder = { Text("ghp_...") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            }

            // --- AI COMMENT AGENT (NEW) ---
            item {
                SettingsSection(title = "AI Comment Agent") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Auto-write trade reasons",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "An AI agent writes the \"why\" comment for each trade.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            val savedProvider by viewModel.aiProvider.collectAsState()
                            val savedKey by viewModel.aiApiKey.collectAsState()
                            val savedBase by viewModel.aiBaseUrl.collectAsState()
                            val savedModel by viewModel.aiModel.collectAsState()
                            val autoGen by viewModel.aiAutoGenerate.collectAsState()

                            var providerInput by remember(savedProvider) { mutableStateOf(savedProvider) }
                            var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
                            var baseInput by remember(savedBase) { mutableStateOf(savedBase) }
                            var modelInput by remember(savedModel) { mutableStateOf(savedModel) }

                            // Provider selector
                            var providerExpanded by remember { mutableStateOf(false) }
                            val providerOptions = listOf(
                                Triple("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
                                Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
                                Triple("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
                                Triple("Groq", "https://api.groq.com/openai/v1", "llama-3.1-70b-versatile"),
                                Triple("Together AI", "https://api.together.xyz/v1", "meta-llama/Llama-3-8b-chat-hf"),
                                Triple("Custom", "", ""),
                            )

                            ExposedDropdownMenuBox(
                                expanded = providerExpanded,
                                onExpandedChange = { providerExpanded = !providerExpanded },
                            ) {
                                OutlinedTextField(
                                    value = providerInput,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Provider") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    )
                                )
                                ExposedDropdownMenu(
                                    expanded = providerExpanded,
                                    onDismissRequest = { providerExpanded = false },
                                ) {
                                    providerOptions.forEach { (name, base, model) ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                providerInput = name
                                                viewModel.saveAiProvider(name)
                                                if (base.isNotBlank()) {
                                                    baseInput = base
                                                    viewModel.saveAiBaseUrl(base)
                                                }
                                                if (model.isNotBlank()) {
                                                    modelInput = model
                                                    viewModel.saveAiModel(model)
                                                }
                                                providerExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = keyInput,
                                onValueChange = {
                                    keyInput = it
                                    viewModel.saveAiApiKey(it)
                                },
                                label = { Text("API Key") },
                                placeholder = { Text("sk-... / dsk-...") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                )
                            )

                            OutlinedTextField(
                                value = baseInput,
                                onValueChange = {
                                    baseInput = it
                                    viewModel.saveAiBaseUrl(it)
                                },
                                label = { Text("Base URL") },
                                placeholder = { Text("https://api.deepseek.com/v1") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                )
                            )

                            OutlinedTextField(
                                value = modelInput,
                                onValueChange = {
                                    modelInput = it
                                    viewModel.saveAiModel(it)
                                },
                                label = { Text("Model") },
                                placeholder = { Text("deepseek-chat") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Auto-generate on import",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        "Fill notes with AI when missing",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Switch(
                                    checked = autoGen,
                                    onCheckedChange = { viewModel.saveAiAutoGenerate(it) }
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val ok = viewModel.testAiConnection()
                                        Toast.makeText(
                                            context,
                                            if (ok) "AI agent connected" else "AI agent failed - check key/URL",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = keyInput.isNotBlank(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Connection")
                            }
                        }
                    }
                }
            }

            // --- TRADING PARAMETERS ---
            item {
                SettingsSection(title = "Trading Parameters") {
                    SettingsCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = makerFee,
                                onValueChange = {
                                    makerFee = it
                                    it.toDoubleOrNull()?.let { fee -> viewModel.saveMakerFee(fee) }
                                },
                                label = { Text("Maker Fee (%)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = takerFee,
                                onValueChange = {
                                    takerFee = it
                                    it.toDoubleOrNull()?.let { fee -> viewModel.saveTakerFee(fee) }
                                },
                                label = { Text("Taker Fee (%)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }

            // --- DATA MANAGEMENT ---
            item {
                SettingsSection(title = "Data Management") {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Import
                            SettingsActionItem(
                                icon = Icons.Filled.UploadFile,
                                title = "Import CSV Data",
                                subtitle = "Upload existing trade logs",
                                onClick = { launcher.launch("*/*") }
                            )
                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            // Export CSV
                            SettingsActionItem(
                                icon = Icons.Filled.TableChart,
                                title = "Export to CSV",
                                subtitle = "Share raw trade data",
                                onClick = {
                                    scope.launch {
                                        isExporting = true
                                        val uri = CsvExporter.exportTradesToCsv(context, trades)
                                        if (uri != null) {
                                            CsvExporter.shareCsv(context, uri)
                                        } else {
                                            Toast.makeText(context, "Export Failed", Toast.LENGTH_SHORT).show()
                                        }
                                        isExporting = false
                                    }
                                },
                                enabled = !isExporting && trades.isNotEmpty()
                            )

                            // Export PDF
                            SettingsActionItem(
                                icon = Icons.Filled.PictureAsPdf,
                                title = "Generate PDF Report",
                                subtitle = "Create detailed performance review",
                                onClick = { showPdfDialog = true },
                                enabled = !isExporting && trades.isNotEmpty()
                            )

                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            // Delete All Data (Destructive)
                            var showDeleteConfirm by remember { mutableStateOf(false) }
                            SettingsActionItem(
                                icon = Icons.Filled.DeleteForever,
                                title = "Clear All Data",
                                subtitle = "Delete all trades and reset stats",
                                onClick = { showDeleteConfirm = true },
                                textColor = MaterialTheme.colorScheme.error,
                                enabled = trades.isNotEmpty()
                            )

                            if (showDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirm = false },
                                    title = { Text("Delete All Data?") },
                                    text = { Text("This action cannot be undone. multiple imports may have caused duplicate data. Clearing and re-importing fresh is recommended.") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                viewModel.deleteAllTrades()
                                                showDeleteConfirm = false
                                                Toast.makeText(context, "Data Cleared", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Delete Everything")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteConfirm = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }



            // --- ABOUT ---
            item {
                SettingsSection(title = "System Intelligence") {
                    SettingsCard {
                        SettingsActionItem(
                            icon = Icons.Outlined.Info,
                            title = "How It Works (The Brain)",
                            subtitle = "Deep dive into bot logic & math",
                            onClick = { navController.navigate("about") }
                        )
                    }
                }
            }

            // --- FOOTER ---
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "TradeFlow v2.2.0-agent",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "Powered by Cloud Bot & AI Analysis",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    // Dialogs
    if (showPdfDialog) {
        PdfFilterDialog(
            onDismiss = { showPdfDialog = false },
            onGenerate = { filters ->
                showPdfDialog = false
                scope.launch {
                    isExporting = true
                    val stats = viewModel.calculateStats(trades)
                    val uri = PdfGenerator.generatePdf(context, trades, stats, filters)
                    if (uri != null) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share PDF"))
                    } else {
                        Toast.makeText(context, "PDF Generation Failed", Toast.LENGTH_SHORT).show()
                    }
                    isExporting = false
                }
            }
        )
    }

    if (showAvoidedDialog) {
         val avoidedTrades = trades.filter { it.setupQuality == 0 }
         AvoidedTradesDialog(
             trades = avoidedTrades,
             onDismiss = { showAvoidedDialog = false }
         )
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
        content()
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = { content() }
        )
    }
}

@Composable
fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) textColor else MaterialTheme.colorScheme.outline
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
    }
}
