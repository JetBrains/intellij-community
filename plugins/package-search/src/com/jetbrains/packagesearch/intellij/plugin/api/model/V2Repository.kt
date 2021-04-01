package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.packagesearch.intellij.plugin.gson.DeserializationFallback

// Note: any parameter that is typed as an enum class and deserialized with Gson must be nullable
internal data class V2Repository(

    @SerializedName("id")
    @NlsSafe
    val id: String,

    @SerializedName("url")
    @NlsSafe
    val url: String?,

    @SerializedName("type")
    val type: V2RepositoryType?,

    @SerializedName("alternate_urls")
    @NlsSafe
    val alternateUrls: List<String>?,

    @SerializedName("friendly_name")
    @NlsSafe
    val friendlyName: String?
)

internal enum class V2RepositoryType {

    @SerializedName("maven")
    MAVEN,

    @DeserializationFallback
    UNSUPPORTED
}
