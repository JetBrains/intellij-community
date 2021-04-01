package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName

internal data class V2Repositories(

    @SerializedName("repositories")
    val repositories: List<V2Repository>
)
