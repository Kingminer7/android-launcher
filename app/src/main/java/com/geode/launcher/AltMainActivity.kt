package com.geode.lite

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.lite.main.*
import com.geode.lite.ui.theme.GeodeLauncherTheme
import com.geode.lite.ui.theme.LocalTheme
import com.geode.lite.ui.theme.Theme
import com.geode.lite.utils.GamePackageUtils
import com.geode.lite.utils.LaunchUtils
import com.geode.lite.utils.PreferenceUtils
import kotlinx.coroutines.launch

class AltMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        clearDownloadedApks(this)

        val gdInstalled = GamePackageUtils.isGameInstalled(packageManager)
        val geodeInstalled = LaunchUtils.isGeodeInstalled(this)

        val returnMessage = intent.extras?.getString(LaunchUtils.LAUNCHER_KEY_RETURN_MESSAGE)
        val returnExtendedMessage = intent.extras?.getString(LaunchUtils.LAUNCHER_KEY_RETURN_EXTENDED_MESSAGE)

        @Suppress("DEPRECATION") // the new api is android 13 only (why)
        val returnError = intent.extras?.getSerializable(LaunchUtils.LAUNCHER_KEY_RETURN_ERROR)
                as? LaunchUtils.LauncherError

        val loadFailureInfo = if (returnError != null && returnMessage != null) {
            LoadFailureInfo(returnError, returnMessage, returnExtendedMessage)
        } else if (LaunchUtils.lastSessionCrashed(this)) {
            LoadFailureInfo(LaunchUtils.LauncherError.CRASHED)
        } else { null }


        setContent {
            val themeOption by PreferenceUtils.useIntPreference(PreferenceUtils.Key.THEME)
            val theme = Theme.fromInt(themeOption)

            val backgroundOption by PreferenceUtils.useBooleanPreference(PreferenceUtils.Key.BLACK_BACKGROUND)

            val launchViewModel = viewModel<LaunchViewModel>(factory = LaunchViewModel.Factory)

            if (loadFailureInfo != null) {
                launchViewModel.loadFailure = loadFailureInfo
            }

            CompositionLocalProvider(LocalTheme provides theme) {
                GeodeLauncherTheme(theme = theme, blackBackground = backgroundOption) {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AltMainScreen(launchViewModel)
                    }
                }
            }
        }

        if (gdInstalled && geodeInstalled) {
            intent.getBooleanExtra("restarted", false).let {
                if (it) {
                    onLaunch(this)
                }
            }
        }
    }
}

data class LaunchStatusInfo(
    val title: String,
    val details: String? = null,
    val progress: (() -> Float)? = null
)

@Composable
fun LaunchCancelledIcon(cancelReason: LaunchViewModel.LaunchCancelReason, modifier: Modifier = Modifier) {
    when (cancelReason) {
        LaunchViewModel.LaunchCancelReason.GAME_OUTDATED,
        LaunchViewModel.LaunchCancelReason.GAME_MISSING -> Icon(painterResource(R.drawable.icon_error), contentDescription = null, modifier)
        LaunchViewModel.LaunchCancelReason.LAST_LAUNCH_CRASHED,
        LaunchViewModel.LaunchCancelReason.GEODE_NOT_FOUND -> Icon(Icons.Default.Warning, contentDescription = null, modifier)
        else -> Icon(Icons.Default.Info, contentDescription = null, modifier)
    }
}

@Composable
fun mapCancelReasonToInfo(cancelReason: LaunchViewModel.LaunchCancelReason): LaunchStatusInfo {
    return when (cancelReason) {
        LaunchViewModel.LaunchCancelReason.LAST_LAUNCH_CRASHED -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_crash)
        )
        LaunchViewModel.LaunchCancelReason.GEODE_NOT_FOUND -> LaunchStatusInfo(
            title = stringResource(R.string.geode_download_title)
        )
        LaunchViewModel.LaunchCancelReason.GAME_MISSING -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_error),
            details = stringResource(R.string.game_not_found)
        )
        LaunchViewModel.LaunchCancelReason.GAME_OUTDATED -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_error),
            details = stringResource(R.string.launcher_cancelled_outdated)
        )
        LaunchViewModel.LaunchCancelReason.AUTOMATIC -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_navigated_away)
        )
        else -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_manual)
        )
    }
}

@Composable
fun mapLaunchStatusToInfo(state: LaunchViewModel.LaunchUIState, inSafeMode: Boolean = false): LaunchStatusInfo {
    return when (state) {
        is LaunchViewModel.LaunchUIState.Initial,
        is LaunchViewModel.LaunchUIState.Working,
        is LaunchViewModel.LaunchUIState.Ready -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_starting_game),
            details = if (inSafeMode) stringResource(R.string.launcher_in_safe_mode) else null
        )
        is LaunchViewModel.LaunchUIState.UpdateCheck -> LaunchStatusInfo(
            title = stringResource(R.string.release_fetch_in_progress)
        )
        is LaunchViewModel.LaunchUIState.Updating -> {
            val context = LocalContext.current

            val downloaded = remember(state.downloaded) {
                Formatter.formatShortFileSize(context, state.downloaded)
            }

            val outOf = remember(state.outOf) {
                Formatter.formatShortFileSize(context, state.outOf)
            }

            LaunchStatusInfo(
                title = stringResource(R.string.launcher_downloading_update),
                details = stringResource(R.string.launcher_downloading_update_details, downloaded, outOf),
                progress = {
                    val progress = state.downloaded / state.outOf.toDouble()
                    progress.toFloat()
                }
            )
        }
        is LaunchViewModel.LaunchUIState.Cancelled -> {
            return mapCancelReasonToInfo(state.reason)
        }
    }
}

@Composable
fun LaunchCancelledBody(statusInfo: LaunchStatusInfo, icon: @Composable () -> Unit, inProgress: Boolean, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
        modifier = modifier.width(300.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                icon()
                Spacer(Modifier.size(8.dp))
                Text(statusInfo.title)
            }
        }

        if (inProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (statusInfo.details != null) {
            Text(
                statusInfo.details,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

}

@Composable
fun LaunchProgressBody(statusInfo: LaunchStatusInfo, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
        modifier = modifier.width(300.dp)
    ) {
        Text(statusInfo.title, style = MaterialTheme.typography.bodyLarge)

        if (statusInfo.progress != null) {
            LinearProgressIndicator(
                statusInfo.progress,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (statusInfo.details != null) {
            Text(statusInfo.details, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RetryButtonContents(reason: LaunchViewModel.LaunchCancelReason) {
    when (reason) {
        LaunchViewModel.LaunchCancelReason.MANUAL -> {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.launcher_cancelled_restart))
        }
        LaunchViewModel.LaunchCancelReason.LAST_LAUNCH_CRASHED,
        LaunchViewModel.LaunchCancelReason.AUTOMATIC -> {
            Icon(
                painterResource(R.drawable.icon_resume),
                contentDescription = null
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.launcher_cancelled_resume))
        }
        LaunchViewModel.LaunchCancelReason.GEODE_NOT_FOUND -> {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.launcher_cancelled_retry))
        }
        // these shouldn't be allowing retries anyways
        LaunchViewModel.LaunchCancelReason.GAME_MISSING,
        LaunchViewModel.LaunchCancelReason.GAME_OUTDATED -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaunchProgressCard(
    uiState: LaunchViewModel.LaunchUIState,
    crashInfo: LoadFailureInfo?,
    onCancel: () -> Unit, 
    onResume: (safeMode: Boolean) -> Unit,
    onMore: () -> Unit,
    extraOptions: @Composable () -> Unit,
    safeModeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showSafeModeDialog by remember { mutableStateOf(false) }
    val status = mapLaunchStatusToInfo(uiState, safeModeEnabled)

    Card(modifier = modifier.width(300.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            if (uiState is LaunchViewModel.LaunchUIState.Cancelled) {
                LaunchCancelledBody(
                    statusInfo = status,
                    icon = { LaunchCancelledIcon(uiState.reason, Modifier.size(32.dp)) },
                    inProgress = uiState.inProgress
                )

                if (uiState.reason == LaunchViewModel.LaunchCancelReason.GAME_OUTDATED) {
                    val context = LocalContext.current

                    // game should be downloaded if this message is showing
                    val showDownload = remember {
                        !GamePackageUtils.identifyGameLegitimacy(context.packageManager)
                    }

                    if (showDownload) {
                        GooglePlayBadge(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }

                if (uiState.reason.allowsRetry()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.width(300.dp)
                    ) {
                        LongPressButton(onClick = { onResume(false) }, onLongPress = {
                            showSafeModeDialog = true
                        }) {
                            RetryButtonContents(uiState.reason)
                        }

                        Spacer(Modifier.weight(1.0f))

                        if (crashInfo != null) {
                            IconButton(onClick = onMore) {
                                Icon(
                                    painterResource(R.drawable.icon_question_mark),
                                    contentDescription = stringResource(R.string.launcher_cancelled_help_alt)
                                )
                            }
                        }

                        extraOptions()
                    }
                }
            } else {
                LaunchProgressBody(statusInfo = status)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(300.dp)) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.release_fetch_button_cancel))
                    }

                    Spacer(Modifier.weight(1.0f))

                    extraOptions()
                }
            }
        }
    }

    if (showSafeModeDialog) {
        SafeModeDialog(
            onDismiss = {
                showSafeModeDialog = false
            },
            onLaunch = {
                onResume(true)
                showSafeModeDialog = false
            }
        )
    }
}

@Composable
fun ExtraOptions(onSettings: () -> Unit) {
    IconButton(
        onClick = onSettings,
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = stringResource(R.string.launcher_settings_icon_alt)
        )
    }
}

@Composable
fun GeodeLogo(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Image(
            painter = painterResource(id = R.drawable.geode_logo),
            contentDescription = null,
            modifier = Modifier.size(76.dp, 76.dp)
        )
        Text(
            stringResource(R.string.launcher_title),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun AltMainScreen(
    launchViewModel: LaunchViewModel = viewModel(factory = LaunchViewModel.Factory)
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        launchViewModel.beginLaunchFlow()
    }

    val launchUIState by launchViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var launchInSafeMode by remember { mutableStateOf(false) }

    var showErrorInfo by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .height(350.dp)
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            GeodeLogo()

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LaunchProgressCard(
                    launchUIState,
                    crashInfo = launchViewModel.currentCrashInfo(),
                    onCancel = {
                        launchInSafeMode = false
                        coroutineScope.launch {
                            launchViewModel.cancelLaunch()
                        }
                    },
                    onResume = { safeMode ->
                        launchInSafeMode = safeMode

                        launchViewModel.clearCrashInfo()
                        coroutineScope.launch {
                            launchViewModel.beginLaunchFlow(true)
                        }
                    },
                    onMore = {
                        showErrorInfo = true
                    },
                    extraOptions = {
                        ExtraOptions(
                            onSettings = {
                                coroutineScope.launch {
                                    launchViewModel.cancelLaunch(true)
                                }

                                onSettings(context)
                            }
                        )
                    },
                    safeModeEnabled = launchInSafeMode
                )

                val nextLauncherUpdate by launchViewModel.nextLauncherUpdate.collectAsState()

                // only show launcher update in a case where a user won't see it ingame
                if (nextLauncherUpdate != null && launchUIState is LaunchViewModel.LaunchUIState.Cancelled) {
                    StatusIndicator(
                        icon = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            installLauncherUpdate(context)
                        },
                        text = {
                            Text(
                                stringResource(R.string.launcher_update_available),
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                }
            }
        }
    }

    if (launchUIState is LaunchViewModel.LaunchUIState.Ready) {
        UpdateWarning(launchInSafeMode) {
            launchInSafeMode = false
            coroutineScope.launch {
                launchViewModel.cancelLaunch()
            }
        }
    }

    val loadFailure = launchViewModel.currentCrashInfo()
    if (showErrorInfo && loadFailure != null) {
        ErrorInfoSheet(loadFailure, onDismiss = { showErrorInfo = false })
    }
}