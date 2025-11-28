package com.bybora.smartxtream

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TrailerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var btnClose: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trailer)

        webView = findViewById(R.id.webview_trailer)
        btnClose = findViewById(R.id.btn_close_trailer)

        btnClose.setOnClickListener { finish() }

        val trailerUrl = intent.getStringExtra("EXTRA_TRAILER_URL")?.trim() ?: ""

        if (trailerUrl.isNotEmpty()) {
            setupWebView(trailerUrl)
        } else {
            Toast.makeText(this, "Link boş", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupWebView(inputUrl: String) {
        val videoId = extractYoutubeId(inputUrl)

        if (videoId.isEmpty()) {
            Toast.makeText(this, "Video ID bulunamadı", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            // Kullanıcı dokunmadan oynatmayı zorla
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // --- SİHİRLİ KOD BURASI ---
        // 1. YouTube IFrame API'sini çağırıyoruz.
        // 2. 'onReady' olayında 'playVideo()' komutunu ateşliyoruz (Otomatik oynatmayı garanti eder).
        // 3. BaseURL olarak "https://www.youtube.com" veriyoruz (Hata Kodu 4'ü yok eder).

        val htmlData = """
            <!DOCTYPE html>
            <html>
              <body style="margin:0;padding:0;background:black;">
                <div id="player" style="height:100vh;width:100vw;"></div>
                <script>
                  var tag = document.createElement('script');
                  tag.src = "https://www.youtube.com/iframe_api";
                  var firstScriptTag = document.getElementsByTagName('script')[0];
                  firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                  var player;
                  function onYouTubeIframeAPIReady() {
                    player = new YT.Player('player', {
                      height: '100%',
                      width: '100%',
                      videoId: '$videoId',
                      playerVars: {
                        'playsinline': 1,
                        'autoplay': 1,
                        'rel': 0,
                        'controls': 1,
                        'fs': 1,
                        'modestbranding': 1
                      },
                      events: {
                        'onReady': onPlayerReady
                      }
                    });
                  }

                  function onPlayerReady(event) {
                    event.target.playVideo();
                  }
                </script>
              </body>
            </html>
        """.trimIndent()

        // YouTube domainiymiş gibi davranarak yüklüyoruz
        webView.loadDataWithBaseURL("https://www.youtube.com", htmlData, "text/html", "UTF-8", null)
    }

    private fun extractYoutubeId(url: String): String {
        var videoId = ""
        try {
            val pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*"
            val compiledPattern = java.util.regex.Pattern.compile(pattern)
            val matcher = compiledPattern.matcher(url)
            if (matcher.find()) {
                videoId = matcher.group()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (videoId.isEmpty() && url.length == 11 && !url.contains("/")) {
            videoId = url
        }

        return videoId
    }

    override fun onPause() {
        super.onPause()
        // Sayfa durunca videoyu durdur (Sesi keser)
        webView.evaluateJavascript("player.pauseVideo();", null)
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}