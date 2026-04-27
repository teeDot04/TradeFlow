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
import com.tradeflow.databinding.FragmentDashboardBinding
import com.tradeflow.utils.ThemeManager

/**
 * The Dashboard now shows the agent's real-time "Thought Stream":
 * timestamped state transitions, reasoning lines from DeepSeek, and
 * execution receipts from OKX, all pushed up from the Python loop via
 * [com.tradeflow.agent.ThoughtStream].
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DashboardViewModel
    private lateinit var thoughtAdapter: ThoughtStreamAdapter

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

        thoughtAdapter = ThoughtStreamAdapter()
        binding.thoughtStreamRecyclerView.apply {
            adapter = thoughtAdapter
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        }

        viewModel.lines.observe(viewLifecycleOwner) { lines ->
            thoughtAdapter.submit(lines)
            if (lines.isNotEmpty()) {
                binding.thoughtStreamRecyclerView.scrollToPosition(lines.size - 1)
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            binding.txtAgentState.text = getString(R.string.agent_state_fmt, state)
        }

        binding.swipeRefresh.setOnRefreshListener {
            // Stream is push-based; nothing to pull. Just dismiss the spinner.
            binding.swipeRefresh.isRefreshing = false
        }

        binding.fabTheme.setOnClickListener { toggleTheme() }
    }

    private fun toggleTheme() {
        val current = ThemeManager.getTheme(requireContext())
        val next = if (current == ThemeManager.THEME_LIGHT) ThemeManager.THEME_DARK
                   else ThemeManager.THEME_LIGHT
        ThemeManager.saveTheme(requireContext(), next)
        ThemeManager.applyTheme(next)
        requireActivity().recreate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class ThoughtStreamAdapter :
        RecyclerView.Adapter<ThoughtStreamAdapter.LineViewHolder>() {

        private var lines: List<String> = emptyList()

        fun submit(newLines: List<String>) {
            lines = newLines
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LineViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_thought_line, parent, false)
            return LineViewHolder(view)
        }

        override fun onBindViewHolder(holder: LineViewHolder, position: Int) {
            holder.bind(lines[position])
        }

        override fun getItemCount(): Int = lines.size

        class LineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView: TextView = itemView.findViewById(R.id.thoughtLineText)
            fun bind(line: String) { textView.text = line }
        }
    }
}
