package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.NlsSafe

internal data class StandardV2LinkedFile(

    @SerializedName("name")
    @NlsSafe
    val name: String?,

    @SerializedName("url")
    @NlsSafe
    val url: String,

    @SerializedName("html_url")
    @NlsSafe
    val htmlUrl: String?,

    @SerializedName("spdx_id")
    @NlsSafe
    val spdxId: String?,

    @SerializedName("key")
    @NlsSafe
    val key: String?
)
