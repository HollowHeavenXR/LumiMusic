package com.lumisound.android.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lumisound.android.bridge.BridgeHttp
import com.lumisound.android.bridge.model.GifSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private enum class AvatarPickerMode { CHOOSE, GIF_SEARCH }

/**
 * Change the account avatar from a device photo or a GIF -- iOS parity
 * (`GifPickerSheet`/`GifCropView`), minus the manual crop step: the bridge
 * accepts raw JPEG/GIF bytes unmodified (max 15MB either way) and every
 * place this app shows an avatar already center-crops to a circle at
 * render time, so there's no information lost by skipping a crop UI --
 * the same bytes are stored, just visually cropped on display instead of
 * before upload.
 */
@Composable
fun AvatarPickerSheet(http: BridgeHttp, onDismiss: () -> Unit, onUploaded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(AvatarPickerMode.CHOOSE) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun upload(bytes: ByteArray, mediaType: String) {
        uploading = true
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { http.auth.uploadAvatar(bytes.toRequestBody(mediaType.toMediaType())) }
                uploading = false
                onUploaded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uploading = false
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        error = null
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        ?: throw IllegalStateException("Could not read that photo")
                    ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, this) }.toByteArray()
                }
                upload(bytes, "image/jpeg")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uploading = false
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!uploading) onDismiss() },
        title = { Text(if (mode == AvatarPickerMode.GIF_SEARCH) "Choose a GIF" else "Change avatar") },
        text = {
            Column {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                }
                if (uploading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else when (mode) {
                    AvatarPickerMode.CHOOSE -> {
                        PickerOptionRow(Icons.Filled.Photo, "Choose a photo") {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Spacer(Modifier.height(4.dp))
                        PickerOptionRow(Icons.Filled.Gif, "Choose a GIF") { mode = AvatarPickerMode.GIF_SEARCH }
                    }
                    AvatarPickerMode.GIF_SEARCH -> GifSearchGrid(
                        http = http,
                        onPick = { gif ->
                            scope.launch {
                                uploading = true
                                error = null
                                try {
                                    val bytes = withContext(Dispatchers.IO) {
                                        http.client.newCall(Request.Builder().url(gif.gifUrl).build()).execute().use { response ->
                                            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                                            response.body?.bytes() ?: throw IllegalStateException("Empty response")
                                        }
                                    }
                                    upload(bytes, "image/gif")
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    uploading = false
                                    error = e.message ?: e.javaClass.simpleName
                                }
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(enabled = !uploading, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickerOptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Trending on open, debounced search as the user types -- same shape as `GifPickerSheet` on iOS. */
@Composable
private fun GifSearchGrid(http: BridgeHttp, onPick: (GifSearchResult) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GifSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(400)
        loading = true
        results = try {
            if (query.isBlank()) http.gif.trending().results else http.gif.search(query).results
        } catch (e: Exception) {
            emptyList()
        }
        loading = false
    }

    Column {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search GIFs") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        if (loading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (results.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No GIFs found.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(320.dp)) {
                items(results, key = { it.id }) { gif ->
                    AsyncImage(
                        model = gif.previewUrl ?: gif.gifUrl,
                        contentDescription = gif.description,
                        modifier = Modifier
                            .padding(3.dp)
                            .size(96.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPick(gif) },
                    )
                }
            }
        }
    }
}
