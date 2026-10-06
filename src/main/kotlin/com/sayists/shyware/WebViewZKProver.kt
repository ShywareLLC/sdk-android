package com.sayists.shyware

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Hosts cmd/zk-prover-wasm (the GOOS=js GOARCH=wasm build, unchanged) inside
 * a real android.webkit.WebView -- its JS engine (Chromium/V8) is a real JIT
 * compiler, a fundamentally different performance class from a pure WASM
 * interpreter. See ShywareLLC/sdk-ios's WebViewZKProver.swift (and
 * ShywareLLC/core/cmd/zk-prover-wasi/main.go's doc comment) for the full
 * finding this is the Android half of: a real Groth16 Prove() call measured
 * 3000x+ slower under a pure-interpreter WASM runtime (WasmKit, iOS's only
 * option since iOS blocks third-party JIT) than under a JIT-compiling JS
 * engine -- validated directly against Node/V8 (same engine family as this
 * WebView) before building either native integration: ~400ms end to end,
 * matching native Go's ~144ms.
 *
 * `person_secret` never leaves this WebView's own JS context -- only the
 * values callers explicitly pass into the methods below, or the JS side's
 * own computed results, cross the native/JS boundary via evaluateJavascript.
 *
 * One instance per prover session is enough -- the underlying WASM module
 * stays loaded (it blocks forever via `select{}` on the Go side, by design,
 * to keep its exposed globals alive) for the lifetime of this object's
 * WebView. Construct once, reuse for every commitment/nullifier/proof call
 * in that session, rather than recreating per call.
 *
 * Must be constructed and used from a Context that can host a WebView
 * (an Activity context is the common case; WebView itself requires the
 * main thread for all operations, which this class handles internally via
 * withContext(Dispatchers.Main) -- callers do not need to switch threads
 * themselves).
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewZKProver(private val context: Context) {
    private var webView: WebView? = null
    private var isReady = false

    class ProverException(message: String) : Exception(message)

    /**
     * Loads zk-prover.html (and its co-located wasm_exec.js/zk-prover.wasm,
     * via the android_asset virtual filesystem) and waits for the page's
     * own `shywareReadyPromise` to resolve. Call once before any other
     * method; safe to call again (a no-op) if already ready.
     */
    suspend fun prepare() {
        if (isReady) return
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val view = WebView(context)
                view.settings.javaScriptEnabled = true
                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                webView = view
                view.loadUrl("file:///android_asset/zkweb/zk-prover.html")
            }
        }
        // Poll for window.shywareZKReady here, on the Kotlin side, rather
        // than evaluating `window.shywareReadyPromise.then(...)` per call:
        // Android's WebView.evaluateJavascript, unlike iOS's
        // WKWebView.evaluateJavaScript, does NOT await a returned Promise --
        // it serializes whatever the expression evaluates to synchronously,
        // which for a Promise is the Promise object itself, not its
        // resolved value (caught before this ever ran against a real
        // device). Polling here instead means every later evalZKCall can
        // issue the plain synchronous shywareZK* call directly, with no
        // Promise involved at all -- same real behavior, on both platforms.
        var ready = false
        while (!ready) {
            val result = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<String?> { continuation ->
                    webView!!.evaluateJavascript("!!window.shywareZKReady") { value ->
                        if (continuation.isActive) continuation.resume(value)
                    }
                }
            }
            ready = result == "true"
            if (!ready) kotlinx.coroutines.delay(10)
        }
        isReady = true
    }

    suspend fun computeCommitment(personSecret: String): String {
        val result = evalZKCall("shywareZKComputeCommitment(${jsStringLiteral(personSecret)})")
        return result as? String ?: throw ProverException("Unexpected result shape: $result")
    }

    suspend fun computeNullifier(personSecret: String, pollId: String): String {
        val args = "${jsStringLiteral(personSecret)}, ${jsStringLiteral(pollId)}"
        val result = evalZKCall("shywareZKComputeNullifier($args)")
        return result as? String ?: throw ProverException("Unexpected result shape: $result")
    }

    data class ProveResult(
        val proofBase64: String,
        val commitmentHex: String,
        val nullifierHex: String,
    )

    /**
     * provingKeyBase64 is a large value (hundreds of KB) -- passed as a JS
     * string literal via evaluateJavascript, which has no meaningful size
     * limit for this purpose (confirmed against the same size class in the
     * Node/V8 validation harness this design is based on).
     */
    suspend fun prove(personSecret: String, pollId: String, provingKeyBase64: String): ProveResult {
        val args = "${jsStringLiteral(personSecret)}, ${jsStringLiteral(pollId)}, ${jsStringLiteral(provingKeyBase64)}"
        val result = evalZKCall("shywareZKProve($args)")
        val obj = result as? JSONObject ?: throw ProverException("Unexpected result shape: $result")
        return ProveResult(
            proofBase64 = obj.getString("proof"),
            commitmentHex = obj.getString("commitment"),
            nullifierHex = obj.getString("nullifier"),
        )
    }

    // MARK: - Private

    /**
     * Evaluates `jsCall`, which must be one of the `shywareZK*` globals
     * zk-prover-wasm exposes -- all of which return a plain {ok, value} or
     * {ok, error} object synchronously, not a Promise (confirmed against
     * cmd/zk-prover-wasm/main.go's actual js.FuncOf return values; also
     * confirmed against the Node/V8 validation harness this design is
     * based on). Callers must have already awaited prepare() -- readiness
     * is established there, once, not re-checked per call: unlike iOS's
     * WKWebView.evaluateJavaScript, Android's WebView.evaluateJavascript
     * does not await a returned Promise, so there is no safe way to fold a
     * readiness-wait into this call's own expression (see prepare()'s
     * doc comment for the bug this avoids).
     *
     * Android's WebView.evaluateJavascript callback returns the JS result
     * JSON-serialized as a String (per Android's own documented behavior:
     * non-string JS values are converted via JSON serialization before
     * being handed to the callback) -- parsed here with org.json rather
     * than assumed to already be a native type, unlike iOS's
     * evaluateJavaScript which hands back native Any values directly.
     */
    private suspend fun evalZKCall(jsCall: String): Any? {
        val raw = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<String?> { continuation ->
                val view = webView ?: run {
                    continuation.resumeWithException(ProverException("prepare() must be called before any ZK call"))
                    return@suspendCancellableCoroutine
                }
                view.evaluateJavascript(jsCall) { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
            }
        }
        val json = JSONObject(raw ?: "null")
        val ok = json.optBoolean("ok", false)
        if (!ok) {
            throw ProverException(json.optString("error", "unknown error"))
        }
        return when (val value = json.opt("value")) {
            is JSONObject -> value
            else -> value
        }
    }

    private fun jsStringLiteral(s: String): String {
        // org.json.JSONObject.quote produces a correctly-escaped JS/JSON
        // string literal (handles embedded quotes/backslashes/newlines) --
        // person_secret and the base64 proving key are opaque data, not
        // assumed to be free of any particular character.
        return JSONObject.quote(s)
    }
}
