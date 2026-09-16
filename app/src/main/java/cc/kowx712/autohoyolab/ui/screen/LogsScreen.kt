package cc.kowx712.autohoyolab.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.kowx712.autohoyolab.R
import cc.kowx712.autohoyolab.data.local.CheckInLog
import cc.kowx712.autohoyolab.data.model.HoyoGame
import cc.kowx712.autohoyolab.ui.component.ExpressiveScaffold
import cc.kowx712.autohoyolab.ui.component.TopBarBackButton
import cc.kowx712.autohoyolab.ui.component.defaultSegmentedColors
import cc.kowx712.autohoyolab.ui.component.defaultSegmentedShape
import cc.kowx712.autohoyolab.ui.component.expressiveTopAppBarColors
import cc.kowx712.autohoyolab.ui.viewmodel.LogsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val filterSuccess by viewModel.filterSuccess.collectAsStateWithLifecycle()
    val filterFailed by viewModel.filterFailed.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    TopBarBackButton(onClick = onBack)
                },
                actions = {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = null)
                        }

                        DropdownMenuPopup(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            val groupInteractionSource = remember { MutableInteractionSource() }

                            DropdownMenuGroup(
                                shapes = MenuDefaults.groupShape(0, 1),
                                interactionSource = groupInteractionSource,
                            ) {
                                CheckableDropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_success)) },
                                    shapes = MenuDefaults.itemShape(0, 2),
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    checked = filterSuccess,
                                    onCheckedChange = { viewModel.setFilterSuccess(it) },
                                )

                                CheckableDropdownMenuItem(
                                    text = { Text(stringResource(R.string.filter_failed)) },
                                    shapes = MenuDefaults.itemShape(1, 2),
                                    checkedLeadingIcon = {
                                        Icon(
                                            Icons.Filled.Check,
                                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                            contentDescription = null,
                                        )
                                    },
                                    checked = filterFailed,
                                    onCheckedChange = { viewModel.setFilterFailed(it) },
                                )
                            }
                        }
                    }
                },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.logs_empty_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    items(logs) { log ->
                        val index = logs.indexOf(log)
                        LogCard(log, index, logs.size)
                    }
                }
            }
        }
    }
}

@Composable
fun LogCard(log: CheckInLog, index: Int, count: Int) {
    val game = HoyoGame.fromGameBiz(log.gameId)
    val gameName = game?.displayName ?: log.gameId

    val iconData: Pair<ImageVector, Color> = when (log.status) {
        "SUCCESS" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        "ALREADY_SIGNED" -> Icons.Default.Done to MaterialTheme.colorScheme.tertiary
        "FAILED" -> Icons.Default.Error to MaterialTheme.colorScheme.error
        "COOKIE_EXPIRED" -> Icons.Default.Warning to MaterialTheme.colorScheme.error
        "NETWORK_ERROR" -> Icons.Default.SignalWifiStatusbarConnectedNoInternet4 to MaterialTheme.colorScheme.error
        else -> Icons.Default.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon = iconData.first
    val iconColor = iconData.second

    SegmentedListItem(
        shapes = defaultSegmentedShape(index = index, count = count),
        colors = defaultSegmentedColors(),
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
            )
        },
        trailingContent = {
            if (game?.imageResId != null) {
                Image(
                    painter = painterResource(game.imageResId),
                    contentDescription = game.displayName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            }
        },
        overlineContent = {
            Text(gameName, fontWeight = FontWeight.Bold)
        },
        supportingContent = {
            Column {
                Row {
                    if (log.gameUid != null) {
                        Text(
                            stringResource(R.string.label_uid_format, log.gameUid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (log.region != null) {
                        Text(
                            " • ${log.region}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        content = {
            Text(log.message ?: stringResource(R.string.log_no_message))
        }
    )
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
