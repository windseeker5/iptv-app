package com.kdresdell.iptvtv.phone

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kdresdell.iptvtv.phone.theme.PhoneTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// v0.2: credentials -> local catalog sync (so search works offline/instantly
// like the TV app) -> My TV / My Library tabs, Search, What's New, All and
// Settings in the side menu (opened from the logo), with a
// full-screen player and episode picker layered on top. The catalog only
// auto-syncs once (first launch); live sports/PPV events get added to the
// provider's list throughout the day, so a manual "Refresh" button forces a
// re-sync on demand rather than waiting for a stale local snapshot.
// Still no recording - see the plan for what stays deferred.
sealed class PhoneScreen {
    data object Home : PhoneScreen()
    data class Player(val item: NowPlaying) : PhoneScreen()
    data class Episodes(val series: SeriesShow) : PhoneScreen()
}

// AppCompatActivity (not plain ComponentActivity): the Cast device picker
// needs a FragmentActivity with an AppCompat theme.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneTheme {
                // Surface (not a bare Box) so text/icons default to the
                // theme's light content color instead of black. It sits
                // outside systemBarsPadding so the area behind the
                // status/nav bars is dark too.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        App()
                    }
                }
            }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val providerPrefs = remember { ProviderPrefs(context) }
    var credentials by remember { mutableStateOf(providerPrefs.load()) }

    if (!credentials.isComplete) {
        CredentialsForm(
            initial = credentials,
            onSaved = { entered ->
                providerPrefs.save(entered)
                credentials = entered
            }
        )
        return
    }

    val api = remember(credentials) { XtreamApi(credentials) }
    val db = remember { CatalogDatabase(context) }
    val syncPrefs = remember { SyncPrefs(context) }
    // catalogReady: there's a local list to show (so a refresh can run on
    // top of the app instead of replacing it). syncProgress/syncError drive
    // the SyncScreen; catalogVersion bumps after each successful sync so
    // the KPI counts re-read.
    var catalogReady by remember(credentials) { mutableStateOf(!db.isEmpty()) }
    var syncProgress by remember { mutableStateOf<SyncProgress?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }
    var catalogVersion by remember { mutableIntStateOf(0) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var lastRefreshMillis by remember { mutableStateOf(syncPrefs.catalogSyncedAt()) }
    var stats by remember { mutableStateOf(CatalogStats()) }
    val ratingsDownloader = remember { RatingsDownloader(db) }
    val downloadRatings: suspend () -> Boolean = {
        try {
            ratingsDownloader.download()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    LaunchedEffect(credentials, refreshTrigger) {
        if (refreshTrigger == 0 && catalogReady) return@LaunchedEffect
        syncError = null
        val total = 6
        try {
            // Each list streams straight into the DB as it downloads.
            syncProgress = SyncProgress(1, total, "Downloading live channels...")
            api.streamLiveStreams { db.replaceLiveChannels(it) }
            syncProgress = SyncProgress(2, total, "Downloading movies...")
            api.streamVodStreams { db.replaceVodStreams(it) }
            syncProgress = SyncProgress(3, total, "Downloading TV shows...")
            api.streamSeriesList { db.replaceSeries(it) }
            syncProgress = SyncProgress(4, total, "Downloading categories...")
            val liveCategories = api.getLiveCategories()
            val vodCategories = api.getVodCategories()
            val seriesCategories = api.getSeriesCategories()
            syncProgress = SyncProgress(5, total, "Saving to your phone...")
            withContext(Dispatchers.IO) {
                db.replaceCategories(CatalogKind.LIVE, liveCategories)
                db.replaceCategories(CatalogKind.VOD, vodCategories)
                db.replaceCategories(CatalogKind.SERIES, seriesCategories)
            }
            syncPrefs.markCatalogSynced()
            lastRefreshMillis = syncPrefs.catalogSyncedAt()
            // Best-effort: What's New just keeps its old scores if the KD
            // server can't be reached.
            syncProgress = SyncProgress(6, total, "Downloading IMDb scores...")
            if (downloadRatings()) syncPrefs.markRatingsSynced()
            catalogReady = true
            catalogVersion++
        } catch (e: XtreamApiException) {
            syncError = e.message ?: "Failed to download the list"
        } finally {
            syncProgress = null
        }
    }

    // The server rebuilds the IMDb scores nightly - fetch them in the
    // background once a day (or if a DB upgrade dropped them).
    LaunchedEffect(catalogReady) {
        if (!catalogReady) return@LaunchedEffect
        val missing = withContext(Dispatchers.IO) { !db.hasImdbRatings() }
        if ((missing || syncPrefs.isRatingsStale()) && downloadRatings()) {
            syncPrefs.markRatingsSynced()
            catalogVersion++
        }
    }

    // Changing the login in Settings re-downloads the whole list from the
    // new provider (the refresh overlay shows over the app meanwhile).
    val saveCredentials: (ProviderCredentials) -> Unit = { entered ->
        providerPrefs.save(entered)
        credentials = entered
        refreshTrigger++
    }

    // Counts + account dates for the Search KPI tiles, re-read after every
    // sync. Account info is a separate, never-failing call.
    LaunchedEffect(credentials, catalogReady, catalogVersion) {
        if (!catalogReady) return@LaunchedEffect
        val counts = withContext(Dispatchers.IO) {
            Triple(db.countLiveChannels(), db.countSeries(), db.countVodStreams())
        }
        stats = stats.copy(liveCount = counts.first, seriesCount = counts.second, vodCount = counts.third)
        stats = stats.copy(account = api.getAccountInfo())
    }

    // Self-update: checked once per launch, and again from Settings.
    val scope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker() }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val checkForUpdate: () -> Unit = {
        scope.launch {
            updateState = UpdateState.Checking
            updateState = try {
                val info = updateChecker.checkForUpdate()
                if (info != null && info.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: "Could not reach the update server")
            }
        }
    }
    val installUpdate: (UpdateInfo) -> Unit = { info ->
        scope.launch {
            updateState = UpdateState.Downloading(info)
            try {
                val apk = updateChecker.downloadApk(context, info)
                updateChecker.installApk(context, apk)
                updateState = UpdateState.Available(info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateState = UpdateState.Failed(e.message ?: "Download failed")
            }
        }
    }
    LaunchedEffect(Unit) { checkForUpdate() }

    val syncOverlay: @Composable (canClose: Boolean) -> Unit = { canClose ->
        if (syncProgress != null || syncError != null) {
            SyncScreen(
                progress = syncProgress,
                error = syncError,
                onRetry = { refreshTrigger++ },
                onClose = if (canClose) ({ syncError = null }) else null
            )
        }
    }

    if (!catalogReady) {
        // First launch: nothing to show underneath yet. Before the effect's
        // first frame both states are null, so fall back to the spinner.
        if (syncProgress == null && syncError == null) {
            SyncScreen(progress = null, error = null, onRetry = {}, onClose = null)
        } else {
            syncOverlay(false)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val favoritesStore = remember { FavoritesStore(context) }
            val vodFavoritesStore = remember { VodFavoritesStore(context) }
            var liveFavorites by remember { mutableStateOf(favoritesStore.load()) }
            var movieFavorites by remember { mutableStateOf(vodFavoritesStore.loadMovies()) }
            var seriesFavorites by remember { mutableStateOf(vodFavoritesStore.loadSeries()) }
            var screen by remember { mutableStateOf<PhoneScreen>(PhoneScreen.Home) }
            var guideStatus by remember { mutableStateOf<GuideStatus>(GuideStatus.Idle) }
            var guideVersion by remember { mutableIntStateOf(0) }

            // "What's on now" for My TV. Fetched here rather than in
            // MyTvScreen so leaving the tab doesn't cancel the download.
            // Only runs when the cache is old or a new channel was saved.
            LaunchedEffect(liveFavorites) {
                val wanted = withContext(Dispatchers.IO) {
                    db.epgChannelIds(liveFavorites.map { it.streamId }).values.toSet()
                }
                if (wanted.isEmpty() || !syncPrefs.isEpgStale(wanted)) return@LaunchedEffect
                guideStatus = GuideStatus.Loading
                guideStatus = try {
                    val programs = api.fetchXmltvPrograms(wanted)
                    withContext(Dispatchers.IO) { db.replaceEpgPrograms(programs) }
                    syncPrefs.markEpgSynced(wanted)
                    guideVersion++
                    GuideStatus.Idle
                } catch (e: XtreamApiException) {
                    GuideStatus.Failed(e.message ?: "Could not load guide")
                }
            }

            BackHandler(enabled = screen != PhoneScreen.Home) { screen = PhoneScreen.Home }
            // Keeps HomeTabs' saveable state (current section, the All
            // screen's category, scroll positions) while the player or
            // episode picker is on screen, so Back returns to where you were.
            val homeState = rememberSaveableStateHolder()

            when (val current = screen) {
                is PhoneScreen.Player -> PlayerScreen(item = current.item, db = db, api = api, onBack = { screen = PhoneScreen.Home })
                is PhoneScreen.Episodes -> EpisodePickerScreen(
                    series = current.series,
                    api = api,
                    onPlay = { screen = PhoneScreen.Player(it) },
                    onBack = { screen = PhoneScreen.Home }
                )
                is PhoneScreen.Home -> homeState.SaveableStateProvider("home") { HomeTabs(
                    db = db,
                    api = api,
                    credentials = credentials,
                    onSaveCredentials = saveCredentials,
                    catalogVersion = catalogVersion,
                    stats = stats,
                    lastRefreshMillis = lastRefreshMillis,
                    guideStatus = guideStatus,
                    guideVersion = guideVersion,
                    updateState = updateState,
                    onCheckForUpdate = checkForUpdate,
                    onInstallUpdate = installUpdate,
                    liveFavorites = liveFavorites,
                    movieFavorites = movieFavorites,
                    seriesFavorites = seriesFavorites,
                    onToggleLiveFavorite = { liveFavorites = favoritesStore.toggle(it, liveFavorites) },
                    onToggleMovieFavorite = { movieFavorites = vodFavoritesStore.toggleMovie(it, movieFavorites) },
                    onToggleSeriesFavorite = { seriesFavorites = vodFavoritesStore.toggleSeries(it, seriesFavorites) },
                    onPlay = { screen = PhoneScreen.Player(it) },
                    onOpenSeries = { screen = PhoneScreen.Episodes(it) },
                    onRefresh = { refreshTrigger++ }
                ) }
            }
            syncOverlay(true)
        }
    }
}

// Top-level sections. Only My TV / My Library are tabs; Search is also on
// the search icon; everything is in the side menu, which the logo opens.
private enum class Section { SEARCH, MY_TV, MY_LIBRARY, WHATS_NEW, ALL, SETTINGS }

@Composable
private fun HomeTabs(
    db: CatalogDatabase,
    api: XtreamApi,
    credentials: ProviderCredentials,
    onSaveCredentials: (ProviderCredentials) -> Unit,
    catalogVersion: Int,
    stats: CatalogStats,
    lastRefreshMillis: Long?,
    guideStatus: GuideStatus,
    guideVersion: Int,
    updateState: UpdateState,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: (UpdateInfo) -> Unit,
    liveFavorites: List<LiveChannel>,
    movieFavorites: List<VodStream>,
    seriesFavorites: List<SeriesShow>,
    onToggleLiveFavorite: (LiveChannel) -> Unit,
    onToggleMovieFavorite: (VodStream) -> Unit,
    onToggleSeriesFavorite: (SeriesShow) -> Unit,
    onPlay: (NowPlaying) -> Unit,
    onOpenSeries: (SeriesShow) -> Unit,
    onRefresh: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(Section.MY_TV) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val refreshFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val lastRefreshLabel = lastRefreshMillis?.let { refreshFormat.format(Date(it)) } ?: "unknown"

    // Back closes the menu first, then returns other sections to My TV,
    // before leaving the app.
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(enabled = !drawerState.isOpen && section != Section.MY_TV) { section = Section.MY_TV }

    val goTo: (Section) -> Unit = { target ->
        section = target
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppLogo(size = 48.dp)
                    Column {
                        Text("KDTV", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Version ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                DrawerItem("Search", Icons.Filled.Search, section == Section.SEARCH) { goTo(Section.SEARCH) }
                DrawerItem("My TV", Icons.Filled.Home, section == Section.MY_TV) { goTo(Section.MY_TV) }
                DrawerItem("My Library", Icons.Filled.Star, section == Section.MY_LIBRARY) { goTo(Section.MY_LIBRARY) }
                DrawerItem("What's New", Icons.Filled.ThumbUp, section == Section.WHATS_NEW) { goTo(Section.WHATS_NEW) }
                DrawerItem("All", Icons.AutoMirrored.Filled.List, section == Section.ALL) { goTo(Section.ALL) }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                DrawerItem(
                    "Settings",
                    Icons.Filled.Settings,
                    section == Section.SETTINGS,
                    badge = if (updateState is UpdateState.Available) "Update" else null
                ) { goTo(Section.SETTINGS) }
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // The logo is the menu button (no hamburger icon), on the left,
            // vertically centred with Search on the right.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "Open menu") { scope.launch { drawerState.open() } }
                        .padding(4.dp)
                ) {
                    AppLogo(size = 62.dp)
                }
                IconButton(onClick = { section = Section.SEARCH }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = if (section == Section.SEARCH) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
            }
            // Launch-time check found a newer build - point to Settings
            // rather than installing unasked.
            if (updateState is UpdateState.Available && section != Section.SETTINGS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Version ${updateState.info.versionName} is available",
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { section = Section.SETTINGS }) {
                        Text("Update", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // The indicator only shows while one of the two tabs is the
            // current section - elsewhere neither tab is "selected".
            val tabIndex = when (section) {
                Section.MY_TV -> 0
                Section.MY_LIBRARY -> 1
                else -> -1
            }
            TabRow(
                selectedTabIndex = tabIndex.coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { positions ->
                    if (tabIndex >= 0) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(positions[tabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                SectionTab("My TV", tabIndex == 0) { section = Section.MY_TV }
                SectionTab("My Library", tabIndex == 1) { section = Section.MY_LIBRARY }
            }
            when (section) {
                Section.SEARCH -> SearchScreen(
                    db = db,
                    api = api,
                    liveFavorites = liveFavorites,
                    vodFavorites = movieFavorites,
                    seriesFavorites = seriesFavorites,
                    onToggleLiveFavorite = onToggleLiveFavorite,
                    onToggleMovieFavorite = onToggleMovieFavorite,
                    onToggleSeriesFavorite = onToggleSeriesFavorite,
                    stats = stats,
                    lastRefreshLabel = lastRefreshLabel,
                    onRefresh = onRefresh,
                    onPlay = onPlay,
                    onOpenSeries = onOpenSeries
                )
                Section.MY_TV -> MyTvScreen(
                    favorites = liveFavorites,
                    db = db,
                    api = api,
                    guideStatus = guideStatus,
                    guideVersion = guideVersion,
                    onPlay = onPlay,
                    onRemove = onToggleLiveFavorite
                )
                Section.MY_LIBRARY -> MyLibraryScreen(
                    movies = movieFavorites,
                    series = seriesFavorites,
                    db = db,
                    api = api,
                    onPlay = onPlay,
                    onOpenSeries = onOpenSeries,
                    onRemoveMovie = onToggleMovieFavorite,
                    onRemoveSeries = onToggleSeriesFavorite
                )
                Section.WHATS_NEW -> {
                    var whatsNew by remember { mutableStateOf<LoadState<WhatsNewContent>>(LoadState.Loading) }
                    LaunchedEffect(catalogVersion) {
                        whatsNew = LoadState.Success(withContext(Dispatchers.IO) { WhatsNewContent.load(db) })
                    }
                    WhatsNewScreen(
                        state = whatsNew,
                        api = api,
                        movieFavorites = movieFavorites,
                        seriesFavorites = seriesFavorites,
                        onToggleMovieFavorite = onToggleMovieFavorite,
                        onToggleSeriesFavorite = onToggleSeriesFavorite,
                        onPlay = onPlay,
                        onOpenSeries = onOpenSeries
                    )
                }
                Section.ALL -> AllScreen(
                    db = db,
                    api = api,
                    catalogVersion = catalogVersion,
                    liveFavorites = liveFavorites,
                    movieFavorites = movieFavorites,
                    seriesFavorites = seriesFavorites,
                    onToggleLiveFavorite = onToggleLiveFavorite,
                    onToggleMovieFavorite = onToggleMovieFavorite,
                    onToggleSeriesFavorite = onToggleSeriesFavorite,
                    onPlay = onPlay,
                    onOpenSeries = onOpenSeries
                )
                Section.SETTINGS -> SettingsScreen(
                    credentials = credentials,
                    onSaveCredentials = onSaveCredentials,
                    stats = stats,
                    lastRefreshLabel = lastRefreshLabel,
                    onRefresh = onRefresh,
                    updateState = updateState,
                    onCheckForUpdate = onCheckForUpdate,
                    onInstallUpdate = onInstallUpdate
                )
            }
        }
    }
}

@Composable
private fun SectionTab(title: String, selected: Boolean, onClick: () -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        text = { Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }
    )
}

@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, contentDescription = null) },
        badge = badge?.let {
            {
                Text(
                    it,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
            selectedIconColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}

// The launcher icon artwork (face + "TV" on yellow), shown round.
@Composable
fun AppLogo(size: Dp) {
    Image(
        painter = painterResource(R.drawable.kdtv_logo),
        contentDescription = "KDTV",
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
    )
}
