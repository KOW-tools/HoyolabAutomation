package cc.kowx712.autohoyolab.ui.component

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable

@Composable
fun defaultSegmentedColors(): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = colorScheme.surfaceBright,
    disabledContainerColor = colorScheme.surfaceBright,
    supportingContentColor = colorScheme.onSurfaceVariant
)

@Composable
fun defaultSegmentedShape(index: Int, count: Int): ListItemShapes {
    val base = ListItemDefaults.segmentedShapes(index, count)
    return if (count == 1) {
        base.copy(shape = MaterialTheme.shapes.large)
    } else {
        base
    }
}
