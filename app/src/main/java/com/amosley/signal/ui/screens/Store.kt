package com.amosley.signal.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.amosley.signal.data.StoreDownloads
import com.amosley.signal.ui.Ctx
import com.amosley.signal.ui.components.Hairline
import com.amosley.signal.ui.components.Mono
import com.amosley.signal.ui.components.OutlineBtn
import com.amosley.signal.ui.components.Spinner
import com.amosley.signal.ui.theme.C
import com.amosley.signal.ui.theme.T

/**
 * The Qobuz store inside Signal. You sign in to your own Qobuz account and pay Qobuz on its own pages;
 * when a purchase is downloaded here, Signal saves it to Music/Signal and adds it to the library.
 */
/** The store page plus its download list; used by the Store tab and by "Find on Qobuz" links. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StoreBrowser(c: Ctx, url: String, modifier: Modifier, showBack: Boolean = false, onPage: (String) -> Unit = {}) {
    val app = c.app
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val jobs by app.store.jobs.collectAsState()
    BackHandler(enabled = canBack) { web?.goBack() }
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            web?.destroy()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cur -> if (cur.moveToFirst()) cur.getString(0) else null } ?: "download.zip"
        app.store.fromFile(uri, name)
    }

    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = if (showBack) 12.dp else 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showBack) {
                Box(Modifier.size(36.dp).clickable { c.st.back() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Fg)
                }
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Qobuz", style = T.ui(17.sp, 600), color = C.Fg)
                Mono("Pay on Qobuz · downloads go to your library", style = T.metaMono, color = C.Faint)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlineBtn("Add file") { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "audio/*")) }
                OutlineBtn("Chrome") {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web?.url ?: url))) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (loading) C.Amber else C.Hair))
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val u = request.url
                            if (u.scheme == "http" || u.scheme == "https") return false
                            // Other apps' links (mailto:, intent:, app links): hand them to Android.
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, u)) }
                            return true
                        }
                        override fun onPageStarted(view: WebView, u: String?, favicon: android.graphics.Bitmap?) { loading = true }
                        override fun onPageFinished(view: WebView, u: String?) {
                            loading = false
                            canBack = view.canGoBack()
                            u?.let(onPage)
                        }
                        override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) { canBack = view.canGoBack() }
                    }
                    setDownloadListener { dlUrl, userAgent, contentDisposition, mimeType, _ ->
                        app.store.fromWeb(dlUrl, userAgent, contentDisposition, mimeType, CookieManager.getInstance().getCookie(dlUrl), this.url)
                    }
                    loadUrl(url)
                    web = this
                }
            },
        )
        if (jobs.isNotEmpty()) {
            Hairline()
            Column(Modifier.fillMaxWidth().background(C.Card).padding(horizontal = 16.dp, vertical = 6.dp)) {
                jobs.takeLast(3).forEach { j -> StoreJobRow(j) { app.store.dismiss(j.id) } }
            }
        }
    }
}

@Composable
private fun StoreJobRow(j: StoreDownloads.Job, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!j.done) Spinner(Modifier.size(14.dp)) else Text(if (j.error == null) "✓" else "!", style = T.ui(14.sp, 600), color = if (j.error == null) C.Green else C.AmberText)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(j.name, style = T.ui(13.5.sp, 500), color = C.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val mb = { b: Long -> "%.0f MB".format(b / 1_048_576.0) }
            val status = when {
                j.error != null -> "Failed · ${j.error}"
                j.done -> "Added ${j.files} song${if (j.files != 1) "s" else ""} to your library"
                else -> listOfNotNull(
                    if (j.total > 0) "${mb(j.bytes)} of ${mb(j.total)}" else mb(j.bytes),
                    if (j.files > 0) "${j.files} songs saved" else null,
                ).joinToString(" · ")
            }
            Mono(status, style = T.metaMono, color = C.Faint)
        }
        if (j.done) Mono("Dismiss", color = C.AmberText, style = T.metaMono, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
    }
}
