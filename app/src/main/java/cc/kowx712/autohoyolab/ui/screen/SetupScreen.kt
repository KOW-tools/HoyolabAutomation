package cc.kowx712.autohoyolab.ui.screen

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cc.kowx712.autohoyolab.R
import cc.kowx712.autohoyolab.ui.WebViewActivity
import cc.kowx712.autohoyolab.ui.component.ExpressiveScaffold
import cc.kowx712.autohoyolab.ui.component.defaultSegmentedColors
import cc.kowx712.autohoyolab.ui.component.defaultSegmentedShape
import cc.kowx712.autohoyolab.ui.component.expressiveTopAppBarColors

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var notificationGranted by remember { mutableStateOf(false) }

    // Permission launcher for notifications
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationGranted = isGranted
    }

    // Check notification permission status
    fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // For SDK < 33, notification permission is automatically granted
            true
        }
    }

    // Request notification permission
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationGranted = true
        }
    }

    // Request autostart permission
    fun requestAutostartPermission() {
        try {
            val intent: Intent
            val manufacturer = Build.MANUFACTURER.lowercase()

            when {
                manufacturer.contains("xiaomi") -> {
                    intent = Intent().apply {
                        component = ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }

                manufacturer.contains("oppo") -> {
                    intent = Intent().apply {
                        component = ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }

                manufacturer.contains("vivo") -> {
                    intent = Intent().apply {
                        component = ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }

                manufacturer.contains("huawei") -> {
                    intent = Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }

                manufacturer.contains("honor") -> {
                    // Try new Honor first (com.hihonor), fallback to old Huawei-based
                    val newHonorIntent = Intent().apply {
                        component = ComponentName(
                            "com.hihonor.systemmanager",
                            "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                    val oldHonorIntent = Intent().apply {
                        component = ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }

                    // Check which one is available
                    intent = if (context.packageManager.resolveActivity(
                            newHonorIntent,
                            PackageManager.MATCH_DEFAULT_ONLY
                        ) != null
                    ) {
                        newHonorIntent
                    } else {
                        oldHonorIntent
                    }
                }

                manufacturer.contains("samsung") -> {
                    intent = Intent().apply {
                        component = ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.ui.battery.BatteryActivity"
                        )
                    }
                }

                else -> {
                    intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
            }

            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Request battery optimization exemption
    fun requestBatteryOptimization() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    // Update permission status on screen resume
    LaunchedEffect(lifecycleOwner) {
        notificationGranted = checkNotificationPermission()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = checkNotificationPermission()

                // Check if logged in and notification granted to complete setup
                val cookiePrefs = context.getSharedPreferences("hoyolab_secure_prefs", android.content.Context.MODE_PRIVATE)
                val appPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                val isLoggedIn = cookiePrefs.getString("cookie", null) != null

                if (notificationGranted && isLoggedIn) {
                    appPrefs.edit { putBoolean("setup_complete", true) }
                    onSetupComplete()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    val setupItems = buildList {
                        add(
                            Triple(
                                stringResource(R.string.setup_grant_notification),
                                { requestNotificationPermission() },
                                if (notificationGranted) {
                                    @Composable {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else null
                            )
                        )
                        add(
                            Triple(
                                stringResource(R.string.setup_grant_autostart),
                                { requestAutostartPermission() },
                                @Composable {
                                    Text(
                                        text = stringResource(R.string.setup_recommended),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        )
                        add(
                            Triple(
                                stringResource(R.string.setup_disable_battery_optimization),
                                { requestBatteryOptimization() },
                                @Composable {
                                    Text(
                                        text = stringResource(R.string.setup_recommended),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        )
                    }

                    setupItems.forEachIndexed { index, (title, onClick, trailingContent) ->
                        SegmentedListItem(
                            onClick = onClick,
                            shapes = defaultSegmentedShape(index = index, count = setupItems.size),
                            colors = defaultSegmentedColors(),
                            content = { Text(title) },
                            trailingContent = trailingContent
                        )
                    }
                }
            }

            item {
                // Login button
                Button(
                    onClick = {
                        context.startActivity(Intent(context, WebViewActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.button_login_hoyolab))
                }
            }
        }
    }
}
