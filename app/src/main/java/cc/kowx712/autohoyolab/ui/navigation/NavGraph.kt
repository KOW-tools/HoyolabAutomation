package cc.kowx712.autohoyolab.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import cc.kowx712.autohoyolab.ui.screen.HomeScreen
import cc.kowx712.autohoyolab.ui.screen.LogsScreen
import cc.kowx712.autohoyolab.ui.screen.SetupScreen
import cc.kowx712.autohoyolab.ui.viewmodel.HomeViewModel
import cc.kowx712.autohoyolab.ui.viewmodel.HomeViewModelFactory
import cc.kowx712.autohoyolab.ui.viewmodel.LogsViewModel
import cc.kowx712.autohoyolab.ui.viewmodel.LogsViewModelFactory

@Composable
fun AppNavGraph(
    navigator: Navigator
) {
    val context = LocalContext.current

    NavDisplay(
        backStack = navigator.backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        transitionSpec = {
            val enter = slideInHorizontally(initialOffsetX = { it })
            val exit = slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut()
            enter togetherWith exit
        },
        popTransitionSpec = {
            val enter = slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
            val exit = scaleOut(targetScale = 0.9f) + fadeOut()
            enter togetherWith exit
        },
        predictivePopTransitionSpec = {
            val enter = slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn()
            val exit = scaleOut(targetScale = 0.9f) + fadeOut()
            enter togetherWith exit
        },
        onBack = { navigator.pop() },
        entryProvider = entryProvider {
            entry<Setup> {
                SetupScreen(
                    onSetupComplete = {
                        navigator.replace(Home)
                    }
                )
            }
            entry<Home> {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModelFactory(context)
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToLogs = { navigator.push(Logs) }
                )
            }
            entry<Logs> {
                val logsViewModel: LogsViewModel = viewModel(
                    factory = LogsViewModelFactory(context)
                )
                LogsScreen(
                    viewModel = logsViewModel,
                    onBack = { navigator.pop() }
                )
            }
        }
    )
}
