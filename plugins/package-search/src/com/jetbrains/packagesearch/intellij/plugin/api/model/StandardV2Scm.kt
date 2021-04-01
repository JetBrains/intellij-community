package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.NlsSafe

internal data class StandardV2Scm(

    @SerializedName("url")
    @NlsSafe
    val url: String
)
