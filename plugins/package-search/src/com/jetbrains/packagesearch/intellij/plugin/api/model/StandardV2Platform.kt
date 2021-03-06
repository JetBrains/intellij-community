package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.jetbrains.packagesearch.intellij.plugin.gson.DeserializationFallback

// Note: any parameter that is typed as an enum class and deserialized with Gson must be nullable
data class StandardV2Platform(

    @SerializedName("type")
    val type: PlatformType?,

    @SerializedName("targets")
    val targets: List<PlatformTarget>?
)

enum class PlatformType {

    @SerializedName("js")
    JS,

    @SerializedName("jvm")
    JVM,

    @SerializedName("common")
    COMMON,

    @SerializedName("native")
    NATIVE,

    @DeserializationFallback
    UNSUPPORTED
}

enum class PlatformTarget {

    @SerializedName("node")
    NODE,

    @SerializedName("browser")
    BROWSER,

    @SerializedName("ios_arm32")
    IOS_ARM32,

    @SerializedName("ios_arm64")
    IOS_ARM64,

    @SerializedName("ios_x64")
    IOS_X64,

    @SerializedName("linux_x64")
    LINUX_X64,

    @SerializedName("macos_x64")
    MACOS_X64,

    @SerializedName("mingw_x64")
    MINGW_X64,

    @SerializedName("tvos_arm64")
    TVOS_ARM64,

    @SerializedName("tvos_x64")
    TVOS_X64,

    @SerializedName("watchos_arm32")
    WATCHOS_ARM32,

    @SerializedName("watchos_arm64")
    WATCHOS_ARM64,

    @SerializedName("watchos_x86")
    WATCHOS_X64,

    @DeserializationFallback
    UNSUPPORTED
}
