package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName

data class StandardV2Scm(

    @SerializedName("url")
    val url: String?
)
