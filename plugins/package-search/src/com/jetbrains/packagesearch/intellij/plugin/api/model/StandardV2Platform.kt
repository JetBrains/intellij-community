package com.jetbrains.packagesearch.intellij.plugin.api.model

import com.google.gson.annotations.SerializedName
import com.jetbrains.packagesearch.intellij.plugin.gson.DeserializationFallback

// Note: any parameter that is typed as an enum class and deserialized with Gson must be nullable
internal data class StandardV2Platform(

    @SerializedName("type")
    val type: PlatformType,

    @SerializedName("targets")
    val targets: List<PlatformTarget>?
)

internal enum class PlatformType {
    @SerializedName("js") JS,
    @SerializedName("jvm") JVM,
    @SerializedName("common") COMMON,
    @SerializedName("native") NATIVE,
    @SerializedName("androidJvm") ANDROID_JVM,
    @DeserializationFallback UNSUPPORTED
}

internal enum class PlatformTarget {

    // *********** Kotlin/JS targets ***********
    @SerializedName("node") NODE,
    @SerializedName("browser") BROWSER,

    // *********** Kotlin/Native targets ***********
    // See the org.jetbrains.kotlin.konan.target.KonanTarget class in the Kotlin code
    @SerializedName("android_x64") ANDROID_X64,
    @SerializedName("android_x86") ANDROID_X86,
    @SerializedName("android_arm32") ANDROID_ARM32,
    @SerializedName("android_arm64") ANDROID_ARM64,
    @SerializedName("ios_arm32") IOS_ARM32,
    @SerializedName("ios_arm64") IOS_ARM64,
    @SerializedName("ios_x64") IOS_X64,
    @SerializedName("watchos_arm32") WATCHOS_ARM32,
    @SerializedName("watchos_arm64") WATCHOS_ARM64,
    @SerializedName("watchos_x86") WATCHOS_X86,
    @SerializedName("watchos_x64") WATCHOS_X64,
    @SerializedName("tvos_arm64") TVOS_ARM64,
    @SerializedName("tvos_x64") TVOS_X64,
    @SerializedName("linux_x64") LINUX_X64,
    @SerializedName("mingw_x86") MINGW_X86,
    @SerializedName("mingw_x64") MINGW_X64,
    @SerializedName("macos_x64") MACOS_X64,
    @SerializedName("macos_arm64") MACOS_ARM64,
    @SerializedName("linux_arm64") LINUX_ARM64,
    @SerializedName("linux_arm32_hfp") LINUX_ARM32_HFP,
    @SerializedName("linux_mips32") LINUX_MIPS32,
    @SerializedName("linux_mipsel32") LINUX_MIPSEL32,
    @SerializedName("wasm32") WASM_32,

    @DeserializationFallback UNSUPPORTED
}
