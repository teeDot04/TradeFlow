package com.tradeflow

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object OkxSigner {
    fun signRequest(timestamp: String, method: String, requestPath: String, body: String, secret: String): String {
        val message = timestamp + method.uppercase() + requestPath + body
        val sha256HMAC = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        sha256HMAC.init(secretKey)
        val hash = sha256HMAC.doFinal(message.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}
