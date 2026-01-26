package com.tradeflow.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tradeflow.R
import com.tradeflow.data.Trade
import com.tradeflow.databinding.FragmentDashboardBinding
import com.tradeflow.utils.Extensions.toCurrency
import com.tradeflow.utils.Extensions.toFormattedDate
import com.tradeflow.utils.Extensions.toPnlColor
import com.tradeflow.utils.Extensions.toPercentage
import com.tradeflow.utils.ThemeManager

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: DashboardViewModel
    private lateinit var tradesAdapter: TradesAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        
        // Set up RecyclerView
        tradesAdapter = TradesAdapter()
        binding.recentTradesRecyclerView.apply {
            adapter = tradesAdapter
            layoutManager = LinearLayoutManager(context)
        }
        
        // Observe data
        observeViewModel()
        
        // Set up swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            // Data automatically updates via LiveData
            binding.swipeRefresh.isRefreshing = false
        }
        
        // Theme toggle FAB
        binding.fabTheme.setOnClickListener {
            toggleTheme()
        }
    }
    
    private fun observeViewModel() {
        // Recent trades
        viewModel.recentTrades.observe(viewLifecycleOwner) { trades ->
            tradesAdapter.submitList(trades)
        }
        
        // Total P&L
        viewModel.totalPnl.observe(viewLifecycleOwner) { pnl ->
            binding.statPnl.findViewById<TextView>(R.id.statLabel).text = 
                getString(R.string.total_pnl)
            binding.statPnl.findViewById<TextView>(R.id.statValue).apply {
                text = pnl.toCurrency()
                setTextColor(pnl.toPnlColor())
            }
        }
        
        // Win Rate
        viewModel.winRate.observe(viewLifecycleOwner) { rate ->
            binding.statWinRate.findViewById<TextView>(R.id.statLabel).text = 
                getString(R.string.win_rate)
            binding.statWinRate.findViewById<TextView>(R.id.statValue).text = 
                rate.toPercentage()
        }
        
        // Profit Factor
        viewModel.profitFactor.observe(viewLifecycleOwner) { factor ->
            binding.statProfitFactor.findViewById<TextView>(R.id.statLabel).text = 
                getString(R.string.profit_factor)
            binding.statProfitFactor.findViewById<TextView>(R.id.statValue).text = 
                String.format("%.2f", factor)
        }
        
        // Average Return
        viewModel.avgReturn.observe(viewLifecycleOwner) { avg ->
            binding.statAvgReturn.findViewById<TextView>(R.id.statLabel).text = 
                getString(R.string.avg_return)
            binding.statAvgReturn.findViewById<TextView>(R.id.statValue).apply {
                text = avg.toPercentage()
                setTextColor(avg.toPnlColor())
            }
        }
    }
    
    private fun toggleTheme() {
        val currentTheme = ThemeManager.getTheme(requireContext())
        val newTheme = if (currentTheme == ThemeManager.THEME_LIGHT) {
            ThemeManager.THEME_DARK
        } else {
            ThemeManager.THEME_LIGHT
        }
        ThemeManager.saveTheme(requireContext(), newTheme)
        ThemeManager.applyTheme(newTheme)
        requireActivity().recreate()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // RecyclerView Adapter
    private class TradesAdapter : RecyclerView.Adapter<TradesAdapter.TradeViewHolder>() {
        private var trades = emptyList<Trade>()
        
        fun submitList(newTrades: List<Trade>) {
            trades = newTrades
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TradeViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_trade, parent, false)
            return TradeViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: TradeViewHolder, position: Int) {
            holder.bind(trades[position])
        }
        
        override fun getItemCount() = trades.size
        
        class TradeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val symbol: TextView = itemView.findViewById(R.id.tradeSymbol)
            private val date: TextView = itemView.findViewById(R.id.tradeDate)
            private val type: TextView = itemView.findViewById(R.id.tradeType)
            private val pnl: TextView = itemView.findViewById(R.id.tradePnl)
            private val pnlPercent: TextView = itemView.findViewById(R.id.tradePnlPercent)
            
            fun bind(trade: Trade) {
                symbol.text = trade.symbol
                date.text = trade.exitDate.toFormattedDate()
                type.text = trade.type.name
                
                val pnlValue = trade.pnl
                pnl.text = pnlValue.toCurrency()
                pnl.setTextColor(pnlValue.toPnlColor())
                
                pnlPercent.text = trade.pnlPercentage.toPercentage()
            }
        }
    }
}
