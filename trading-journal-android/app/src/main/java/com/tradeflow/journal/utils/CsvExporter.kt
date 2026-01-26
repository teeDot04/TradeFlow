package com.tradeflow.journal.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tradeflow.journal.data.Trade
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    
    fun exportTradesToCsv(context: Context, trades: List<Trade>): Uri? {
        try {
            val fileName = "tradeflow_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val file = File(context.getExternalFilesDir(null), fileName)
            
            FileWriter(file).use { writer ->
                // Write headers
                writer.append("ID,Symbol,Side,Entry Price,Exit Price,Quantity,")
                writer.append("Gross P&L,Fees,Net P&L,Return %,Entry Time,Exit Time,")
                writer.append("Strategy,Emotion,Market Condition,Setup Quality,Notes,")
                writer.append("OHLCV Avg Price,OHLCV Avg Volume,OHLCV Max High,OHLCV Min Low,")
                writer.append("OHLCV Price Range,OHLCV Volatility %,OHLCV Num Candles,")
                writer.append("Microstructure,Market Context,Risk Metrics,Sentiment,Fundamental\n")
                
                // Write data
                trades.forEach { trade ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    
                    writer.append("${trade.id},")
                    writer.append("${trade.symbol},")
                    writer.append("${trade.side},")
                    writer.append("${trade.entryPrice},")
                    writer.append("${trade.exitPrice},")
                    writer.append("${trade.quantity},")
                    writer.append("${String.format("%.2f", trade.grossPnL)},")
                    writer.append("${String.format("%.2f", trade.totalFees)},")
                    writer.append("${String.format("%.2f", trade.netPnL)},")
                    writer.append("${String.format("%.2f", trade.returnPct)},")
                    writer.append("${dateFormat.format(Date(trade.entryTime))},")
                    writer.append("${dateFormat.format(Date(trade.exitTime))},")
                    writer.append("${trade.strategy},")
                    writer.append("${trade.emotion.name.replace("_", " ")},")
                    writer.append("${trade.marketCondition.name.replace("_", " ")},")
                    writer.append("${trade.setupQuality},")
                    writer.append("\"${trade.notes.replace("\"", "\"\"")}\",")
                    
                    trade.ohlcvData?.let { ohlcv ->
                        writer.append("${String.format("%.2f", ohlcv.avgPrice)},")
                        writer.append("${String.format("%.2f", ohlcv.avgVolume)},")
                        writer.append("${String.format("%.2f", ohlcv.maxHigh)},")
                        writer.append("${String.format("%.2f", ohlcv.minLow)},")
                        writer.append("${String.format("%.2f", ohlcv.priceRange)},")
                        writer.append("${String.format("%.2f", ohlcv.volatility)},")
                        writer.append("${ohlcv.numCandles},")
                    } ?: writer.append(",,,,,,,")
                    
                    // Advanced Data (Raw Strings, preserving pipes)
                    writer.append("\"${trade.microstructure ?: ""}\",")
                    writer.append("\"${trade.marketContext ?: ""}\",")
                    writer.append("\"${trade.riskMetrics ?: ""}\",")
                    writer.append("\"${trade.sentimentData ?: ""}\",")
                    writer.append("\"${trade.fundamentalData ?: ""}\"")
                    
                    writer.append("\n")
                }
            }
            
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
    
    fun shareCsv(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share CSV"))
    }
}
