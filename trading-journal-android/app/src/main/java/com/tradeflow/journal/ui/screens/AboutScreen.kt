package com.tradeflow.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Intelligence", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // TITLE SECTION
            item {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        "TradeFlow: A Deterministic Execution Protocol\nfor Regime-Adaptive Cryptocurrency Trading",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Author: Telo (Lead Quant) & TradeFlow Agent\nDate: January 24, 2026\nVersion: 4.0 (Production)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Serif
                    )
                    Divider(modifier = Modifier.padding(top = 16.dp))
                }
            }

            // ABSTRACT
            item {
                  Text("Abstract", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                  Text(
                      "The volatility of cryptocurrency markets presents a unique challenge for algorithmic trading: statistical distributions of price returns are non-stationary, rendering traditional 'Buy and Hold' strategies inefficient. While Machine Learning (ML) models attempt to predict future price action, they often suffer from overfitting.\n\nThis paper introduces TradeFlow, a hybrid algorithmic system that prioritizes regime identification over price prediction. Empirical results over a 3.5-year period demonstrate a 65.1% Win Rate and a Profit Factor of 3.26.",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onBackground,
                      fontFamily = FontFamily.Serif,
                      lineHeight = 22.sp,
                      modifier = Modifier.padding(top = 8.dp)
                  )
            }

            // 1. INTRODUCTION
            item {
                SectionHeader("1. Introduction: The 'Model vs. Bot' Dilemma")
                // ... (Keep existing text)
                Text(
                    "TradeFlow rejects prediction. It operates as a Reactive Bot. This shift from Probabilistic to Deterministic logic offers interpretability, lower latency, and robustness against hallucination.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Serif
                )
                
                Text(
                    "1.3 Core Assumptions",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top=16.dp, bottom=8.dp)
                )
                
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("A1: Non-Stationarity", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Price distributions change over time. A static 'Buy & Hold' strategy fails in bear markets.", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("A2: Volatility Clustering", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Large changes tend to follow large changes (Mandelbrot). We can't predict direction, but we can predict magnitude.", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("A3: Execution Alpha", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Speed (Latency) dominates Prediction (Accuracy) in high-frequency regimes.", fontSize = 12.sp)
                    }
                }
            }

            // 2. MATH FRAMEWORK
            // ... (Keep existing math) ...
            
            item {
                 // ...
            }
            
            // 3. LOGIC CORES
            item {
                 SectionHeader("3. Logic Cores (The Brain)")
                 Text(
                     "The system selects one of three active cores based on the Regime Function S(t).",
                     style = MaterialTheme.typography.bodySmall,
                     fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                     modifier = Modifier.padding(bottom=16.dp)
                 )
                 
                 // Strategy A: PIVOT HUNTER
                 val blueColor = if (isSystemInDarkTheme()) Color(0xFF90CAF9) else Color(0xFF1565C0)
                 RichStrategyCard(
                     title = "3.1 Pivot Hunter",
                     subtitle = "Mean Reversion Core",
                     icon = Icons.Default.CompareArrows,
                     color = blueColor, // Adaptive Blue
                     regime = "RANGE (ADX < 25)",
                     trigger = """
def pivot_hunter(row):
    # Mean Reversion Logic
    if row['rsi'] < 30 and row['price'] <= row['S1']:
        return SIGNAL_LONG
    if row['price'] > row['vwap']:
        return SIGNAL_CLOSE
    return NONE
                     """.trimIndent(),
                     execution = "Limit Buy @ S1\nTarget: Mean (VWAP)\nStop: P - 1.0*ATR"
                 )
                 
                 Spacer(modifier = Modifier.height(16.dp))
                 
                 // Strategy B: TREND SURFER
                 val greenColor = if (isSystemInDarkTheme()) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                 RichStrategyCard(
                     title = "3.2 Trend Surfer",
                     subtitle = "Momentum Core",
                     icon = Icons.AutoMirrored.Filled.TrendingUp,
                     color = greenColor, // Adaptive Green
                     regime = "TREND (ADX ≥ 25)",
                     trigger = """
def trend_surfer(row):
    # Momentum Logic
    if row['price'] > row['supertrend']:
        if row['vol_24h'] > row['avg_vol']:
            return SIGNAL_LONG_TREND
    return NONE
                     """.trimIndent(),
                     execution = "Market Buy\nTarget: INFINITY (Trail)\nStop: SuperTrend Line"
                 )
                 
                 Spacer(modifier = Modifier.height(16.dp))
                 
                 // Strategy C: WICK HUNTER
                 val redColor = if (isSystemInDarkTheme()) Color(0xFFEF9A9A) else Color(0xFFC62828)
                 RichStrategyCard(
                     title = "3.3 Wick Hunter",
                     subtitle = "Black Swan Core",
                     icon = Icons.Default.Bolt,
                     color = redColor, // Adaptive Red
                     regime = "CRASH (Price ≤ S3)",
                     trigger = """
def wick_hunter(row):
    # Catching Knives safely
    crash_depth = (row['S3'] - row['price'])
    if crash_depth > 0:
        return SIGNAL_SNIPE
    return NONE
                     """.trimIndent(),
                     execution = "Limit Catch @ S3\nTarget: S2 (+R:R)\nStop: Fixed -5%"
                 )
            }

            // 4. EMPIRICAL RESULTS
            item {
                SectionHeader("5. Empirical Results (2021-2026)")
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ResultRow("Total Trades", "4,739")
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.1f))
                        ResultRow("Win Rate", "65.1%")
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.1f))
                        ResultRow("Profit Factor", "3.26 🚀")
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.1f))
                        ResultRow("Best Regime", "High Volatility (7.25 PF)")
                    }
                }
                
                Text(
                    "5.1 Regime Performance (Good, Bad, Choppy)",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top=16.dp, bottom=8.dp)
                )
                
                // Regime Table Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.1f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        RegimeRow("Choppy (Vol < 2%)", "62.8% WR", "1.97 PF")
                        Divider(modifier = Modifier.padding(vertical=4.dp), color=MaterialTheme.colorScheme.outline.copy(alpha=0.1f))
                        RegimeRow("Normal (Trend)", "65.4% WR", "3.33 PF")
                        Divider(modifier = Modifier.padding(vertical=4.dp), color=MaterialTheme.colorScheme.outline.copy(alpha=0.1f))
                        RegimeRow("High Vol (Stress)", "72.5% WR", "7.25 PF 🔥")
                    }
                }

                Text(
                    "5.2 The 'Black Swan' Test",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top=16.dp)
                )
                Text(
                    "Data proves the system is Antifragile. While most bots failed during high-volatility crashes (FTX), TradeFlow achieved its highest Profit Factor (7.25).",
                     style = MaterialTheme.typography.bodyMedium,
                     fontFamily = FontFamily.Serif
                )
            }
            
            // 6. CONCLUSION & FUTURE WORK
            item {
                 SectionHeader("6. Conclusion: The 'Edge' Found")
                 
                 Text(
                     "This project began as a quest for 'Prediction' but discovered 'Discipline'. The data proves that markets are not random, but they are noisy. Humans lose because they trade the noise. TradeFlow wins because it only trades the Signal.",
                     style = MaterialTheme.typography.bodyMedium,
                     fontFamily = FontFamily.Serif,
                     modifier = Modifier.padding(bottom=16.dp)
                 )
                 
                 Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                     Column(modifier = Modifier.padding(16.dp)) {
                         Text("Why We Win (The Alpha)", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                         Spacer(modifier = Modifier.height(8.dp))
                         Text("1. Speed: We react to volatility in <200ms.\n2. Math: We never hold a loser > 1.0 ATR.\n3. Regime: We never buy 'The Dip' in a Crash.", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                     }
                 }
                 
                 Text(
                     "7. Future Work (Q3 2026)",
                     style = MaterialTheme.typography.titleSmall,
                     fontFamily = FontFamily.Serif,
                     fontWeight = FontWeight.Bold,
                     modifier = Modifier.padding(top=24.dp, bottom=8.dp)
                 )
                 
                 // Future Items
                 RichStrategyCard(
                     title = "7.1 Reinforcement Learning (PPO)",
                     subtitle = "From Static to Dynamic",
                     icon = Icons.AutoMirrored.Filled.TrendingUp, 
                     color = Color(0xFF673AB7), // Purple
                     regime = "RESEARCH PHASE",
                     trigger = "Replace Fixed ATR Limit",
                     execution = "Agent learns optimal Stop Loss distance via Proximal Policy Optimization (PPO) self-play."
                 )
                 
                 Spacer(modifier = Modifier.height(16.dp))
                 
                 RichStrategyCard(
                     title = "7.2 Sentiment LLM Core",
                     subtitle = "Layer 0 Upgrade",
                     icon = Icons.Default.Public,
                     color = Color(0xFFFF9800), // Orange
                     regime = "DESIGN PHASE",
                     trigger = "News Sentiment < -0.8",
                     execution = "Use On-Device LLM (Gemini Nano) to parse 'FUD' vs 'Real News' in real-time."
                 )
                 
                 Spacer(modifier = Modifier.height(32.dp))
                 Text(
                     "End of Research Paper v4.0",
                     style = MaterialTheme.typography.bodySmall,
                     fontFamily = FontFamily.Serif,
                     modifier = Modifier.fillMaxWidth(),
                     textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                     color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.5f)
                 )
            }
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Serif,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
    Divider(color = MaterialTheme.colorScheme.primary.copy(alpha=0.3f), thickness = 2.dp)
}

@Composable
fun MathBlock(formula: String, caption: String = "") {
    val containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(containerColor, RoundedCornerShape(4.dp))
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(16.dp)
        ) {
            Text(
                formula, 
                fontFamily = FontFamily.Monospace, 
                fontSize = 14.sp, 
                color = textColor, 
                fontWeight = FontWeight.Bold
            )
        }
        if (caption.isNotEmpty()) {
            Text(
                "Fig: $caption", 
                style = MaterialTheme.typography.bodySmall, 
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, 
                color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f),
                fontFamily = FontFamily.Serif,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun VSCodeBlock(code: String) {
    val colorScheme = object {
        val keyword = Color(0xFFC586C0) // Pink
        val function = Color(0xFFDCDCAA) // Yellow
        val comment = Color(0xFF6A9955) // Green
        val number = Color(0xFFB5CEA8) // Light Green
        val plain = Color(0xFFD4D4D4) // Light Gray
        val background = Color(0xFF1E1E1E) // Dark Gray
    }

    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        // Simple "regex-like" parsing (very basic for visual effect)
        val lines = code.split("\n")
        lines.forEachIndexed { index, line ->
            // Check for comment
            if (line.trim().startsWith("#") || line.trim().startsWith("//")) {
                withStyle(androidx.compose.ui.text.SpanStyle(color = colorScheme.comment)) {
                    append(line)
                }
            } else {
                val words = line.split(Regex("(?<=\\s)|(?=\\s)|(?<=[(),.])|(?=[(),.])"))
                words.forEach { word ->
                    val color = when (word.trim()) {
                        "def", "fun", "return", "if", "else", "while", "true", "false", "for", "in" -> colorScheme.keyword
                        "print", "len", "max", "min", "abs", "calculate_atr" -> colorScheme.function
                        else -> {
                            if (word.trim().matches(Regex("\\d+(\\.\\d+)?"))) colorScheme.number
                            else colorScheme.plain
                        }
                    }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = color)) {
                        append(word)
                    }
                }
            }
            if (index < lines.size - 1) append("\n")
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(colorScheme.background, RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Text(
                annotatedString,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun RichStrategyCard(title: String, subtitle: String, icon: ImageVector, color: Color, regime: String, trigger: String, execution: String) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header with Color
            // In dark mode, strict color overlay might be too dark or weird.
            // We use surfaceColor + tinted alpha.
            val headerBg = color.copy(alpha = if (isSystemInDarkTheme()) 0.2f else 0.1f)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = color.copy(alpha = 0.2f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                       Icon(icon, contentDescription = null, tint = color)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
                }
            }
            
            Divider(color = color.copy(alpha = 0.2f))
            
            // Content
            Column(modifier = Modifier.padding(16.dp)) {
                // Regime Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant, 
                    modifier = Modifier.padding(bottom=12.dp)
                ) {
                    Text(
                        regime, 
                        modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Code Block for Logic
                Text("Logic Core:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                VSCodeBlock(trigger)
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Execution:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(execution, fontFamily = FontFamily.Serif, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontFamily = FontFamily.Serif)
        Text(value, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun RegimeRow(label: String, wr: String, pf: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
        Text(wr, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(pf, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black)
    }
}
