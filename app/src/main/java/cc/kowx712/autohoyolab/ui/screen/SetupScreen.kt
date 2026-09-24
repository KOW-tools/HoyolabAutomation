package cc.kowx712.autohoyolab.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.kowx712.autohoyolab.R
import cc.kowx712.autohoyolab.data.preferences.AppPrefs
import cc.kowx712.autohoyolab.ui.component.CaptchaWidget
import cc.kowx712.autohoyolab.ui.component.ExpressiveScaffold
import cc.kowx712.autohoyolab.ui.component.defaultSegmentedColors
import cc.kowx712.autohoyolab.ui.component.defaultSegmentedShape
import cc.kowx712.autohoyolab.ui.component.dialog.VerificationDialog
import cc.kowx712.autohoyolab.ui.component.expressiveTopAppBarColors
import cc.kowx712.autohoyolab.ui.viewmodel.SetupViewModel
import cc.kowx712.autohoyolab.ui.viewmodel.SetupViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val coroutineScope = rememberCoroutineScope()

    val viewModel: SetupViewModel = viewModel(
        factory = SetupViewModelFactory(context)
    )

    val loginState by viewModel.loginState.collectAsState()
    val captchaSession by viewModel.captchaSession.collectAsState()
    val notificationGranted by viewModel.notificationGranted.collectAsState()
    val otpCountdown by viewModel.otpCountdown.collectAsState()
    val otpSending by viewModel.otpSending.collectAsState()
    val otpError by viewModel.otpError.collectAsState()

    // Keep the completion page mounted even when email verification is skipped.
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Permission launcher for notifications
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.updateNotificationPermission(isGranted)
    }

    // Request notification permission
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.updateNotificationPermission(true)
        }
    }

    // Update permission status on screen resume and detach the observer when
    // this screen leaves composition.
    DisposableEffect(lifecycleOwner) {
        viewModel.checkNotificationPermission()

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNotificationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle login state changes
    LaunchedEffect(loginState) {
        when (loginState) {
            is SetupViewModel.LoginState.Success -> {
                // Move to completion page
                pagerState.animateScrollToPage(2)
            }

            else -> {}
        }
    }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { padding ->
        val navBars = WindowInsets.navigationBars.asPaddingValues()
        val captionBar = WindowInsets.captionBar.asPaddingValues()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 16.dp + navBars.calculateBottomPadding() + captionBar.calculateBottomPadding())
        ) {
            item {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillParentMaxSize(),
                    userScrollEnabled = false
                ) { page ->
                    when (page) {
                        0 -> PermissionsPage(
                            notificationGranted = notificationGranted,
                            onRequestNotification = { requestNotificationPermission() },
                            onRequestAutostart = { viewModel.requestAutostartPermission() },
                            onRequestBattery = { viewModel.requestBatteryOptimization() },
                            onNext = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(1)
                                }
                            }
                        )

                        1 -> LoginPage(
                            viewModel = viewModel,
                            loginState = loginState,
                            onBack = {
                                viewModel.cancelLogin()
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        )

                        2 -> CompletionPage(
                            enabled = loginState is SetupViewModel.LoginState.Success,
                            onComplete = {
                                AppPrefs.setSetupComplete(context, true)
                                onSetupComplete()
                            }
                        )
                    }
                }
            }
        }
    }

    if (loginState is SetupViewModel.LoginState.EmailVerificationRequired) {
        VerificationDialog(
            verificationCode = viewModel.verificationCode.collectAsState().value,
            loginState = loginState,
            otpCountdown = otpCountdown,
            otpSending = otpSending,
            otpError = otpError,
            onVerificationCodeChange = { viewModel.updateVerificationCode(it) },
            onSendOtp = { viewModel.sendOtp() },
            onVerifyEmail = { viewModel.verifyEmail() },
            onDismiss = { viewModel.cancelLogin() },
            onResetLoginState = { viewModel.resetLoginState() }
        )
    }

    if (captchaSession != null) {
        CaptchaWidget(
            session = captchaSession!!,
            onDismiss = { viewModel.cancelCaptchaVerification() },
            onCaptchaSuccess = { aigis ->
                viewModel.submitCaptchaResult(aigis)
            }
        )
    }
}

@Composable
private fun PermissionsPage(
    notificationGranted: Boolean,
    onRequestNotification: () -> Unit,
    onRequestAutostart: () -> Unit,
    onRequestBattery: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_permissions_title),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(R.string.setup_permissions_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                val setupItems = buildList {
                    add(
                        Triple(
                            stringResource(R.string.setup_grant_notification),
                            onRequestNotification,
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
                            onRequestAutostart,
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
                            onRequestBattery,
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = notificationGranted
        ) {
            Text(stringResource(R.string.setup_button_next))
        }
    }
}

@Composable
private fun LoginPage(
    viewModel: SetupViewModel,
    loginState: SetupViewModel.LoginState,
    onBack: () -> Unit
) {
    val email by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    fun submitLogin() {
        // Keep an error visible until the user starts another attempt.
        if (loginState is SetupViewModel.LoginState.Error) {
            viewModel.resetLoginState()
        }
        viewModel.login()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_login_title),
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = stringResource(R.string.setup_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = email,
                onValueChange = { viewModel.updateEmail(it) },
                label = { Text(stringResource(R.string.setup_email_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                enabled = loginState !is SetupViewModel.LoginState.Loading &&
                        loginState !is SetupViewModel.LoginState.CaptchaRequired &&
                        loginState !is SetupViewModel.LoginState.EmailVerificationRequired
            )

            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text(stringResource(R.string.setup_password_label)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submitLogin() }
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) stringResource(R.string.setup_hide_password) else stringResource(R.string.setup_show_password)
                        )
                    }
                },
                singleLine = true,
                enabled = loginState !is SetupViewModel.LoginState.Loading &&
                        loginState !is SetupViewModel.LoginState.CaptchaRequired &&
                        loginState !is SetupViewModel.LoginState.EmailVerificationRequired
            )

            Button(
                onClick = { submitLogin() },
                modifier = Modifier.fillMaxWidth(),
                enabled = loginState !is SetupViewModel.LoginState.Loading &&
                        loginState !is SetupViewModel.LoginState.CaptchaRequired &&
                        loginState !is SetupViewModel.LoginState.EmailVerificationRequired &&
                        email.isNotBlank() && password.isNotBlank()
            ) {
                if (loginState is SetupViewModel.LoginState.Loading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text(stringResource(R.string.setup_login_button))
            }

            if (loginState is SetupViewModel.LoginState.Error) {
                Text(
                    text = loginState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.setup_button_back))
        }
    }
}

@Composable
private fun CompletionPage(
    enabled: Boolean,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var animationComplete by remember { mutableStateOf(false) }

    val checkIconAlpha by animateFloatAsState(
        targetValue = if (animationComplete) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "checkIconAlpha"
    )

    val checkIconScale by animateFloatAsState(
        targetValue = if (animationComplete) 1f else 0.5f,
        animationSpec = tween(durationMillis = 300),
        label = "checkIconScale"
    )

    LaunchedEffect(enabled) {
        if (!enabled) {
            progress.snapTo(0f)
            animationComplete = false
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1500))
        animationComplete = true
        delay(500.milliseconds)
        onComplete()
    }

    val navBars = WindowInsets.navigationBars.asPaddingValues()
    val captionBar = WindowInsets.captionBar.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = navBars.calculateBottomPadding() + captionBar.calculateBottomPadding()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularWavyProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxSize()
                )
                if (animationComplete) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(48.dp)
                            .scale(checkIconScale)
                            .alpha(checkIconAlpha)
                    )
                }
            }

            Text(
                text = if (animationComplete) stringResource(R.string.setup_completion_complete) else stringResource(R.string.setup_completion_setting_up),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )

            if (animationComplete) {
                Text(
                    text = stringResource(R.string.setup_completion_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
