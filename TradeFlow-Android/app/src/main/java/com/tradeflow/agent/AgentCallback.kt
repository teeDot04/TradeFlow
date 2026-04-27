package com.tradeflow.agent

/**
 * Bridge surface used by the Python intelligence engine to push state
 * transitions, reasoning output, and execution receipts back into the
 * Kotlin/UI layer. Implementations MUST marshal updates onto the main
 * thread before touching any view.
 *
 * The interface is deliberately narrow: the Python side calls these
 * methods directly via Chaquopy's `PyObject` -> Java/Kotlin proxy.
 */
interface AgentCallback {

    /** Reasoning event from the Brain phase. */
    fun onThoughtGenerated(thought: String, confidence: Int)

    /** State machine transitions: IDLE -> SENTRY -> TRIGGERED -> EXECUTING -> COOLDOWN. */
    fun onStateChanged(newState: String)

    /** Successful order submission and protective stop placement. */
    fun onTradeExecuted(details: String)

    /** Surface anything that fails — network drops, API errors, bad responses. */
    fun onError(message: String)
}
