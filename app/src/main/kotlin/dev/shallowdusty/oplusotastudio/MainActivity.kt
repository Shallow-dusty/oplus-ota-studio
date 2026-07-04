package dev.shallowdusty.oplusotastudio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.shallowdusty.oplusotastudio.feature.downloads.DownloadsScreen
import dev.shallowdusty.oplusotastudio.feature.downloads.DownloadsViewModel
import dev.shallowdusty.oplusotastudio.feature.lookup.LookupScreen
import dev.shallowdusty.oplusotastudio.feature.lookup.LookupViewModel
import dev.shallowdusty.oplusotastudio.ui.theme.OtaStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OtaStudioTheme {
                OtaStudioApp()
            }
        }
    }
}

private sealed interface Dest {
    val route: String
    @get:StringRes
    val labelRes: Int

    data object Lookup : Dest {
        override val route = "lookup"
        override val labelRes = R.string.nav_lookup
    }
    data object Downloads : Dest {
        override val route = "downloads"
        override val labelRes = R.string.nav_downloads
    }
}

@Composable
private fun OtaStudioApp() {
    val navController = rememberNavController()
    val destinations = listOf(Dest.Lookup, Dest.Downloads)
    val requestDownloadStorageAccess = rememberDownloadStorageAccessRequester()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination
            NavigationBar {
                destinations.forEach { dest ->
                    val selected = current?.hierarchy?.any { it.route == dest.route } == true
                    val label = stringResource(dest.labelRes)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (dest is Dest.Lookup) Icons.Filled.Search else Icons.Filled.CloudDownload,
                                contentDescription = label,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Dest.Lookup.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Dest.Lookup.route) {
                val graph = androidx.compose.ui.platform.LocalContext.current.applicationContext
                    .let { it as OtaStudioApplication }.graph
                LookupScreen(
                    factory = {
                        LookupViewModel(
                            deviceDetector = graph.deviceDetector,
                            lookupService = graph.otaLookupService,
                            downloadEngine = graph.downloadEngine,
                            packageRepository = graph.packageRepository,
                            privacyConsentStore = graph.lookupPrivacyConsentStore,
                        )
                    },
                    beforeDownload = requestDownloadStorageAccess,
                    onDownloadQueued = {
                        navController.navigate(Dest.Downloads.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Dest.Downloads.route) {
                val graph = androidx.compose.ui.platform.LocalContext.current.applicationContext
                    .let { it as OtaStudioApplication }.graph
                DownloadsScreen(
                    factory = {
                        DownloadsViewModel(
                            engine = graph.downloadEngine,
                            packageRepository = graph.packageRepository,
                        )
                    },
                    beforeDownload = requestDownloadStorageAccess,
                )
            }
        }
    }
}

@Composable
private fun rememberDownloadStorageAccessRequester(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    val pendingAction = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction.value
        pendingAction.value = null
        if (granted) {
            action?.invoke()
        }
    }

    return remember(context, launcher) {
        { action ->
            val alreadyAllowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            if (alreadyAllowed) {
                action()
            } else {
                pendingAction.value = action
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}
