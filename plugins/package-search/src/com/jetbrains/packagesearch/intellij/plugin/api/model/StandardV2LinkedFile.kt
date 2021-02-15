package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.NonNls

data class StandardV2LinkedFile(

    @NlsSafe
    @SerializedName("name")
    val name: String?,

    @NlsSafe
    @SerializedName("url")
    val url: String?,

    @NlsSafe
    @SerializedName("html_url")
    val htmlUrl: String?,

    @NonNls
    @SerializedName("spdx_id")
    val spdxId: String?,

    @NonNls
    @SerializedName("key")
    val key: String?
)
