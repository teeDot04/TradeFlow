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
import com.tradeflow.data.Thought
import com.tradeflow.data.ThoughtManager
import com.tradeflow.databinding.FragmentDashboardBinding
import com.tradeflow.PythonBridge
import com.tradeflow.utils.EncryptedPrefs
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
    private lateinit var thoughtAdapter: ThoughtAdapter
    
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
        
        // Set up Trades RecyclerView
        tradesAdapter = TradesAdapter()
        binding.recentTradesRecyclerView.apply {
            adapter = tradesAdapter
            layoutManager = LinearLayoutManager(context)
        }

        // Set up Sovereign Control Switch
        val isSovereignActive = EncryptedPrefs.getKey(requireContext(), EncryptedPrefs.KEY_SOVEREIGN_CONTROL) == "true"
        binding.switchSovereignControl.isChecked = isSovereignActive
        PythonBridge.toggleAgent(isSovereignActive) // Initial state push
        
        binding.switchSovereignControl.setOnCheckedChangeListener { _, isChecked ->
            EncryptedPrefs.saveKey(requireContext(), EncryptedPrefs.KEY_SOVEREIGN_CONTROL, isChecked.toString())
            PythonBridge.toggleAgent(isChecked)
        }

        // Set up PANIC Button
        binding.btnPanic.setOnClickListener {
            binding.switchSovereignControl.isChecked = false
            PythonBridge.triggerPanic()
        }

        // Set up Heartbeat Listener
        PythonBridge.heartbeatListener = {
            activity?.runOnUiThread {
                binding.heartbeatLed.animate()
                    .alpha(1.0f)
                    .setDuration(100)
                    .withEndAction {
                        binding.heartbeatLed.animate().alpha(0.3f).setDuration(300).start()
                    }
                    .start()
            }
        }

        // Set up Thought Stream RecyclerView
        thoughtAdapter = ThoughtAdapter()
        binding.thoughtStreamRecyclerView.apply {
            adapter = thoughtAdapter
            layoutManager = LinearLayoutManager(context)
        }
        
        // Observe data
        observeViewModel()
        
        // Set up swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
        
        // Theme toggle FAB
        binding.fabTheme.setOnClickListener {
            toggleTheme()
        }
    }
    
    private fun observeViewModel() {
        // Thoughts
        ThoughtManager.thoughts.observe(viewLifecycleOwner) { thoughts ->
            thoughtAdapter.submitList(thoughts)
        }

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

    // Thought Adapter
    private class ThoughtAdapter : RecyclerView.Adapter<ThoughtAdapter.ThoughtViewHolder>() {
        private var thoughts = emptyList<Thought>()
        
        fun submitList(newThoughts: List<Thought>) {
            thoughts = newThoughts
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThoughtViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_thought, parent, false)
            return ThoughtViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ThoughtViewHolder, position: Int) {
            holder.bind(thoughts[position])
        }
        
        override fun getItemCount() = thoughts.size
        
        class ThoughtViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val timestamp: TextView = itemView.findViewById(R.id.thoughtTimestamp)
            private val message: TextView = itemView.findViewById(R.id.thoughtMessage)
            private val confidence: TextView = itemView.findViewById(R.id.thoughtConfidence)
            
            fun bind(thought: Thought) {
                timestamp.text = thought.getFormattedTime()
                message.text = thought.message
                
                // Red tint for HIBERNATE messages
                if (thought.message.startsWith("HIBERNATE")) {
                    itemView.setBackgroundColor(0x33FF5252) // 20% opacity accent color
                } else {
                    itemView.setBackgroundColor(0x00000000) // Transparent default
                }
                
                if (thought.confidence != null) {
                    confidence.text = "${thought.confidence}%"
                    confidence.visibility = View.VISIBLE
                } else {
                    confidence.visibility = View.GONE
                }
            }
        }
    }
    
    // Trades Adapter
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
