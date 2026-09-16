package cc.kowx712.autohoyolab.ui.component.dialog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cc.kowx712.autohoyolab.R

@Composable
fun LogoutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.dialog_logout_title))
        },
        text = {
            Text(text = stringResource(R.string.dialog_logout_message))
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.dialog_logout_confirm)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_logout_cancel)) }
        }
    )
}
