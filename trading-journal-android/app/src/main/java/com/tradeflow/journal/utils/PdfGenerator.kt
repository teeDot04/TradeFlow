package com.tradeflow.journal.utils

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.tradeflow.journal.data.Trade
import com.tradeflow.journal.ui.components.PdfFilters
import com.tradeflow.journal.viewmodel.TradeStats
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.DashPathEffect

object PdfGenerator {
    
    private const val PAGE_WIDTH = 595 // A4 width in points
    private const val PAGE_HEIGHT = 842 // A4 height in points
    private const val MARGIN = 40
    
    fun generatePdf(
        context: Context,
        trades: List<Trade>,
        stats: TradeStats,
        filters: PdfFilters
    ): Uri? {
        try {
            val fileName = "tradeflow_report_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            
            // Page 1: Summary
            if (filters.includeSummary) {
                val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create())
                drawSummaryPage(context, page.canvas, stats, trades)
                pdfDocument.finishPage(page)
            }
            
            // Page 2: Trade List
            if (filters.includeTradeList) {
                val filteredTrades = filterTrades(trades, filters)
                pageNumber = drawTradeListPage(pdfDocument, pageNumber, filteredTrades)
            }
            
            // Page 3: Strategy Breakdown
            if (filters.includeStrategyBreakdown) {
                val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create())
                drawStrategyBreakdownPage(page.canvas, trades)
                pdfDocument.finishPage(page)
            }
            
            // Page 4: Symbol Performance
            if (filters.includeSymbolPerformance) {
                val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create())
                drawSymbolPerformancePage(page.canvas, trades)
                pdfDocument.finishPage(page)
            }
            
            // Page 5: AI Market Data Log (New)
            if (filters.includeTradeList) { // Reuse trade list filter
                val filteredTrades = filterTrades(trades, filters)
                pageNumber = drawAdvancedDataPage(pdfDocument, pageNumber, filteredTrades)
            }
            
            // Save to file
            FileOutputStream(file).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
            pdfDocument.close()
            
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun filterTrades(trades: List<Trade>, filters: PdfFilters): List<Trade> {
        var filtered = trades
        
        // Filter by date range
        val now = System.currentTimeMillis()
        filtered = when (filters.dateRange) {
            "7d" -> filtered.filter { it.timestamp > now - 7L * 24 * 60 * 60 * 1000 }
            "30d" -> filtered.filter { it.timestamp > now - 30L * 24 * 60 * 60 * 1000 }
            "90d" -> filtered.filter { it.timestamp > now - 90L * 24 * 60 * 60 * 1000 }
            "1y" -> filtered.filter { it.timestamp > now - 365L * 24 * 60 * 60 * 1000 }
            else -> filtered
        }
        
        // Filter by P&L
        filters.minPnL?.let { min ->
            filtered = filtered.filter { it.netPnL >= min }
        }
        filters.maxPnL?.let { max ->
            filtered = filtered.filter { it.netPnL <= max }
        }
        
        return filtered
    }
    
    private fun drawSummaryPage(context: Context, canvas: Canvas, stats: TradeStats, trades: List<Trade>) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 16f
        }
        
        val profitPaint = Paint().apply {
            color = Color.parseColor("#00C853")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val lossPaint = Paint().apply {
            color = Color.parseColor("#D32F2F")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        var yPosition = MARGIN + 50f
        
        // Title and Logo
        val logoBitmap = BitmapFactory.decodeResource(context.resources, com.tradeflow.journal.R.drawable.app_logo)
        if (logoBitmap != null) {
            val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, 60, 60, true)
            canvas.drawBitmap(scaledLogo, MARGIN.toFloat(), MARGIN.toFloat() + 20, null)
            canvas.drawText("TradeFlow Performance Report", MARGIN.toFloat() + 80, yPosition, titlePaint)
        } else {
            canvas.drawText("TradeFlow Performance Report", MARGIN.toFloat(), yPosition, titlePaint)
        }
        yPosition += 40
        
        canvas.drawText(SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(Date()), MARGIN.toFloat() + (if (logoBitmap != null) 80 else 0), yPosition, bodyPaint)
        yPosition += 60
        
        // Performance Summary
        canvas.drawText("Performance Summary", MARGIN.toFloat(), yPosition, headerPaint)
        yPosition += 40
        
        // Draw stats grid
        drawStatRow(canvas, "Total Trades:", trades.size.toString(), MARGIN.toFloat(), yPosition, bodyPaint)
        yPosition += 35
        
        val pnlColor = if (stats.totalPnL >= 0) profitPaint else lossPaint
        drawStatRow(canvas, "Total P&L:", "$${String.format("%.2f", stats.totalPnL)}", MARGIN.toFloat(), yPosition, bodyPaint, pnlColor)
        yPosition += 35
        
        val winRateColor = if (stats.winRate >= 50) profitPaint else lossPaint
        drawStatRow(canvas, "Win Rate:", "${String.format("%.1f", stats.winRate)}%", MARGIN.toFloat(), yPosition, bodyPaint, winRateColor)
        yPosition += 35
        
        drawStatRow(canvas, "Profit Factor:", String.format("%.2f", stats.profitFactor), MARGIN.toFloat(), yPosition, bodyPaint)
        yPosition += 35
        
        val avgReturnColor = if (stats.avgReturn >= 0) profitPaint else lossPaint
        drawStatRow(canvas, "Avg Return:", "${String.format("%.2f", stats.avgReturn)}%", MARGIN.toFloat(), yPosition, bodyPaint, avgReturnColor)
        yPosition += 50
        
        // Breakdown
        canvas.drawText("Breakdown", MARGIN.toFloat(), yPosition, headerPaint)
        yPosition += 40
        
        drawStatRow(canvas, "Average Win:", "$${String.format("%.2f", stats.avgWin)}", MARGIN.toFloat(), yPosition, bodyPaint)
        yPosition += 35
        
        drawStatRow(canvas, "Average Loss:", "$${String.format("%.2f", stats.avgLoss)}", MARGIN.toFloat(), yPosition, bodyPaint)
        yPosition += 35
        
        drawStatRow(canvas, "Largest Win:", "$${String.format("%.2f", stats.largestWin)}", MARGIN.toFloat(), yPosition, bodyPaint, profitPaint)
        yPosition += 35
        
        drawStatRow(canvas, "Largest Loss:", "$${String.format("%.2f", stats.largestLoss)}", MARGIN.toFloat(), yPosition, bodyPaint, lossPaint)
        
        // Draw Chart Title
        yPosition += 60
        canvas.drawText("Equity Curve (Cumulative P&L)", MARGIN.toFloat(), yPosition, headerPaint)
        yPosition += 20
        
        val chartHeight = 200f
        val chartWidth = PAGE_WIDTH - 2 * MARGIN.toFloat()
        val chartRect = RectF(MARGIN.toFloat(), yPosition, MARGIN.toFloat() + chartWidth, yPosition + chartHeight)
        
        // Draw background
        val chartBgPaint = Paint().apply {
            color = Color.parseColor("#FAFAFA")
            style = Paint.Style.FILL
        }
        canvas.drawRect(chartRect, chartBgPaint)
        
        // Calculate P&L points
        val sortedTrades = trades.sortedBy { it.timestamp }
        val cumulativePnL = mutableListOf<Double>(0.0)
        var currentPnL = 0.0
        sortedTrades.forEach { 
            currentPnL += it.netPnL
            cumulativePnL.add(currentPnL)
        }
        
        if (cumulativePnL.size > 1) {
            val maxPnL = (cumulativePnL.maxOrNull() ?: 0.0).coerceAtLeast(100.0)
            val minPnL = (cumulativePnL.minOrNull() ?: 0.0).coerceAtMost(-100.0)
            // Add some padding to range
            val padding = (maxPnL - minPnL) * 0.1
            val effectiveMax = maxPnL + padding
            val effectiveMin = minPnL - padding
            val range = effectiveMax - effectiveMin
            
            // Grid Paint
            val gridPaint = Paint().apply {
                color = Color.parseColor("#E0E0E0")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            
            // 1. Draw Horizontal Grid & Labels
            val textPaint = Paint().apply {
                color = Color.GRAY
                textSize = 9f
                textAlign = Paint.Align.RIGHT
            }
            
            val steps = 4
            for (i in 0..steps) {
                val value = effectiveMin + (range * i / steps)
                val y = yPosition + chartHeight - ((value - effectiveMin) / range * chartHeight).toFloat()
                
                // Draw line
                canvas.drawLine(chartRect.left, y, chartRect.right, y, gridPaint)
                // Draw label
                canvas.drawText("$${String.format("%.0f", value)}", chartRect.left - 5, y + 3, textPaint)
            }
            
            // 2. Draw Vertical Grid (Time based - roughly)
            val timeSteps = 5
            for (i in 1 until timeSteps) {
                val x = chartRect.left + (chartWidth * i / timeSteps)
                canvas.drawLine(x, chartRect.top, x, chartRect.bottom, gridPaint)
            }
            
            // 3. Draw Zero Line
            if (effectiveMin < 0 && effectiveMax > 0) {
                val zeroY = yPosition + chartHeight - ((0 - effectiveMin) / range * chartHeight).toFloat()
                val zeroLinePaint = Paint().apply {
                    color = Color.GRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                }
                canvas.drawLine(chartRect.left, zeroY, chartRect.right, zeroY, zeroLinePaint)
            }
            
            // 4. Draw Path with Gradient
            val path = Path()
            val fillPath = Path()
            
            val startY = yPosition + chartHeight - ((cumulativePnL[0] - effectiveMin) / range * chartHeight).toFloat()
            path.moveTo(chartRect.left, startY)
            fillPath.moveTo(chartRect.left, startY)
            
            val stepX = chartWidth / (cumulativePnL.size - 1)
            
            for (i in 1 until cumulativePnL.size) {
                val currentX = chartRect.left + i * stepX
                val valY = yPosition + chartHeight - ((cumulativePnL[i] - effectiveMin) / range * chartHeight).toFloat()
                path.lineTo(currentX, valY)
                fillPath.lineTo(currentX, valY)
            }
            
            // Close fill path
            fillPath.lineTo(chartRect.right, chartRect.bottom)
            fillPath.lineTo(chartRect.left, chartRect.bottom)
            fillPath.close()
            
            // Draw Gradient Fill
            val gradientPaint = Paint().apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    0f, chartRect.top, 0f, chartRect.bottom,
                    Color.parseColor("#4400C853"), // Transparent Green
                    Color.parseColor("#0500C853"), // Very transparent
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(fillPath, gradientPaint)
            
            // Draw Line
            val linePaint = Paint().apply {
                color = Color.parseColor("#00C853") // Money Green
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                isAntiAlias = true
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(path, linePaint)
            
            // Draw Border
            val borderPaint = Paint().apply {
                color = Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRect(chartRect, borderPaint)
            
        } else {
             val chartTextPaint = Paint().apply {
                color = Color.GRAY
                textSize = 14f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Not enough data to plot chart", chartRect.centerX(), chartRect.centerY(), chartTextPaint)
        }
    }
    
    private fun drawStatRow(canvas: Canvas, label: String, value: String, x: Float, y: Float, labelPaint: Paint, valuePaint: Paint = labelPaint) {
        canvas.drawText(label, x, y, labelPaint)
        canvas.drawText(value, x + 300, y, valuePaint)
    }
    
    private fun drawTradeListPage(pdfDocument: PdfDocument, startPage: Int, trades: List<Trade>): Int {
        var pageNum = startPage
        
        // If no trades, just return
        if (trades.isEmpty()) return pageNum
        
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 9f
        }
        
        // Start first page
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create())
        var canvas = page.canvas
        var yPosition = MARGIN + 50f
        
        canvas.drawText("Trade History", MARGIN.toFloat(), yPosition, titlePaint)
        yPosition += 40
        
        // Helper to draw headers
        fun drawHeaders(c: Canvas, y: Float) {
            c.drawText("Date", MARGIN.toFloat(), y, headerPaint)
            c.drawText("Symbol", MARGIN + 70f, y, headerPaint)
            // c.drawText("Side", MARGIN + 140f, y, headerPaint) // Removed
            c.drawText("Entry", MARGIN + 160f, y, headerPaint) // Shifted left
            c.drawText("Exit", MARGIN + 230f, y, headerPaint)
            c.drawText("P&L", MARGIN + 300f, y, headerPaint)
            c.drawText("Ret%", MARGIN + 370f, y, headerPaint)
            c.drawText("Strategy", MARGIN + 420f, y, headerPaint)
            
            c.drawLine(MARGIN.toFloat(), y + 10, (PAGE_WIDTH - MARGIN).toFloat(), y + 10, Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            })
        }
        
        drawHeaders(canvas, yPosition)
        yPosition += 25
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        for (trade in trades) {
            // Check if we need new page
            if (yPosition > PAGE_HEIGHT - MARGIN - 20) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create())
                canvas = page.canvas
                yPosition = MARGIN + 40f
                drawHeaders(canvas, yPosition)
                yPosition += 25
            }
            
            val profitColor = if (trade.netPnL >= 0) Color.parseColor("#00C853") else Color.parseColor("#D32F2F")
            val valuePaint = bodyPaint.apply { color = profitColor }
            bodyPaint.color = Color.DKGRAY // Reset for normal text
            
            canvas.drawText(dateFormat.format(Date(trade.timestamp)), MARGIN.toFloat(), yPosition, bodyPaint)
            canvas.drawText(trade.symbol, MARGIN + 70f, yPosition, bodyPaint)
            // canvas.drawText(trade.side.name.take(4), MARGIN + 140f, yPosition, bodyPaint) // Removed
            canvas.drawText(String.format("%.2f", trade.entryPrice), MARGIN + 160f, yPosition, bodyPaint) // Shifted left
            canvas.drawText(String.format("%.2f", trade.exitPrice), MARGIN + 230f, yPosition, bodyPaint)
            
            // Color P&L and Return
            canvas.drawText(String.format("%.2f", trade.netPnL), MARGIN + 300f, yPosition, valuePaint)
            canvas.drawText("${String.format("%.1f", trade.returnPct)}%", MARGIN + 370f, yPosition, valuePaint)
            
            canvas.drawText(trade.strategy.take(12), MARGIN + 420f, yPosition, bodyPaint)
            
            yPosition += 20
        }
        
        pdfDocument.finishPage(page)
        return pageNum
    }
    
    private fun drawStrategyBreakdownPage(canvas: Canvas, trades: List<Trade>) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
        }
        
        var yPosition = MARGIN + 50f
        
        canvas.drawText("Strategy Analysis", MARGIN.toFloat(), yPosition, titlePaint)
        yPosition += 60
        
        val strategyStats = trades.groupBy { it.strategy }
            .mapValues { (_, trades) ->
                StrategyInfo(
                    count = trades.size,
                    totalPnL = trades.sumOf { it.netPnL },
                    winRate = (trades.count { it.netPnL > 0 }.toDouble() / trades.size) * 100
                )
            }
            .toList()
            .sortedByDescending { it.second.totalPnL }
        
        strategyStats.forEach { (strategy, info) ->
            canvas.drawText(strategy, MARGIN.toFloat(), yPosition, headerPaint)
            yPosition += 30
            
            canvas.drawText("Trades: ${info.count}", MARGIN + 20f, yPosition, bodyPaint)
            yPosition += 25
            
            val pnlColor = if (info.totalPnL >= 0) Color.parseColor("#00C853") else Color.parseColor("#D32F2F")
            canvas.drawText("Total P&L: $${String.format("%.2f", info.totalPnL)}", MARGIN + 20f, yPosition, bodyPaint.apply { color = pnlColor })
            bodyPaint.color = Color.DKGRAY
            yPosition += 25
            
            canvas.drawText("Win Rate: ${String.format("%.1f", info.winRate)}%", MARGIN + 20f, yPosition, bodyPaint)
            yPosition += 40
            
            // Draw separator line
            canvas.drawLine(MARGIN.toFloat(), yPosition - 10, (PAGE_WIDTH - MARGIN).toFloat(), yPosition - 10, Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            })
        }
    }
    
    private fun drawSymbolPerformancePage(canvas: Canvas, trades: List<Trade>) {
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
        }
        
        var yPosition = MARGIN + 50f
        
        canvas.drawText("Symbol Performance", MARGIN.toFloat(), yPosition, titlePaint)
        yPosition += 60
        
        val symbolStats = trades.groupBy { it.symbol }
            .mapValues { (_, trades) ->
                SymbolInfo(
                    count = trades.size,
                    totalPnL = trades.sumOf { it.netPnL },
                    winRate = (trades.count { it.netPnL > 0 }.toDouble() / trades.size) * 100,
                    avgReturn = trades.map { it.returnPct }.average()
                )
            }
            .toList()
            .sortedByDescending { it.second.totalPnL }
        
        symbolStats.forEach { (symbol, info) ->
            canvas.drawText(symbol, MARGIN.toFloat(), yPosition, headerPaint)
            yPosition += 30
            
            canvas.drawText("Trades: ${info.count}", MARGIN + 20f, yPosition, bodyPaint)
            yPosition += 25
            
            val pnlColor = if (info.totalPnL >= 0) Color.parseColor("#00C853") else Color.parseColor("#D32F2F")
            canvas.drawText("Total P&L: $${String.format("%.2f", info.totalPnL)}", MARGIN + 20f, yPosition, bodyPaint.apply { color = pnlColor })
            bodyPaint.color = Color.DKGRAY
            yPosition += 25
            
            canvas.drawText("Win Rate: ${String.format("%.1f", info.winRate)}%", MARGIN + 20f, yPosition, bodyPaint)
            yPosition += 25
            
            val returnColor = if (info.avgReturn >= 0) Color.parseColor("#00C853") else Color.parseColor("#D32F2F")
            canvas.drawText("Avg Return: ${String.format("%.2f", info.avgReturn)}%", MARGIN + 20f, yPosition, bodyPaint.apply { color = returnColor })
            bodyPaint.color = Color.DKGRAY
            yPosition += 40
            
            // Draw separator line
            canvas.drawLine(MARGIN.toFloat(), yPosition - 10, (PAGE_WIDTH - MARGIN).toFloat(), yPosition - 10, Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            })
        }
    }
    
    data class StrategyInfo(val count: Int, val totalPnL: Double, val winRate: Double)
    data class SymbolInfo(val count: Int, val totalPnL: Double, val winRate: Double, val avgReturn: Double)

    private fun drawAdvancedDataPage(pdfDocument: PdfDocument, startPage: Int, trades: List<Trade>): Int {
        var pageNum = startPage
        if (trades.isEmpty()) return pageNum
        
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 8f
        }
        
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create())
        var canvas = page.canvas
        var yPosition = MARGIN + 50f
        
        canvas.drawText("AI Market Intelligence Log", MARGIN.toFloat(), yPosition, titlePaint)
        yPosition += 40
        
        fun drawHeaders(c: Canvas, y: Float) {
            c.drawText("Date", MARGIN.toFloat(), y, headerPaint)
            c.drawText("Symbol", MARGIN + 60f, y, headerPaint)
            c.drawText("Spread", MARGIN + 120f, y, headerPaint)
            c.drawText("Imbalance", MARGIN + 170f, y, headerPaint)
            c.drawText("24h Vol", MARGIN + 230f, y, headerPaint)
            c.drawText("ATR", MARGIN + 300f, y, headerPaint)
            c.drawText("Funding", MARGIN + 350f, y, headerPaint)
            
            c.drawLine(MARGIN.toFloat(), y + 10, (PAGE_WIDTH - MARGIN).toFloat(), y + 10, Paint().apply {
                color = Color.LTGRAY; strokeWidth = 1f
            })
        }
        
        drawHeaders(canvas, yPosition)
        yPosition += 25
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        for (trade in trades) {
             if (yPosition > PAGE_HEIGHT - MARGIN - 20) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum++).create())
                canvas = page.canvas
                yPosition = MARGIN + 40f
                drawHeaders(canvas, yPosition)
                yPosition += 25
            }
            
            // Extract Metrics safely
            var spread = "-"
            var imb = "-"
            var vol24 = "-"
            var atr = "-"
            var funding = "-"
            
            try {
                if (trade.microstructure != null) {
                    val json = org.json.JSONObject(trade.microstructure)
                    spread = String.format("%.2f", json.optDouble("spread", 0.0))
                    imb = String.format("%.2f", json.optDouble("imbalance", 0.0))
                }
                if (trade.marketContext != null) {
                    val json = org.json.JSONObject(trade.marketContext)
                    vol24 = String.format("%.0f", json.optDouble("24h_vol", 0.0))
                }
                if (trade.riskMetrics != null) {
                    val json = org.json.JSONObject(trade.riskMetrics)
                    atr = String.format("%.2f", json.optDouble("atr", 0.0))
                }
                if (trade.sentimentData != null) {
                    val json = org.json.JSONObject(trade.sentimentData)
                    funding = json.optString("funding_rate", "-")
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
            
            canvas.drawText(dateFormat.format(Date(trade.timestamp)), MARGIN.toFloat(), yPosition, bodyPaint)
            canvas.drawText(trade.symbol, MARGIN + 60f, yPosition, bodyPaint)
            canvas.drawText(spread, MARGIN + 120f, yPosition, bodyPaint)
            canvas.drawText(imb, MARGIN + 170f, yPosition, bodyPaint)
            canvas.drawText(vol24, MARGIN + 230f, yPosition, bodyPaint)
            canvas.drawText(atr, MARGIN + 300f, yPosition, bodyPaint)
            canvas.drawText(funding, MARGIN + 350f, yPosition, bodyPaint)
            
            yPosition += 20
        }
        
        pdfDocument.finishPage(page)
        return pageNum
    }
}
