package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.api.RequiresWhitespaceCleanup

internal data class StandardV2Author(

    @RequiresWhitespaceCleanup
    @SerializedName("name")
    @NlsSafe
    val name: String?,

    @SerializedName("org")
    @NlsSafe
    val org: String?,

    @SerializedName("org_url")
    @NlsSafe
    val orgUrl: String?
)
