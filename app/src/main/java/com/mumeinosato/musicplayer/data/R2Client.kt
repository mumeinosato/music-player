package com.mumeinosato.musicplayer.data

import com.mumeinosato.musicplayer.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Cloudflare R2 (S3 互換) から SigV4 署名付き GET でオブジェクトを落とす */
class R2Client(
    private val endpoint: String = BuildConfig.R2_ENDPOINT,
    private val bucket: String = BuildConfig.R2_BUCKET,
    private val accessKeyId: String = BuildConfig.R2_ACCESS_KEY_ID,
    private val secretAccessKey: String = BuildConfig.R2_SECRET_ACCESS_KEY,
) {
    private val region = "auto"
    private val service = "s3"

    /** key を dest にダウンロードする。途中で失敗しても dest は作られない */
    fun download(key: String, dest: File) {
        val base = URL(endpoint.trimEnd('/'))
        val path = "/$bucket/$key"
        val host = if (base.port == -1) base.host else "${base.host}:${base.port}"

        val amzDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val dateStamp = amzDate.substring(0, 8)
        val payloadHash = EMPTY_SHA256

        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalRequest = "GET\n$path\n\n" +
            "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n\n" +
            "$signedHeaders\n$payloadHash"
        val scope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest.toByteArray())}"

        val kDate = hmac("AWS4$secretAccessKey".toByteArray(), dateStamp)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, service)
        val kSigning = hmac(kService, "aws4_request")
        val signature = hmac(kSigning, stringToSign).toHex()

        val conn = URL("${base.protocol}://$host$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("x-amz-content-sha256", payloadHash)
            conn.setRequestProperty("x-amz-date", amzDate)
            conn.setRequestProperty(
                "Authorization",
                "AWS4-HMAC-SHA256 Credential=$accessKeyId/$scope, SignedHeaders=$signedHeaders, Signature=$signature",
            )
            if (conn.responseCode != 200) error("R2 GET $key failed: HTTP ${conn.responseCode}")

            val part = File(dest.parentFile, dest.name + ".part")
            try {
                conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it, 64 * 1024) } }
                // 途中で切れたファイルを完成品として扱わない
                val expected = conn.contentLengthLong
                if (expected >= 0 && part.length() != expected) {
                    error("incomplete download: $key (${part.length()}/$expected bytes)")
                }
                Files.move(part.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                part.delete()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun hmac(key: ByteArray, data: String): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
            .doFinal(data.toByteArray())

    private fun sha256Hex(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
