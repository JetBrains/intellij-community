package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.jetbrains.packagesearch.intellij.plugin.api.RequiresWhitespaceCleanup

data class StandardV2Author(

    @RequiresWhitespaceCleanup
    @SerializedName("name")
    val name: String?,

    @SerializedName("org")
    val org: String?,

    @SerializedName("org_url")
    val orgUrl: String?
)
