package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName

data class StandardV2StackOverflowTags(

    @SerializedName("tags")
    val tags: List<StackOverflowTag>
)

data class StackOverflowTag(

    @SerializedName("tag")
    val tag: String,

    @SerializedName("count")
    val count: Int
)
