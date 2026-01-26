package com.tradeflow.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Research Paper: v2.1.6", fontFamily = FontFamily.Monospace, fontSize = 14.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp), // PDF margins
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // --- HEADER ---
            item {
                Text(
                    "Algorithmic Protocol Specification:\nRegime-Adaptive Execution",
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Author: Autonomous Agent (TradeFlow)\nDate: January 2026\nType: Technical Documentation",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Serif,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f)
                )
                Divider(modifier = Modifier.padding(top=16.dp), color=MaterialTheme.colorScheme.onBackground.copy(alpha=0.2f))
            }

            // 1. ABSTRACT
            item {
                SectionHeader("1. Abstract")
                Paragraph(
                    "This document outlines the mathematical and logical framework of the TradeFlow system. Unlike predictive models, TradeFlow is reactive. It utilizes specific mathematical filters to identify market regimes (Trending vs. Ranging) and deploys deterministic logic gates to execute trades. The system has demonstrated a verified Profit Factor of 2.14 in backtesting."
                )
            }

            // 2. MATHEMATICAL FRAMEWORK
            item {
                SectionHeader("2. Mathematical Framework")
                Paragraph("The core of the system relies on the Average Directional Index (ADX) to enable regime identification.")
                
                SubHeader("2.1 Directional Movement")
                Paragraph("We first calculate the directional movement (DX) based on the smoothed positive and negative directional indicators.")
                TextbookMathBlock(
                    formula = "DX_t = \\frac{|+DI_t - -DI_t|}{+DI_t + -DI_t} \\times 100",
                    caption = "Eq 1. Directional Index Strength"
                )
                
                SubHeader("2.2 The Regime Filter")
                Paragraph("The ADX is derived by smoothing the DX over a 14-period window. This acts as a low-pass filter to remove noise.")
                TextbookMathBlock(
                    formula = "ADX_t = \\frac{1}{14} \\sum_{i=1}^{14} DX_{t-i}",
                    caption = "Eq 2. Average Directional Index"
                )
                
                SubHeader("2.3 State Determination S(t)")
                Paragraph("The system state S(t) is a piecewise function determined by the ADX threshold.")
                TextbookMathBlock(
                    formula = "S(t) = \\begin{cases} \\text{TRENDING}, & \\text{if } ADX_t \\ge 25 \\\\ \\text{RANGING}, & \\text{if } ADX_t < 25 \\end{cases}",
                    caption = "Eq 3. Regime Switch Function"
                )
            }

            // 3. EXECUTION LOGIC
            item {
                SectionHeader("3. Execution Logic")
                Paragraph("Once the state S(t) is determined, the following Python logic executes.")
                
                SubHeader("3.1 Trend Surfing (S(t) = TRENDING)")
                VSCodeBlock(
"""def trend_logic(row):
    # Momentum Core
    if row['price'] > row['SuperTrend']:
        if row['volume'] > row['avg_vol']:
            return SIGNAL_LONG
    return HOLD"""
                )
                
                SubHeader("3.2 Mean Reversion (S(t) = RANGING)")
                VSCodeBlock(
"""def mean_reversion(row):
    # Pivot Hunter
    if row['rsi'] < 30 and row['price'] <= row['S1']:
        return SIGNAL_LONG_LIMIT
    if row['price'] >= row['vwap']:
        return SIGNAL_CLOSE_PROFIT
    return HOLD"""
                )
            }

            // 4. LIMITATIONS
            item {
                SectionHeader("4. Limitations & Risk Factors")
                Paragraph("While the system aims for deterministic execution, several stochastic factors remain:")
                
                SubHeader("4.1 Implementation Shortfall (Slippage)")
                Paragraph("In high-volatility regimes (>5% hourly moves), order book depth may be insufficient to fill limit orders at S1/S3 levels, resulting in missed fills (Opportunity Cost).")
                
                SubHeader("4.2 Exchange Latency")
                Paragraph("The bot operates on a 1-minute candle close cycle. Flash crashes occurring within the 60-second window are invisible until the candle closes.")
                
                SubHeader("4.3 Counterparty Risk")
                Paragraph("Standard centralized exchange risks apply. The bot assumes solvency of the execution venue.")
            }

            // 5. FUTURE WORK (Renumbered)
            item {
                SectionHeader("5. Future Work: Financial ML")
                Paragraph("Version 3.0 will transition from Heuristic Rules to Machine Learning. The primary challenge in Financial ML is stationarity.")
                
                SubHeader("4.1 Fractional Differentiation")
                Paragraph("Standard differentiation (d=1) destroys memory. We use Fractional Differentiation (d < 1) to preserve long-term dependencies while achieving stationarity.")
                TextbookMathBlock(
                    formula = "(1 - B)^d X_t = \\sum_{k=0}^{\\infty} \\omega_k X_{t-k}",
                    caption = "Eq 4. FracDiff Operator"
                )
                Paragraph("The weights ω are calculated iteratively to allow for memory decay:")
                TextbookMathBlock(
                    formula = "\\omega_k = (-1)^k \\prod_{i=0}^{k-1} \\frac{d-i}{k!}",
                    caption = "Eq 5. Weight Decay Coefficients"
                )

                SubHeader("4.2 Triple Barrier Labeling")
                Paragraph("To prevent overfitting, we do not label data based on fixed time horizons. We use the Triple Barrier Method:")
                VSCodeBlock(
"""def get_labels(prices, t_events, pt, sl):
    # 1. Upper Barrier (Profit Take)
    # 2. Lower Barrier (Stop Loss)
    # 3. Vertical Barrier (Time Expiry)
    pass # Implementation in v3.0"""
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.1f))
                Text(
                    "End of Document", 
                    style = MaterialTheme.typography.labelSmall, 
                    modifier = Modifier.fillMaxWidth().padding(top=16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontFamily = FontFamily.Serif
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// --- PAPER STYLE COMPOSABLES ---

@Composable
fun SectionHeader(text: String) {
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(
            text, 
            style = MaterialTheme.typography.titleLarge, 
            fontFamily = FontFamily.Serif, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        // No divider, simple whitespace like a paper
    }
}

@Composable
fun SubHeader(text: String) {
    Text(
        text, 
        style = MaterialTheme.typography.titleMedium, 
        fontFamily = FontFamily.Serif, 
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun Paragraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Serif,
        lineHeight = 24.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.9f),
        textAlign = androidx.compose.ui.text.style.TextAlign.Justify
    )
}

@Composable
fun TextbookMathBlock(formula: String, caption: String = "") {
    // PDF Style: Math blends into the page (Transparent Background)
    // Fixes 2.0: Removed 'Center' alignment to prevent top-clipping.
    // Now uses 'Flex-Start' + Padding to force content into view.
    
    val textColorHex = "#EEEEEE"
    
    val htmlContent = """
        <html>
        <head>
            <script type="text/javascript" async src="https://cdnjs.cloudflare.com/ajax/libs/mathjax/2.7.9/MathJax.js?config=TeX-MML-AM_CHTML"></script>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                body {
                    background-color: transparent;
                    color: $textColorHex;
                    margin: 0;
                    padding: 0;
                    
                    /* FIXED: Align to TOP to prevent top-clipping */
                    display: flex;
                    justify-content: center;
                    align-items: flex-start; 
                    padding-top: 20px; /* Push down into view */
                    
                    height: 100%;
                    width: 100%;
                }
                .math-container {
                    font-size: 18px !important;
                    text-align: center;
                    width: 100%;
                    overflow-x: auto;
                    white-space: nowrap;
                    padding-bottom: 10px;
                }
                /* Hide MathJax loading message */
                #MathJax_Message { display: none !important; }
            </style>
        </head>
        <body>
            <div class="math-container">
                $$ $formula $$
            </div>
        </body>
        </html>
    """.trimIndent()

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp) // Standard height, let scroll handle width
                .background(Color.Transparent)
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    android.webkit.WebView(context).apply {
                         settings.javaScriptEnabled = true
                         settings.useWideViewPort = false
                         settings.loadWithOverviewMode = false
                         
                         setBackgroundColor(android.graphics.Color.TRANSPARENT)
                         isVerticalScrollBarEnabled = false
                         isHorizontalScrollBarEnabled = true 
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (caption.isNotEmpty()) {
            Text(
                caption, 
                style = MaterialTheme.typography.labelSmall, 
                color = MaterialTheme.colorScheme.onBackground.copy(alpha=0.6f),
                fontFamily = FontFamily.Serif,
                modifier = Modifier.padding(top=4.dp)
            )
        }
    }
}

@Composable
fun VSCodeBlock(code: String) {
    val bg = Color(0xFF1E1E1E)
    val pink = Color(0xFFC586C0)
    val blue = Color(0xFF9CDCFE)
    val green = Color(0xFF6A9955)
    val orange = Color(0xFFCE9178)
    val yellow = Color(0xFFDCDCAA)
    val white = Color(0xFFD4D4D4)

    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        val lines = code.split("\n")
        lines.forEachIndexed { index, line ->
            if (line.trim().startsWith("#") || line.trim().startsWith("//")) {
                withStyle(androidx.compose.ui.text.SpanStyle(color = green)) { append(line) }
            } else {
                val regex = Regex("([a-zA-Z0-9_]+|\\W)")
                val matches = regex.findAll(line)
                matches.forEach { match ->
                    val word = match.value
                    val color = when {
                        word in listOf("def", "return", "if", "else", "and", "or", "pass", "class") -> pink
                        word in listOf("print", "len", "sum", "max", "min") -> yellow
                        word.matches(Regex("[0-9]+")) -> Color(0xFFB5CEA8)
                        word.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*")) -> blue
                        else -> white
                    }
                    withStyle(androidx.compose.ui.text.SpanStyle(color = color)) { append(word) }
                }
            }
            if (index < lines.size - 1) append("\n")
        }
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column {
             // Mac Lights
            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = Color(0xFFFF5F56), shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(8.dp)) {}
                Surface(color = Color(0xFFFFBD2E), shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(8.dp)) {}
                Surface(color = Color(0xFF27C93F), shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(8.dp)) {}
            }
            Text(
                annotatedString,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
