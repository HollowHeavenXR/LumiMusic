package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/** One GIPHY result, as simplified by the bridge's `/api/gif-search*` endpoints. */
data class GifSearchResult(
    val id: String = "",
    val description: String = "",
    @SerializedName("preview_url") val previewUrl: String? = null,
    @SerializedName("gif_url") val gifUrl: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

data class GifSearchResponse(val results: List<GifSearchResult> = emptyList())
