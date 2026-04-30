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

            // --- SOVEREIGN AGENT API ---
            item {
                SettingsSection(title = "Sovereign Agent API") {
                    SettingsCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val savedOkxKey by viewModel.okxApiKey.collectAsState()
                            val savedOkxSecret by viewModel.okxApiSecret.collectAsState()
                            val savedOkxPass by viewModel.okxApiPassphrase.collectAsState()
                            val savedDsKey by viewModel.deepSeekApiKey.collectAsState()
                            val isSimulated by viewModel.simulatedMode.collectAsState()

                            var okxKey by remember(savedOkxKey) { mutableStateOf(savedOkxKey ?: "") }
                            var okxSecret by remember(savedOkxSecret) { mutableStateOf(savedOkxSecret ?: "") }
                            var okxPass by remember(savedOkxPass) { mutableStateOf(savedOkxPass ?: "") }
                            var dsKey by remember(savedDsKey) { mutableStateOf(savedDsKey ?: "") }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Simulated Mode (Dry Run)", style = MaterialTheme.typography.titleSmall)
                                    Text("Simulates trades without calling OKX API. Works without balance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = isSimulated,
                                    onCheckedChange = { viewModel.saveSimulatedMode(it) }
                                )
                            }
                            
                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                            OutlinedTextField(
                                value = okxKey,
                                onValueChange = { okxKey = it },
                                label = { Text("OKX API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = okxSecret,
                                onValueChange = { okxSecret = it },
                                label = { Text("OKX API Secret") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = okxPass,
                                onValueChange = { okxPass = it },
                                label = { Text("OKX Passphrase") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = dsKey,
                                onValueChange = { dsKey = it },
                                label = { Text("DeepSeek API Key") },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    viewModel.saveOkxCredentials(okxKey, okxSecret, okxPass)
                                    viewModel.saveDeepSeekKey(dsKey)
                                    Toast.makeText(context, "API Credentials Saved", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Save Credentials")
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
                        "TradeFlow v2.1.6",
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
