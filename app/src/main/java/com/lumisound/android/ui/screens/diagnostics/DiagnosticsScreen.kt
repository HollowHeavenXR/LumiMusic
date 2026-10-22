package com.lumisound.android.ui.screens.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumisound.android.AppContainer
import com.lumisound.android.BuildConfig
import com.lumisound.android.bridge.api.BugReportBody
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.ui.components.ScreenTitle
import com.lumisound.android.ui.components.SettingsGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the app knows about itself, on screen.
 *
 * This is the other half of the telemetry story: the server-side snapshots answer
 * "what was this session doing" after the fact, and this answers it while the person
 * holding the phone is still looking at the problem. Same data, no round trip -- and
 * a bug report from here carries the log tail with it, so a report never arrives as
 * just "it didn't work".
 */
@Composable
fun DiagnosticsScreen(container: AppContainer, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by container.diagnostics.lastSnapshot.collectAsStateWithLifecycle()
    val playback by container.player.state.collectAsStateWithLifecycle()
    var logText by remember { mutableStateOf(AppLogger.asText(200)) }
    var reportText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }

    // The log is a ring buffer, not a flow; polling it is both simpler and cheaper
    // than making every log call recompose a screen that is usually closed.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_500)
            logText = AppLogger.asText(200)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle(
            "Diagnostics",
            subtitle = "Live state, logs, and a way to report a problem",
            eyebrow = "Support",
            onBack = onBack,
        )

        Spacer(Modifier.height(10.dp))
        SettingsGroup("Build") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    "LumiMusic ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                        "${if (BuildConfig.DEBUG) "debug" else "release"}\n" +
                        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsGroup("Live state") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    buildString {
                        appendLine("player connected: ${playback.connected}")
                        appendLine("playing: ${playback.isPlaying} · queue ${playback.queueIndex + 1}/${playback.queueSize}")
                        appendLine("source: ${if (playback.isLocalSource) "device file" else "bridge stream"}")
                        playback.playbackError?.let { appendLine("player error: $it") }
                        appendLine("errors logged: ${AppLogger.errorCount()}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsGroup("HTTP routes") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                val routes = container.httpMetrics.snapshot()
                if (routes.isEmpty()) {
                    Text("No requests yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        routes.entries.sortedByDescending { it.value.failures }.forEach { (route, stat) ->
                            Text(
                                "%-42s %4d calls  %3d fail  last %s  avg %4dms  max %5dms".format(
                                    route.take(42), stat.calls, stat.failures, stat.lastStatus ?: "-", stat.averageMs, stat.slowestMs,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (stat.failures > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        container.diagnostics.sendNow("manual")
                        container.telemetry.flushNow()
                        note = "Snapshot and log batch sent."
                    }) { Text("Send diagnostics") }
                    OutlinedButton(onClick = {
                        copyToClipboard(context, "LumiMusic diagnostics", diagnosticsText(snapshot, logText))
                        note = "Copied to clipboard."
                    }) { Text("Copy report") }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsGroup("Report a problem") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    "Sent to the bridge with the last 200 log lines attached.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = reportText,
                    onValueChange = { reportText = it },
                    label = { Text("What happened?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = reportText.isNotBlank(),
                    onClick = {
                        val description = reportText
                        reportText = ""
                        scope.launch {
                            note = try {
                                container.http.diagnostics.bugReport(
                                    BugReportBody(
                                        category = "android",
                                        description = description,
                                        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                        deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}",
                                        recentLogs = AppLogger.asText(200).take(20_000),
                                    )
                                )
                                "Report sent. Thanks."
                            } catch (e: Exception) {
                                "Could not send the report: ${e.javaClass.simpleName}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Send report") }

                note?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsGroup("Log") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Last 200 lines",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        copyToClipboard(context, "LumiMusic log", logText)
                        note = "Log copied."
                    }) { Text("Copy log") }
                }
                LogTail(logText)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LogTail(text: String) {
    val lines = remember(text) { text.lines().reversed() }
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        items(lines) { line ->
            Text(
                line,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = when {
                    " ERROR " in line || " FATAL " in line -> MaterialTheme.colorScheme.error
                    " WARN " in line -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun diagnosticsText(snapshot: Map<String, Any?>, logText: String): String = buildString {
    appendLine("LumiMusic ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
    appendLine()
    appendLine("--- last snapshot ---")
    snapshot.forEach { (key, value) -> appendLine("$key: $value") }
    appendLine()
    appendLine("--- log ---")
    append(logText)
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
