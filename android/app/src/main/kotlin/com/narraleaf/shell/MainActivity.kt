package com.narraleaf.shell

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * The whole shell: one Activity hosting one WebView that plays the injected
 * payload.
 *
 * Plain android.app.Activity, not AppCompat — the shell carries no libraries
 * (see app/build.gradle.kts for why that matters beyond size).
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var config: ShellConfig

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config = ShellConfig.load(assets)
        requestedOrientation = config.orientation.toActivityInfo()

        if (BuildConfig.DEBUG) {
            // Release builds never enable this: it would let anything on the
            // device attach to the game's WebView.
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView = WebView(this).apply {
            setBackgroundColor(config.backgroundColor)
            val server = WwwServer(assets, decoderFor(config))
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = server.intercept(request)
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                // The payload is local and complete; nothing may be fetched.
                allowFileAccess = false
                allowContentAccess = false
                // Let the game start its own music and play video inline —
                // without this the WebView holds playback until a gesture and
                // an opening track never sounds.
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = false
                useWideViewPort = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                textZoom = 100
            }
        }
        setContentView(webView)

        // The window is opaque and full-bleed; the game paints every pixel.
        window.setBackgroundDrawable(null)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        disableBackKey()

        if (savedInstanceState == null) {
            webView.loadUrl(WwwServer.ENTRY_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersiveMode()
        }
    }

    /**
     * The Back key and the back gesture, going nowhere.
     *
     * A NarraLeaf game is one document that never navigates, so Back has nothing
     * to go back to, and the other platforms the same game ships to have no such
     * input at all. Binding it to something — an in-game menu, "press again to
     * exit" — would be a behaviour the author never asked for and cannot see
     * from the editor, on the one platform out of four that has the key. A
     * mobile build that wants a way out draws a button, where its author put it.
     *
     * Doing nothing is not the default: an Activity that ignores Back finishes
     * itself, dropping the process and with it every line the player has read
     * since their last save — no warning, and nothing to recover from. The web
     * shell refuses the same navigation for the same reason (Studio's
     * `historyGuard`), and it cannot reach this one: the key never gets as far
     * as the WebView.
     *
     * Two paths, because Android has two. The key arrives at `onBackPressed()`
     * below, unless the ahead-of-time dispatcher of API 33+ is active — which it
     * is once an app opts into predictive back or targets a release where that
     * is the default. A callback registered there that does nothing is what
     * makes the second path go nowhere; where it is inactive it is never
     * invoked, which costs nothing. Neither path is the one to delete when this
     * shell's target moves.
     */
    private fun disableBackKey() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                OnBackInvokedCallback {
                    // Deliberately empty. Registered so the system has a
                    // handler for Back that is not "finish the app".
                },
            )
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // No super call: Activity's is the one that finishes the game.
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    /**
     * Hide the status and navigation bars, and let the content reach into the
     * display cutout — a visual novel is a full-bleed surface.
     */
    private fun enterImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * The decoder this build's payload needs. A key in the config means the
     * packer encoded the payload, and the library must then be present: falling
     * back to serving ciphertext would look like a corrupt game rather than a
     * broken build, so it fails here instead.
     */
    private fun decoderFor(config: ShellConfig): ContentDecoder {
        val key = config.contentKey ?: return IdentityContentDecoder
        check(NativeContentDecoder.isAvailable) {
            "this build carries an encoded payload but no decoder library"
        }
        return NativeContentDecoder(key)
    }

}
