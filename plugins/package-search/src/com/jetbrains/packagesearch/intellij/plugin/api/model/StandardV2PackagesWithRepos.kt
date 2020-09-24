package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName

data class StandardV2PackagesWithRepos(

    @SerializedName("packages")
    val packages: List<StandardV2Package>?,

    @SerializedName("repositories")
    val repositories: List<V2Repository>?
)
