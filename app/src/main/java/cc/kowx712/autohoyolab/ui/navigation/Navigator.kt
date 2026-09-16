package cc.kowx712.autohoyolab.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

/**
 * Simple navigation helper that owns a back stack.
 */
class Navigator(
    initialKey: NavKey
) {
    val backStack: SnapshotStateList<NavKey> = mutableStateListOf(initialKey)

    /**
     * Push a key onto the back stack.
     */
    fun push(key: NavKey) {
        backStack.add(key)
    }

    /**
     * Replace the top key, or push if the stack is empty.
     */
    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    /**
     * Replace the backstack with a new list of keys if the stack is not empty.
     */
    fun replaceAll(keys: List<NavKey>) {
        if (keys.isEmpty()) {
            return
        }
        if (backStack.isNotEmpty()) {
            backStack.clear()
            backStack.addAll(keys)
        }
    }

    /**
     * Pop the top key if present.
     */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    companion object {
        val Saver: Saver<Navigator, Any> = Saver(
            save = { navigator ->
                navigator.backStack.map { key ->
                    when (key) {
                        is Home -> "Home"
                        is Logs -> "Logs"
                        else -> "Home"
                    }
                }
            },
            restore = { savedList ->
                @Suppress("UNCHECKED_CAST")
                val stringList = savedList as? List<String> ?: listOf("Home")
                val keys = stringList.map { str ->
                    when (str) {
                        "Logs" -> Logs
                        else -> Home
                    }
                }
                val initialKey = keys.firstOrNull() ?: Home
                val navigator = Navigator(initialKey)
                navigator.backStack.clear()
                navigator.backStack.addAll(keys)
                navigator
            }
        )
    }
}

@Composable
fun rememberNavigator(startRoute: NavKey): Navigator {
    return rememberSaveable(startRoute, saver = Navigator.Saver) {
        Navigator(startRoute)
    }
}
