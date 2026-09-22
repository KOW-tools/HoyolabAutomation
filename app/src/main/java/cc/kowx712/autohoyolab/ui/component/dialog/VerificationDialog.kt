package cc.kowx712.autohoyolab.ui.component.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.kowx712.autohoyolab.R
import cc.kowx712.autohoyolab.ui.viewmodel.SetupViewModel

@Composable
fun VerificationDialog(
    verificationCode: String,
    loginState: SetupViewModel.LoginState,
    otpCountdown: Int,
    otpSending: Boolean,
    otpError: String?,
    onVerificationCodeChange: (String) -> Unit,
    onSendOtp: () -> Unit,
    onVerifyEmail: () -> Unit,
    onDismiss: () -> Unit,
    onResetLoginState: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_verification_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.setup_verification_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = onVerificationCodeChange,
                    label = { Text(stringResource(R.string.setup_verification_code_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onVerifyEmail() }
                    ),
                    singleLine = true,
                    enabled = loginState !is SetupViewModel.LoginState.Loading
                )

                Button(
                    onClick = onSendOtp,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !otpSending && otpCountdown == 0 && loginState !is SetupViewModel.LoginState.Loading
                ) {
                    if (otpSending) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                    } else if (otpCountdown > 0) {
                        Text(otpCountdown.toString())
                    } else {
                        Text(stringResource(R.string.setup_verification_send_otp))
                    }
                }

                if (otpError != null) {
                    Text(
                        text = otpError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                if (loginState is SetupViewModel.LoginState.Error) {
                    LaunchedEffect(loginState) {
                        onResetLoginState()
                    }

                    Text(
                        text = loginState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onVerifyEmail,
                enabled = loginState !is SetupViewModel.LoginState.Loading && verificationCode.isNotBlank()
            ) {
                if (loginState is SetupViewModel.LoginState.Loading) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.setup_verification_button))
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
