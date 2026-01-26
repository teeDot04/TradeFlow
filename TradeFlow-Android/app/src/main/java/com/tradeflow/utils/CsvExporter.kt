package com.tradeflow.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.tradeflow.data.Trade
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object CsvExporter {
    fun exportTradesToCsv(context: Context, trades: List<Trade>): Uri? {
        try {
            val csvContent = generateCsvContent(trades)
            val fileName = "TradeFlow_Export_${System.currentTimeMillis()}.csv"
            
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                    }
                }
                
                uri
            } else {
                // Legacy approach for older Android versions
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val file = File(downloadsDir, fileName)
                
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }
                
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun generateCsvContent(trades: List<Trade>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val builder = StringBuilder()
        
        // Header
        builder.append("Symbol,Type,Quantity,Entry Price,Exit Price,Entry Date,Exit Date,P&L,P&L %,Strategy,Notes\n")
        
        // Data rows
        trades.forEach { trade ->
            builder.append("${trade.symbol},")
            builder.append("${trade.type},")
            builder.append("${trade.quantity},")
            builder.append("${trade.entryPrice},")
            builder.append("${trade.exitPrice},")
            builder.append("${dateFormat.format(Date(trade.entryDate))},")
            builder.append("${dateFormat.format(Date(trade.exitDate))},")
            builder.append("${String.format("%.2f", trade.pnl)},")
            builder.append("${String.format("%.2f", trade.pnlPercentage)},")
            builder.append("${trade.strategy},")
            builder.append("\"${trade.notes.replace("\"", "\"\"")}\"\n")
        }
        
        return builder.toString()
    }
}
