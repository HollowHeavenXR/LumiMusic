package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/** One member of a [AcousticDuplicatesResponse] group -- a bare filename, not a full path (see server-side comment). */
data class DuplicateFileDto(val file: String = "", val duration: Double = 0.0)

/** `GET /user/library/acoustic-duplicates` -- files that are the same recording acoustically, regardless of metadata. */
data class AcousticDuplicatesResponse(
    val scanned: Int = 0,
    @SerializedName("duplicate_groups") val duplicateGroups: List<List<DuplicateFileDto>> = emptyList(),
    val cached: Boolean = false,
    @SerializedName("scanned_at") val scannedAt: String? = null,
)
