package com.geode.launcher

import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geode.launcher.main.*
import com.geode.launcher.ui.theme.GeodeLauncherTheme
import com.geode.launcher.ui.theme.LocalTheme
import com.geode.launcher.ui.theme.Theme
import com.geode.launcher.utils.GamePackageUtils
import com.geode.launcher.utils.LaunchUtils
import com.geode.launcher.utils.PreferenceUtils
import kotlinx.coroutines.launch

class AltMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        enableEdgeToEdge()

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
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        WithWave {
                            AltMainScreen(launchViewModel)
                        }
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
fun LaunchCancelledIcon(cancelReason: LaunchViewModel.LaunchCancelReason) {
    when (cancelReason) {
        LaunchViewModel.LaunchCancelReason.GAME_OUTDATED,
        LaunchViewModel.LaunchCancelReason.GAME_MISSING -> Icon(painterResource(R.drawable.icon_error), contentDescription = null)
        LaunchViewModel.LaunchCancelReason.LAST_LAUNCH_CRASHED,
        LaunchViewModel.LaunchCancelReason.GEODE_NOT_FOUND -> Icon(Icons.Default.Warning, contentDescription = null)
        else -> Icon(Icons.Default.Info, contentDescription = null)
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
        else -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_cancelled_manual)
        )
    }
}

@Composable
fun mapLaunchStatusToInfo(state: LaunchViewModel.LaunchUIState): LaunchStatusInfo {
    return when (state) {
        is LaunchViewModel.LaunchUIState.Initial,
        is LaunchViewModel.LaunchUIState.Working,
        is LaunchViewModel.LaunchUIState.Ready -> LaunchStatusInfo(
            title = stringResource(R.string.launcher_starting_game)
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
fun LaunchCancelledBody(statusInfo: LaunchStatusInfo, icon: @Composable () -> Unit, inProgress: Boolean) {
    Row {
        icon()
        Text(statusInfo.title)
    }

    if (inProgress) {
        LinearProgressIndicator()
    }

    if (statusInfo.details != null) {
        Text(statusInfo.details)
    }
}

@Composable
fun LaunchProgressBody(statusInfo: LaunchStatusInfo, modifier: Modifier = Modifier) {
    Column {
        Text(statusInfo.title)

        if (statusInfo.progress != null) {
            LinearProgressIndicator(statusInfo.progress)
        } else {
            LinearProgressIndicator()
        }

        if (statusInfo.details != null) {
            Text(statusInfo.details)
        }
    }
}

@Composable
fun LaunchProgressCard(uiState: LaunchViewModel.LaunchUIState, onCancel: () -> Unit, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val status = mapLaunchStatusToInfo(uiState)
    Card(modifier = modifier) {
        if (uiState is LaunchViewModel.LaunchUIState.Cancelled) {
            LaunchCancelledBody(
                statusInfo = status,
                icon = { LaunchCancelledIcon(uiState.reason) },
                inProgress = uiState.inProgress
            )

            if (uiState.reason.allowsRetry()) {
                TextButton(onClick = onResume) {
                    Text(stringResource(R.string.launcher_cancelled_restart))
                }
            }
        } else {
            LaunchProgressBody(statusInfo = status)

            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.release_fetch_button_cancel))
            }
        }

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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        GeodeLogo(modifier = Modifier.offset(y = (-90).dp))

        LaunchProgressCard(
            launchUIState,
            onCancel = {
                launchInSafeMode = false
                coroutineScope.launch {
                    launchViewModel.cancelLaunch()
                }
            },
            onResume = {
                launchViewModel.loadFailure = null
                coroutineScope.launch {
                    launchViewModel.beginLaunchFlow()
                }
            },
            modifier = Modifier.offset(y = 90.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        launchViewModel.cancelLaunch()
                    }

                    onSettings(context)
                },
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(context.getString(R.string.launcher_settings))
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
}