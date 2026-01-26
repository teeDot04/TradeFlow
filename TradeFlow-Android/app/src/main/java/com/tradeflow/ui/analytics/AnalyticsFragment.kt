package com.tradeflow.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.tradeflow.R
import com.tradeflow.data.Trade
import com.tradeflow.databinding.FragmentAnalyticsBinding
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {
    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AnalyticsViewModel
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[AnalyticsViewModel::class.java]
        
        // Observe trades and update charts
        viewModel.allTrades.observe(viewLifecycleOwner) { trades ->
            if (trades.isNotEmpty()) {
                setupPnlChart(trades)
                setupWinLossChart(trades)
                setupDistributionChart(trades)
            }
        }
    }
    
    private fun setupPnlChart(trades: List<Trade>) {
        val entries = mutableListOf<Entry>()
        var cumulativePnl = 0.0
        
        trades.sortedBy { it.exitDate }.forEachIndexed { index, trade ->
            cumulativePnl += trade.pnl
            entries.add(Entry(index.toFloat(), cumulativePnl.toFloat()))
        }
        
        val dataSet = LineDataSet(entries, "Cumulative P&L").apply {
            color = Color.parseColor("#2196F3")
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 3f
            setCircleColor(Color.parseColor("#2196F3"))
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        
        binding.pnlLineChart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
            }
            axisRight.isEnabled = false
            animateX(800)
            invalidate()
        }
    }
    
    private fun setupWinLossChart(trades: List<Trade>) {
        val wins = trades.count { it.isWin }
        val losses = trades.size - wins
        
        val entries = listOf(
            PieEntry(wins.toFloat(), getString(R.string.wins)),
            PieEntry(losses.toFloat(), getString(R.string.losses))
        )
        
        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#4CAF50"),  // Green for wins
                Color.parseColor("#F44336")   // Red for losses
            )
            valueTextSize = 14f
            valueTextColor = Color.WHITE
        }
        
        binding.winLossPieChart.apply {
            data = PieData(dataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            holeRadius = 40f
            setHoleColor(Color.TRANSPARENT)
            setDrawEntryLabels(false)
            animateY(800)
            invalidate()
        }
    }
    
    private fun setupDistributionChart(trades: List<Trade>) {
        // Group trades by symbol and sum P&L
        val symbolPnl = trades.groupBy { it.symbol }
            .mapValues { (_, trades) -> trades.sumOf { it.pnl } }
            .toList()
            .sortedByDescending { it.second }
            .take(10) // Top 10 symbols
        
        val entries = symbolPnl.mapIndexed { index, (_, pnl) ->
            BarEntry(index.toFloat(), pnl.toFloat())
        }
        
        val dataSet = BarDataSet(entries, "P&L by Symbol").apply {
            valueTextSize = 10f
            colors = entries.map { entry ->
                if (entry.y >= 0) Color.parseColor("#4CAF50")
                else Color.parseColor("#F44336")
            }
        }
        
        binding.distributionBarChart.apply {
            data = BarData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return symbolPnl.getOrNull(value.toInt())?.first ?: ""
                    }
                }
            }
            axisRight.isEnabled = false
            animateY(800)
            invalidate()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
