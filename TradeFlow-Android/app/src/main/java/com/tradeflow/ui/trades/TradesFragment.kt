package com.tradeflow.ui.trades

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tradeflow.R
import com.tradeflow.data.Trade
import com.tradeflow.databinding.FragmentTradesBinding
import com.tradeflow.utils.Extensions.toCurrency
import com.tradeflow.utils.Extensions.toFormattedDate
import com.tradeflow.utils.Extensions.toPnlColor
import com.tradeflow.utils.Extensions.toPercentage

class TradesFragment : Fragment() {
    private var _binding: FragmentTradesBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: TradesViewModel
    private lateinit var adapter: TradesAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTradesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[TradesViewModel::class.java]
        
        // Set up RecyclerView
        adapter = TradesAdapter()
        binding.tradesRecyclerView.apply {
            this.adapter = this@TradesFragment.adapter
            layoutManager = LinearLayoutManager(context)
        }
        
        // Observe filtered trades
        viewModel.filteredTrades.observe(viewLifecycleOwner) { trades ->
            adapter.submitList(trades)
            binding.emptyTextView.visibility = if (trades.isEmpty()) View.VISIBLE else View.GONE
        }
        
        // Search functionality
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.search(newText ?: "")
                return true
            }
        })
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
