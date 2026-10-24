package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/** `POST /api/stem-separation`'s 202 body. */
data class StemJobResponse(@SerializedName("job_id") val jobId: String = "")

/** `GET /api/stem-separation/status`. `status` is "pending", "running", "done", or "error". */
data class StemStatusResponse(val status: String = "pending", val error: String? = null)
