package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.jetbrains.packagesearch.intellij.plugin.gson.DeserializationFallback

// Note: any parameter that is typed as an enum class and deserialized with Gson must be nullable
data class V2Repository(

    @SerializedName("id")
    val id: String,

    @SerializedName("url")
    val url: String,

    @SerializedName("type")
    val type: V2RepositoryType?,

    @SerializedName("alternate_urls")
    val alternateUrls: List<String>?,

    @SerializedName("friendly_name")
    val friendlyName: String
)

enum class V2RepositoryType {

    @SerializedName("maven")
    MAVEN,

    @DeserializationFallback
    UNSUPPORTED
}
