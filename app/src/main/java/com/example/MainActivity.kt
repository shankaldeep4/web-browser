package com.example

import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adblock.AdBlocker
import com.example.data.Bookmark
import com.example.data.DownloadItem
import com.example.data.HistoryItem
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BrowserViewModel
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    // Full-screen video states
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val isFullscreen by viewModel.isFullscreenActive.collectAsStateWithLifecycle()

                // Lock screen orientation to landscape on active fullscreen video playing
                LaunchedEffect(isFullscreen) {
                    requestedOrientation = if (isFullscreen) {
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    toggleSystemBars(isFullscreen)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrowserScreen(viewModel, customView, customViewCallback,
                        onShowCustomView = { view, callback ->
                            customView = view
                            customViewCallback = callback
                            viewModel.setFullscreen(true)
                        },
                        onHideCustomView = {
                            customView = null
                            customViewCallback = null
                            viewModel.setFullscreen(false)
                        })
                }
            }
        }
    }

    private fun toggleSystemBars(hide: Boolean) {
        val window = this.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    customView: View?,
    customViewCallback: WebChromeClient.CustomViewCallback?,
    onShowCustomView: (View, WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomView: () -> Unit
) {
    val context = LocalContext.current
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val currentTitle by viewModel.currentTitle.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.loadingProgress.collectAsStateWithLifecycle()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedAdsCount.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isFullscreen by viewModel.isFullscreenActive.collectAsStateWithLifecycle()

    var addressInput by remember { mutableStateOf("") }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var activeBackSupport by remember { mutableStateOf(false) }
    var activeForwardSupport by remember { mutableStateOf(false) }

    // Sliding drawer tabs: "bookmarks", "history", "downloads", or none ("")
    var activeDrawerState by remember { mutableStateOf("") }

    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()

    val keyboardController = LocalSoftwareKeyboardController.current

    // Synchronize Address Bar text with actual webView updates
    LaunchedEffect(currentUrl) {
        addressInput = currentUrl
        if (currentUrl.isEmpty()) {
            webViewInstance?.loadUrl("about:blank")
            viewModel.setTitle("AdBlock Web Browser")
        }
    }

    // Capture Back Gestures inside WebView context first before collapsing
    BackHandler(enabled = currentUrl.isNotEmpty() || activeDrawerState.isNotEmpty()) {
        if (activeDrawerState.isNotEmpty()) {
            activeDrawerState = ""
        } else if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            // Revert back directly to Cosmic home dashboard
            viewModel.setUrl("")
        }
    }

    // COSMIC DARK GRADIENT THEME (Atmosphere: Linear paint)
    val cosmicSlateGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F111A), // Deep interstellar dark
            Color(0xFF07080D)  // Absolute void pitch dark
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cosmicSlateGradient)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // SECTION 1: SEARCH HEADER BAR (Only visible if not playing fullscreen video)
            if (!isFullscreen) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF141724),
                    tonalElevation = 8.dp,
                    shadowElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Back home button
                            if (currentUrl.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setUrl("") }) {
                                    Icon(
                                        Icons.Default.Home,
                                        contentDescription = "Home",
                                        tint = Color.White
                                    )
                                }
                            }

                            // Dynamic styled AdBlock Shield icon
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (adBlockEnabled) Color(0xFF00F5D4).copy(alpha = 0.15f)
                                        else Color.DarkGray
                                    )
                                    .clickable { viewModel.toggleAdBlock() }
                            ) {
                                Icon(
                                    imageVector = if (adBlockEnabled) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                    contentDescription = "Shield Protection Toggle",
                                    tint = if (adBlockEnabled) Color(0xFF00F5D4) else Color.LightGray,
                                    modifier = Modifier.size(22.dp)
                                )
                                // Real-time shield blocked ad-count badge
                                if (adBlockEnabled && blockedCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-2).dp)
                                            .background(Color.Red, CircleShape)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = blockedCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // URL Address Search Field (Styled modern search bar)
                            TextField(
                                value = addressInput,
                                onValueChange = { addressInput = it },
                                placeholder = {
                                    Text(
                                        "Enter website URL / Search Google...",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF1F2336),
                                    unfocusedContainerColor = Color(0xFF1C1F30),
                                    disabledContainerColor = Color(0xFF1C1F30),
                                    focusedIndicatorColor = Color(0xFF8A5CF6),
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF00F5D4)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (addressInput.isNotEmpty()) {
                                        IconButton(onClick = {
                                            addressInput = ""
                                            if (currentUrl.isNotEmpty()) viewModel.setUrl("")
                                        }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        viewModel.triggerSearchOrLoad(addressInput)
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            )
                        }

                        // Smooth page progress indicator
                        if (isLoading && currentUrl.isNotEmpty()) {
                            LinearProgressIndicator(
                                progress = { loadingProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .height(3.dp),
                                color = Color(0xFF00F5D4),
                                trackColor = Color(0xFF1F2333)
                            )
                        }
                    }
                }
            }

            // SECTION 2: BROWSER AREA
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (currentUrl.isEmpty()) {
                    // LANDING SPEED DIAL PAGE (Home View Dashboard)
                    HomeDashboardView(
                        blockedCount = blockedCount,
                        adBlockEnabled = adBlockEnabled,
                        onSpeedDialSelected = { dialUrl ->
                            viewModel.triggerSearchOrLoad(dialUrl)
                        },
                        onToggleShield = { viewModel.toggleAdBlock() },
                        bookmarksCount = bookmarks.size,
                        historyCount = history.size,
                        downloadsCount = downloads.size,
                        onDrawerRequested = { activeDrawerState = it }
                    )
                } else {
                    // REAL WEBVIEW INTERFACE
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewInstance = this

                                // Optimize loading speed settings
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    mediaPlaybackRequiresUserGesture = false
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    useWideViewPort = true
                                    loadWithOverviewMode = true

                                    // Remove WebView signatures so YouTube doesn't force block or show Sign In popups
                                    try {
                                        val defaultUA = WebSettings.getDefaultUserAgent(ctx)
                                        userAgentString = defaultUA
                                            .replace("Version/4.0 ", "")
                                            .replace("; wv", "")
                                            .replace("wv", "")
                                    } catch (e: Exception) {
                                        userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                                    }
                                }

                                // Interactive actions & downloads handler
                                setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                                    viewModel.handleDownload(url, userAgent, contentDisposition, mimetype)
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        // Allow normal link clicking navigation within WebView
                                        return false
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        super.onPageStarted(view, url, favicon)
                                        viewModel.setLoading(true)
                                        url?.let {
                                            if (it != "about:blank" && it.isNotEmpty()) {
                                                addressInput = it
                                            }
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        viewModel.setLoading(false)
                                        if (url != null && url != "about:blank" && url.isNotEmpty()) {
                                            view?.title?.let { viewModel.setTitle(it) }
                                            // Hide lingering advertisement overlays via custom script integration
                                            if (adBlockEnabled) {
                                                view?.evaluateJavascript(AdBlocker.getAdHideScript(), null)
                                            }
                                            if (url.contains("youtube.com")) {
                                                view?.evaluateJavascript(AdBlocker.getYoutubeCleanScript(), null)
                                            }
                                        }
                                        activeBackSupport = view?.canGoBack() ?: false
                                        activeForwardSupport = view?.canGoForward() ?: false
                                    }

                                    override fun doUpdateVisitedHistory(
                                        view: WebView?,
                                        url: String?,
                                        isReload: Boolean
                                    ) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        url?.let {
                                            if (it != "about:blank" && it.isNotEmpty()) {
                                                viewModel.setUrl(it)
                                            }
                                        }
                                        activeBackSupport = view?.canGoBack() ?: false
                                        activeForwardSupport = view?.canGoForward() ?: false
                                    }

                                    // Intercept and purge ads requested on standard protocol
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): WebResourceResponse? {
                                        if (request == null) return null
                                        val reqUrl = request.url?.toString() ?: ""

                                        if (adBlockEnabled && AdBlocker.isAd(reqUrl)) {
                                            viewModel.incrementBlockedAds()
                                            return WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                ByteArrayInputStream("".toByteArray())
                                            )
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    // Handle web video element requests for hardware accelerated full screen presentation
                                    override fun onShowCustomView(
                                        view: View,
                                        callback: CustomViewCallback
                                    ) {
                                        onShowCustomView(view, callback)
                                    }

                                    override fun onHideCustomView() {
                                        onHideCustomView()
                                    }

                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        viewModel.setProgress(newProgress)
                                        if (newProgress > 60) {
                                            val url = view?.url ?: ""
                                            if (url.contains("youtube.com")) {
                                                view?.evaluateJavascript(AdBlocker.getYoutubeCleanScript(), null)
                                            }
                                        }
                                        if (newProgress == 100) {
                                            viewModel.setLoading(false)
                                        }
                                    }
                                }

                                loadUrl(currentUrl)
                            }
                        },
                        update = { view ->
                            // Update WebView target URL if changed globally
                            if (view.url != currentUrl && currentUrl.isNotEmpty()) {
                                view.loadUrl(currentUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // SECTION 3: NAVIGATION BOTTOM CONTROLS (Only visible if not playing fullscreen video)
            if (!isFullscreen) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF141724),
                    tonalElevation = 8.dp
                ) {
                    Column {
                        HorizontalDivider(color = Color(0xFF22283A))
                        Row(
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            // Back Navigation arrow
                            IconButton(
                                onClick = { webViewInstance?.goBack() },
                                enabled = currentUrl.isNotEmpty() && activeBackSupport
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (currentUrl.isNotEmpty() && activeBackSupport) Color.White else Color.Gray
                                )
                            }

                            // Forward Navigation arrow
                            IconButton(
                                onClick = { webViewInstance?.goForward() },
                                enabled = currentUrl.isNotEmpty() && activeForwardSupport
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (currentUrl.isNotEmpty() && activeForwardSupport) Color.White else Color.Gray
                                )
                            }

                            // Reload/Stop Button
                            IconButton(onClick = {
                                if (currentUrl.isNotEmpty()) {
                                    if (isLoading) webViewInstance?.stopLoading() else webViewInstance?.reload()
                                }
                            }) {
                                Icon(
                                    imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                    contentDescription = if (isLoading) "Stop Loading" else "Refresh",
                                    tint = if (currentUrl.isNotEmpty()) Color.White else Color.Gray
                                )
                            }

                            // Bookmark toggle for current URL
                            IconButton(
                                onClick = { viewModel.toggleBookmark() },
                                enabled = currentUrl.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Bookmark Page",
                                    tint = if (currentUrl.isEmpty()) Color.Gray else if (isBookmarked) Color(0xFFFFB703) else Color.White
                                )
                            }

                            // Drawer Trigger Button to show Saved URLs/Settings
                            IconButton(onClick = {
                                activeDrawerState = if (activeDrawerState.isNotEmpty()) "" else "bookmarks"
                            }) {
                                Icon(
                                    Icons.Default.Bookmarks,
                                    contentDescription = "Bookmarks and History List",
                                    tint = if (activeDrawerState.isNotEmpty()) Color(0xFF00F5D4) else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // BOTTOM DRAWER UTILITY DIALOGUE (bookmarks, history, downloads)
        AnimatedVisibility(
            visible = activeDrawerState.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .align(Alignment.BottomCenter)
                .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            DrawerUtilitySheet(
                activeState = activeDrawerState,
                history = history,
                bookmarks = bookmarks,
                downloads = downloads,
                onClose = { activeDrawerState = "" },
                onSelectUrl = { target ->
                    activeDrawerState = ""
                    viewModel.triggerSearchOrLoad(target)
                },
                onDeleteBookmark = { viewModel.deleteBookmark(it) },
                onDeleteHistory = { viewModel.deleteHistoryItem(it) },
                onClearHistory = { viewModel.clearAllHistory() }
            )
        }

        // FULLSCREEN HARDWARE VIDEO LAYER INTERACTIVE PORTAL
        if (isFullscreen && customView != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { customView },
                    modifier = Modifier.fillMaxSize()
                )

                // High visual safety exit button in case html fullscreen callbacks fail
                IconButton(
                    onClick = {
                        onHideCustomView()
                    },
                    modifier = Modifier
                        .padding(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun HomeDashboardView(
    blockedCount: Int,
    adBlockEnabled: Boolean,
    onSpeedDialSelected: (String) -> Unit,
    onToggleShield: () -> Unit,
    bookmarksCount: Int,
    historyCount: Int,
    downloadsCount: Int,
    onDrawerRequested: (String) -> Unit
) {
    val speedDials = listOf(
        SpeedDialItem("Google", "https://www.google.com", Icons.Default.Search, Color(0xFF4285F4)),
        SpeedDialItem("YouTube", "https://m.youtube.com", Icons.Default.SmartDisplay, Color(0xFFFF0000)),
        SpeedDialItem("Wikipedia", "https://en.m.wikipedia.org", Icons.Default.Info, Color(0xFF7A7F85)),
        SpeedDialItem("Facebook", "https://m.facebook.com", Icons.Default.Public, Color(0xFF1877F2)),
        SpeedDialItem("Instagram", "https://www.instagram.com", Icons.Default.CameraAlt, Color(0xFFE1306C)),
        SpeedDialItem("Reddit", "https://www.reddit.com", Icons.Default.Forum, Color(0xFFFF4500)),
        SpeedDialItem("GitHub", "https://github.com", Icons.Default.Code, Color(0xFF6E5494)),
        SpeedDialItem("Fast.com", "https://fast.com", Icons.Default.Speed, Color(0xFF00F5D4))
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))

            // BIG AD-BLOCK SHIELD BADGE (Aesthetic Atmospheric Glow Center)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(170.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (adBlockEnabled) listOf(
                                Color(0xFF00F5D4).copy(alpha = 0.25f),
                                Color.Transparent
                            ) else listOf(Color.Red.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
                    .clickable { onToggleShield() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (adBlockEnabled) Icons.Default.Shield else Icons.Default.ShieldMoon,
                        contentDescription = "Shield State icon",
                        tint = if (adBlockEnabled) Color(0xFF00F5D4) else Color(0xFFFF5454),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (adBlockEnabled) "$blockedCount Blocked" else "Shield Offline",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Touch to Toggle",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            // SHIELD TOGGLE CARD
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141724), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = if (adBlockEnabled) Color(0xFF00F5D4) else Color.Gray,
                    )
                    Column {
                        Text(
                            "AdBlock Shield",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            if (adBlockEnabled) "Ad-Shield active for fast, secure web." else "Ads are currently allowed.",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
                Switch(
                    checked = adBlockEnabled,
                    onCheckedChange = { onToggleShield() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF141724),
                        checkedTrackColor = Color(0xFF00F5D4),
                        uncheckedTrackColor = Color.LightGray.copy(alpha = 0.1f)
                    )
                )
            }
        }

        item {
            // Speed Dials Section Title
            Text(
                "Popular Sites",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                textAlign = TextAlign.Start
            )
        }

        // SPEED DIALS GRID
        item {
            Box(modifier = Modifier.height(200.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(speedDials) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSpeedDialSelected(item.url) }
                                .padding(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF1F2336), CircleShape)
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.name,
                                    tint = item.tintColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.name,
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // UTILITY SELECTOR CHIPS
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UtilityChip(
                    label = "Bookmarks ($bookmarksCount)",
                    icon = Icons.Default.Folder,
                    onClick = { onDrawerRequested("bookmarks") }
                )
                UtilityChip(
                    label = "Recent History ($historyCount)",
                    icon = Icons.Default.History,
                    onClick = { onDrawerRequested("history") }
                )
                UtilityChip(
                    label = "Downloads ($downloadsCount)",
                    icon = Icons.Default.Download,
                    onClick = { onDrawerRequested("downloads") }
                )
            }
        }
    }
}

@Composable
fun DrawerUtilitySheet(
    activeState: String,
    history: List<HistoryItem>,
    bookmarks: List<Bookmark>,
    downloads: List<DownloadItem>,
    onClose: () -> Unit,
    onSelectUrl: (String) -> Unit,
    onDeleteBookmark: (Int) -> Unit,
    onDeleteHistory: (Int) -> Unit,
    onClearHistory: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(activeState) }

    LaunchedEffect(activeState) {
        if (activeState.isNotEmpty()) {
            selectedTab = activeState
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141724))
    ) {
        // Sheet Drag Handle + Close Button Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = when (selectedTab) {
                    "bookmarks" -> "Saved Bookmarks"
                    "history" -> "Broswer History"
                    else -> "Saved Downloads"
                },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedTab == "history" && history.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) {
                        Text("Clear All", color = Color(0xFFFF5454), fontSize = 13.sp)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close Panel", tint = Color.LightGray)
                }
            }
        }

        // Segmented Tabs Header
        TabRow(
            selectedTabIndex = when (selectedTab) {
                "bookmarks" -> 0
                "history" -> 1
                else -> 2
            },
            containerColor = Color(0xFF1D2132),
            contentColor = Color(0xFF00F5D4),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        tabPositions[when (selectedTab) {
                            "bookmarks" -> 0
                            "history" -> 1
                            else -> 2
                        }]
                    ),
                    color = Color(0xFF00F5D4)
                )
            }
        ) {
            Tab(
                selected = selectedTab == "bookmarks",
                onClick = { selectedTab = "bookmarks" },
                text = { Text("Bookmarks", fontSize = 14.sp) }
            )
            Tab(
                selected = selectedTab == "history",
                onClick = { selectedTab = "history" },
                text = { Text("History", fontSize = 14.sp) }
            )
            Tab(
                selected = selectedTab == "downloads",
                onClick = { selectedTab = "downloads" },
                text = { Text("Downloads", fontSize = 14.sp) }
            )
        }

        // Tabs Content Body
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                "bookmarks" -> {
                    if (bookmarks.isEmpty()) {
                        EmptyStatePrompt(
                            "No Bookmarks Yet",
                            "While scanning web, click star button on toolbar to save bookmarks here.",
                            Icons.Default.StarOutline
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(bookmarks) { bookmark ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            bookmark.title,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            bookmark.url,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Bookmark",
                                                tint = Color.Gray
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectUrl(bookmark.url) }
                                )
                                HorizontalDivider(color = Color(0xFF1E2333))
                            }
                        }
                    }
                }

                "history" -> {
                    if (history.isEmpty()) {
                        EmptyStatePrompt(
                            "History Clear",
                            "Any websites you browse globally will show up here for fast access.",
                            Icons.Default.HistoryToggleOff
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(history) { item ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            item.title,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            item.url,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { onDeleteHistory(item.id) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.Gray
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectUrl(item.url) }
                                )
                                HorizontalDivider(color = Color(0xFF1E2333))
                            }
                        }
                    }
                }

                "downloads" -> {
                    if (downloads.isEmpty()) {
                        EmptyStatePrompt(
                            "Downloads Empty",
                            "Videos or files you trigger downloading on sites will download natively and show up here.",
                            Icons.Default.CloudDownload
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(downloads) { dItem ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            dItem.fileName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Column {
                                            Text(
                                                dItem.url,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Saved: ${dItem.sizeLabel}",
                                                color = Color(0xFF00F5D4),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Success Status",
                                            tint = Color(0xFF00F5D4)
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                HorizontalDivider(color = Color(0xFF1E2333))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStatePrompt(title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = desc,
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun UtilityChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(Color(0xFF1F2336), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

data class SpeedDialItem(
    val name: String,
    val url: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tintColor: Color
)
