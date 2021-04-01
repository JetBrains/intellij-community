package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.NlsSafe

internal data class StandardV2StackOverflowTags(

    @SerializedName("tags")
    val tags: List<StackOverflowTag>
)

internal data class StackOverflowTag(

    @SerializedName("tag")
    @NlsSafe
    val tag: String,

    @SerializedName("count")
    val count: Int
)
