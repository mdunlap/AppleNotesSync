package com.applenotesync.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.applenotesync.app.data.ServerState

private const val PREFS_NAME = "apple_notes_sync"
private const val KEY_SERVER_URL = "server_url"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: NotesViewModel = viewModel(),
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var serverUrl by remember { mutableStateOf(prefs.getString(KEY_SERVER_URL, "") ?: "") }
    var saved by remember { mutableStateOf(false) }
    val discoveryState by viewModel.serverDiscovery.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // mDNS Discovery status
            Text("Auto-Discovery", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            when (val state = discoveryState) {
                is ServerState.Searching -> {
                    Text(
                        "Searching for server on local network...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ServerState.Found -> {
                    Text(
                        "Server found: http://${state.host}:${state.port}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is ServerState.Error -> {
                    Text(
                        "Discovery error: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            Text("Manual Server URL", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    saved = false
                },
                label = { Text("e.g. http://192.168.x.x:8642") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    prefs.edit().putString(KEY_SERVER_URL, serverUrl).apply()
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saved) "Saved!" else "Save")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Enter the URL of your Mac running the Apple Notes Sync server. " +
                    "If left blank, the app will use the auto-discovered server. " +
                    "Both devices must be on the same network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun getServerUrl(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_SERVER_URL, null) ?: ""
}
