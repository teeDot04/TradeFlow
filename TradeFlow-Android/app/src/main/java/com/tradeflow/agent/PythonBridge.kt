package com.tradeflow.agent

import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the single dedicated thread that all CPython JNI calls flow through.
 *
 * Why a single-thread executor:
 *  - CPython's GIL serialises bytecode execution. Spinning up multiple Java
 *    threads to call into Python would just queue them behind the GIL anyway,
 *    while still risking ANR if any caller happens to be the Main looper.
 *  - Pinning all calls to one thread also keeps the asyncio event loop owned
 *    by `agent_core.run_forever()` on a deterministic, never-Main thread.
 */
object PythonBridge {

    private const val TAG = "PythonBridge"

    private val pyExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "tradeflow-py").apply { isDaemon = true }
    }

    @Volatile private var agentModule: PyObject? = null
    @Volatile private var running = false

    /**
     * Boot the Python agent loop. Safe to call multiple times — subsequent
     * invocations are no-ops while the loop is alive.
     */
    fun start(
        callback: AgentCallback,
        okxApiKey: String,
        okxApiSecret: String,
        okxApiPassphrase: String,
        deepseekApiKey: String,
        dbPath: String
    ) {
        if (running) {
            Log.i(TAG, "Agent loop already running, ignoring start request")
            return
        }
        running = true
        pyExecutor.submit {
            try {
                val py = Python.getInstance()
                val module = py.getModule("agent_core").also { agentModule = it }
                module.callAttr(
                    "boot",
                    callback,
                    okxApiKey,
                    okxApiSecret,
                    okxApiPassphrase,
                    deepseekApiKey,
                    dbPath
                )
            } catch (t: Throwable) {
                running = false
                Log.e(TAG, "Python agent crashed: ${t.message}", t)
                callback.onError("Python agent crashed: ${t.message}")
            }
        }
    }

    /** Request the asyncio loop in agent_core to shut down cleanly. */
    fun stop() {
        if (!running) return
        pyExecutor.submit {
            try {
                agentModule?.callAttr("shutdown")
            } catch (t: Throwable) {
                Log.e(TAG, "Error during Python shutdown", t)
            } finally {
                running = false
            }
        }
    }

    fun isRunning(): Boolean = running
}
