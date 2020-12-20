package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName

data class StandardV2Version(

    @SerializedName("version")
    val version: String,

    @SerializedName("last_changed")
    val lastChanged: Long?,

    @SerializedName("stable")
    val stable: Boolean?,

    @SerializedName("repository_ids")
    val repositoryIds: List<String>?,

    @SerializedName("artifacts")
    val artifacts: List<StandardV2Artifact>?
)

data class StandardV2Artifact(

    @SerializedName("sha1")
    val sha1: String?,

    @SerializedName("sha256")
    val sha256: String?,

    @SerializedName("md5")
    val md5: String?,

    @SerializedName("packaging")
    val packaging: String?,

    @SerializedName("classifier")
    val classifier: String?
)
