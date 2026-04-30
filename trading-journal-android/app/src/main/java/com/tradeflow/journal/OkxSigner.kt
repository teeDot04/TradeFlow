package com.tradeflow.journal

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object OkxSigner {
    fun signRequest(timestamp: String, method: String, path: String, body: String, secret: String): String {
        val message = timestamp + method.uppercase() + path + body
        val hmacSha256 = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        hmacSha256.init(secretKey)
        val hash = hmacSha256.doFinal(message.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }
}
