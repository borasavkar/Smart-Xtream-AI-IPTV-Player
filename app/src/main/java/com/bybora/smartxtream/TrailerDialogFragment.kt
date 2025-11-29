package com.bybora.smartxtream

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment

class TrailerDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_URL = "arg_url"

        fun newInstance(trailerUrl: String): TrailerDialogFragment {
            return TrailerDialogFragment().apply {
                arguments = bundleOf(ARG_URL to trailerUrl)
            }
        }
    }

    private var trailerUrl: String? = null
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trailerUrl = arguments?.getString(ARG_URL)
        // Tam ekran, şeffaf arka planlı dialog
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_trailer_player, container, false)

        webView = view.findViewById(R.id.web_trailer)
        val btnClose = view.findViewById<Button>(R.id.btn_trailer_close)

        // Google TV kumandası: ilk focus Kapat butonunda olsun
        btnClose.isFocusable = true
        btnClose.isFocusableInTouchMode = true
        btnClose.requestFocus()

        btnClose.setOnClickListener {
            dismiss()
        }

        setupWebView()
        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun setupWebView() {
        val url = trailerUrl ?: return
        val wv = webView ?: return

        val settings = wv.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Bazı Android TV cihazlarında WebView için bu önemli oluyor
        wv.webChromeClient = WebChromeClient()
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                return false
            }
        }

        // SADECE URL YÜKLE; HTML/iframe yok
        wv.loadUrl(url)
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.destroy()
        webView = null
    }
}
