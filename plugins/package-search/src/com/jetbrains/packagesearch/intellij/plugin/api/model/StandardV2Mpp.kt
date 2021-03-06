package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.jetbrains.packagesearch.intellij.plugin.gson.DeserializationFallback

// Note: any parameter that is typed as an enum class and deserialized with Gson must be nullable
data class StandardV2Mpp(

    @SerializedName("module_type")
    val moduleType: MppModuleType?
)

enum class MppModuleType {

    @SerializedName("root")
    ROOT,

    @SerializedName("child")
    CHILD,

    @DeserializationFallback
    UNKNOWN
}
