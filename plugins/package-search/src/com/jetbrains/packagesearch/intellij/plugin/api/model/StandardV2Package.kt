package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.api.RequiresWhitespaceCleanup

data class StandardV2Package(

    @SerializedName("group_id")
    val groupId: String,

    @SerializedName("artifact_id")
    val artifactId: String,

    @NlsSafe
    @RequiresWhitespaceCleanup
    @SerializedName("name")
    val name: String,

    @NlsSafe
    @RequiresWhitespaceCleanup
    @SerializedName("description")
    val description: String?,

    @SerializedName("url")
    val url: String?,

    @SerializedName("licenses")
    val licenses: StandardV2Licenses?,

    @SerializedName("scm")
    val scm: StandardV2Scm?,

    @SerializedName("mpp")
    val mpp: StandardV2Mpp?,

    @SerializedName("platforms")
    val platforms: List<StandardV2Platform>?,

    @SerializedName("authors")
    val authors: List<StandardV2Author>?,

    @SerializedName("latest_version")
    val latestVersion: StandardV2Version,

    @SerializedName("versions")
    val versions: List<StandardV2Version>?,

    @SerializedName("dependency_rating")
    val dependencyRating: Double,

    @SerializedName("github")
    val gitHub: StandardV2GitHub?,

    @SerializedName("stackoverflow")
    val stackOverflowTags: StandardV2StackOverflowTags?
) {

    fun toSimpleIdentifier(): String = "$groupId:$artifactId".toLowerCase()
}
