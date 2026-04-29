package com.tradeflow

import android.content.Context
import com.chaquo.python.Python
import com.tradeflow.data.ThoughtManager
import com.tradeflow.utils.EncryptedPrefs
import java.util.concurrent.Executors

interface AgentCallback {
    fun onStateChanged(state: String)
    fun onThoughtGenerated(thought: String, confidence: Int)
    fun onTradeExecuted(details: String)
    fun onHeartbeat()
}

object PythonBridge {
    private val executor = Executors.newSingleThreadExecutor()
    private var isRunning = false
    
    // Heartbeat callback for UI
    var heartbeatListener: (() -> Unit)? = null

    fun startAgent(context: Context) {
        if (isRunning) return
        isRunning = true
        
        executor.submit {
            try {
                val py = Python.getInstance()
                val agentModule = py.getModule("agent_core")
                
                // Fetch credentials
                val okxKey = EncryptedPrefs.getKey(context, EncryptedPrefs.KEY_OKX_API_KEY) ?: ""
                val okxSecret = EncryptedPrefs.getKey(context, EncryptedPrefs.KEY_OKX_API_SECRET) ?: ""
                val okxPass = EncryptedPrefs.getKey(context, EncryptedPrefs.KEY_OKX_PASSPHRASE) ?: ""
                val deepseekKey = EncryptedPrefs.getKey(context, EncryptedPrefs.KEY_DEEPSEEK_API_KEY) ?: ""

                val callback = object : AgentCallback {
                    override fun onStateChanged(state: String) {
                        ThoughtManager.addThought("STATE: $state", null)
                    }

                    override fun onThoughtGenerated(thought: String, confidence: Int) {
                        ThoughtManager.addThought(thought, confidence)
                    }

                    override fun onTradeExecuted(details: String) {
                        ThoughtManager.addThought("TRADE EXECUTED: $details", 100)
                    }

                    override fun onHeartbeat() {
                        heartbeatListener?.invoke()
                    }
                }

                // Pass the db path
                val dbPath = context.getDatabasePath("tradeflow.db").absolutePath

                agentModule.callAttr("start_agent", 
                    okxKey, okxSecret, okxPass, deepseekKey, dbPath, callback
                )
            } catch (e: Exception) {
                e.printStackTrace()
                ThoughtManager.addThought("CRITICAL ERROR: ${e.message}", null)
                isRunning = false
            }
        }
    }

    fun stopAgent() {
        if (!isRunning) return
        executor.submit {
            try {
                val py = Python.getInstance()
                val agentModule = py.getModule("agent_core")
                agentModule.callAttr("stop_agent")
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRunning = false
                ThoughtManager.addThought("Agent stopped.", null)
            }
        }
    }
    
    fun toggleAgent(active: Boolean) {
        executor.submit {
            try {
                if (Python.isStarted()) {
                    val py = Python.getInstance()
                    val agentModule = py.getModule("agent_core")
                    agentModule.callAttr("set_system_enabled", active)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ThoughtManager.addThought("Error toggling sovereign control.", null)
            }
        }
    }

    fun triggerPanic() {
        executor.submit {
            try {
                if (Python.isStarted()) {
                    val py = Python.getInstance()
                    val agentModule = py.getModule("agent_core")
                    agentModule.callAttr("panic_close_all")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ThoughtManager.addThought("CRITICAL: Error executing PANIC.", null)
            }
        }
    }
}
