package cc.kowx712.autohoyolab.ui.screen

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cc.kowx712.autohoyolab.R
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.data.model.HoyoGameRole
import cc.kowx712.autohoyolab.notification.CheckInNotifier
import cc.kowx712.autohoyolab.ui.WebViewActivity
import cc.kowx712.autohoyolab.ui.component.ExpressiveScaffold
import cc.kowx712.autohoyolab.ui.component.defaultSegmentedColors
import cc.kowx712.autohoyolab.ui.component.defaultSegmentedShape
import cc.kowx712.autohoyolab.ui.component.dialog.LogoutConfirmationDialog
import cc.kowx712.autohoyolab.ui.component.expressiveTopAppBarColors
import cc.kowx712.autohoyolab.ui.viewmodel.AccountState
import cc.kowx712.autohoyolab.ui.viewmodel.HomeViewModel
import cc.kowx712.autohoyolab.worker.AlarmScheduler
import cc.kowx712.autohoyolab.worker.CheckInWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pullToRefreshState = rememberPullToRefreshState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val accountInfo by viewModel.accountInfo.collectAsStateWithLifecycle()
    val gameRoles by viewModel.gameRoles.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingGames by viewModel.isLoadingGames.collectAsStateWithLifecycle()
    val lastLogs by viewModel.lastLogs.collectAsStateWithLifecycle()
    val selectedProfiles by viewModel.selectedProfiles.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }

    // Reload account info when screen resumes (e.g., returning from WebViewActivity)
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadAccountInfo()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToLogs) {
                        Icon(Icons.Default.History, stringResource(R.string.content_desc_view_logs))
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.loadAccountInfo() },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                // Account Status Card
                item {
                    AccountStatusCard(
                        accountState = accountInfo,
                        onLoginClick = {
                            context.startActivity(Intent(context, WebViewActivity::class.java))
                        },
                        onRefreshClick = { viewModel.loadAccountInfo() },
                        onRunNowClick = {
                            val workRequest = OneTimeWorkRequestBuilder<CheckInWorker>().build()
                            WorkManager.getInstance(context).enqueue(workRequest)
                            CheckInNotifier(context).showManualCheckInStarted()
                        },
                        onLogoutClick = {
                            showLogoutDialog = true
                        },
                        onReloginClick = {
                            viewModel.relogin()
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Games List
                if (gameRoles.isNullOrEmpty()) {
                    if (isLoadingGames) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.home_loading_games),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else if (accountInfo is AccountState.Success) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.home_no_games_found),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.home_games),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                    }

                    // Group games by gameId to detect multiple servers
                    val gamesById = gameRoles!!.groupBy { it.first.id }

                    items(gameRoles!!) { (game, role) ->
                        val gameId = game.id
                        val profiles = gamesById[gameId] ?: listOf(game to role)
                        val hasMultipleServers = profiles.size > 1
                        val globalIndex = gameRoles!!.indexOf(game to role)

                        GameRoleCard(
                            role = role,
                            index = globalIndex,
                            count = gameRoles!!.size,
                            lastLog = lastLogs[game.id],
                            hasMultipleServers = hasMultipleServers,
                            isSelected = selectedProfiles[gameId] == role.gameUid,
                            onSelectProfile = { viewModel.selectProfile(gameId, role.gameUid, role.region) },
                            onClick = { }
                        )
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                viewModel.logout()
                AlarmScheduler.cancelAlarm(context)
            }
        )
    }
}

@Composable
fun AccountStatusCard(
    accountState: AccountState,
    onLoginClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onRunNowClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onReloginClick: () -> Unit
) {
    when (accountState) {
        is AccountState.Loading -> {
            ListItem(
                content = { LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
            )
        }

        is AccountState.NoCookie -> {
            ListItem(
                content = { Text(stringResource(R.string.account_status_no_login)) },
                supportingContent = { Text(stringResource(R.string.account_status_tap_to_login)) },
                leadingContent = {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    supportingContentColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
                onClick = onLoginClick
            )
        }

        is AccountState.Expired -> {
            ListItem(
                content = { Text(stringResource(R.string.account_status_cookie_expired)) },
                supportingContent = { Text(stringResource(R.string.account_status_tap_to_login)) },
                leadingContent = {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    supportingContentColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
                onClick = onLoginClick
            )
        }

        is AccountState.Error -> {
            ListItem(
                content = { Text(stringResource(R.string.account_status_error_loading)) },
                supportingContent = { Text(accountState.message) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Help,
                        contentDescription = null
                    )
                },
                trailingContent = {
                    FilledIconButton(
                        onClick = onRefreshClick,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                        )
                    }
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    supportingContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
            )
        }

        is AccountState.Success -> {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ListItem(
                    content = { Text(accountState.accountName) },
                    supportingContent = {
                        if (accountState.expiresAt > 0) {
                            Text(stringResource(R.string.label_expires_in, calculateDaysRemaining(accountState.expiresAt)))
                        }
                    },
                    leadingContent = {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        supportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape),
                    onClick = onReloginClick
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    // Account info rows
                    val infoItems = buildList {
                        add(Triple(Icons.Filled.Email, stringResource(R.string.label_email), accountState.email))
                        add(
                            Triple(
                                Icons.Filled.SportsEsports,
                                stringResource(R.string.label_games),
                                stringResource(R.string.label_games_found_format, accountState.gameCount)
                            )
                        )
                        add(Triple(Icons.Filled.DoneAll, stringResource(R.string.label_last_validated), formatDate(accountState.validatedAt)))
                    }

                    infoItems.forEachIndexed { index, (icon, label, value) ->
                        SegmentedListItem(
                            onClick = { },
                            shapes = defaultSegmentedShape(index = index, count = infoItems.size),
                            colors = defaultSegmentedColors(),
                            leadingContent = { Icon(icon, contentDescription = null) },
                            content = { Text(label) },
                            supportingContent = { Text(value) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SplitButtonDefaults.Spacing)
                ) {
                    SplitButtonDefaults.LeadingButton(
                        onClick = onRunNowClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.button_run_checkin_now))
                    }
                    SplitButtonDefaults.TrailingButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.button_logout))
                    }
                }
            }
        }
    }
}

@Composable
fun GameRoleCard(
    role: HoyoGameRole,
    index: Int,
    count: Int,
    lastLog: String?,
    hasMultipleServers: Boolean,
    isSelected: Boolean,
    onSelectProfile: () -> Unit,
    onClick: () -> Unit
) {
    val game = HoyoGame.fromGameBiz(role.gameBiz)
    val gameIconRes = game?.imageResId ?: R.drawable.img_hoyolab

    SegmentedListItem(
        onClick = onClick,
        shapes = defaultSegmentedShape(index = index, count = count),
        colors = defaultSegmentedColors(),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            Image(
                painter = painterResource(id = gameIconRes),
                contentDescription = game?.displayName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentScale = ContentScale.Crop
            )
        },
        trailingContent = {
            if (hasMultipleServers) {
                RadioButton(
                    selected = isSelected,
                    onClick = onSelectProfile
                )
            }
        },
        supportingContent = {
            Column {
                Text(
                    text = "${role.nickname} • Lv.${role.level} • ${role.gameUid} • ${role.regionName}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (lastLog != null) {
                    Text(
                        text = "Last check-in: $lastLog",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        content = {
            Text(game?.displayName ?: "Unknown Game")
        }
    )
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun calculateDaysRemaining(expiresAt: Long): String {
    if (expiresAt <= 0) {
        return stringResource(R.string.expires_unknown)
    }

    val currentTime = System.currentTimeMillis()
    val remainingMs = expiresAt - currentTime

    if (remainingMs <= 0) {
        return stringResource(R.string.expires_expired)
    }

    val days = remainingMs / (1000 * 60 * 60 * 24)
    return when {
        days > 1 -> stringResource(R.string.expires_days, days)
        days == 1L -> stringResource(R.string.expires_one_day)
        else -> {
            val hours = remainingMs / (1000 * 60 * 60)
            if (hours > 1) stringResource(R.string.expires_hours, hours) else stringResource(R.string.expires_less_than_hour)
        }
    }
}
