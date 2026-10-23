package com.lumisound.android.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-folder custom cover art for the device library's Folders view -- iOS
 * parity (`FolderCoverArtService`): picking a photo stands in for a folder
 * instead of the generic folder glyph. Device-local only, no bridge
 * involvement, same reasoning as iOS's own doc comment on that class --
 * there's no cross-device "source of truth" for which photo a LOCAL folder
 * (whose underlying files aren't shared across devices either) should show.
 *
 * Keyed by the folder's own name (what `LocalTrackEntity.folder` and the
 * Folders grouping already use as identity) rather than a full path --
 * simpler than iOS's path-hashing scheme since this app has no sandbox-
 * container-UUID problem to hash around in the first place.
 */
class FolderCoverArtStore(context: Context) {
    private val dir = File(context.filesDir, "folder_covers").apply { mkdirs() }
    private val contentResolver = context.contentResolver

    /** Bumped on every change -- lets a folder row re-check without a Flow per folder. */
    private val _changeCount = MutableStateFlow(0)
    val changeCount: StateFlow<Int> = _changeCount.asStateFlow()

    private fun key(folderName: String): String =
        MessageDigest.getInstance("SHA-256").digest(folderName.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun file(folderName: String) = File(dir, "${key(folderName)}.jpg")

    /** Suitable as Coil's `model` directly (a bare path string is ambiguous to it) -- `null` means "use the default". */
    fun cover(folderName: String): File? = file(folderName).takeIf { it.exists() }

    fun setCover(folderName: String, uri: Uri): Boolean {
        val bitmap = try {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        } ?: return false
        return try {
            file(folderName).outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            _changeCount.value++
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeCover(folderName: String) {
        if (file(folderName).delete()) _changeCount.value++
    }
}
