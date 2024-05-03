// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.kmp

import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms

enum class KMPTestPlatform(
    val directiveName: String,
    val testDataSuffix: String?,
    val isSpecified: Boolean = true,
) {
    Jvm(directiveName = "JVM", testDataSuffix = null) {
        override val targetPlatform
            get(): TargetPlatform = JvmPlatforms.jvm8
    },
    Js(directiveName = "JS", testDataSuffix = "js") {
        override val targetPlatform
            get(): TargetPlatform = JsPlatforms.defaultJsPlatform
    },
    NativeLinux(directiveName = "NATIVE", testDataSuffix = "native") {
        override val targetPlatform
            get(): TargetPlatform = NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64)
    },
    CommonNativeJvm(directiveName = "COMMON_NATIVE+JVM", testDataSuffix = "common_nj") {
        override val targetPlatform
            get(): TargetPlatform = TargetPlatform(
                NativePlatforms.nativePlatformBySingleTarget(KonanTarget.LINUX_X64).componentPlatforms +
                        JvmPlatforms.unspecifiedJvmPlatform.componentPlatforms
            )
    },
    Unspecified(directiveName = "UNSPECIFIED", testDataSuffix = null, isSpecified = false) {
        override val targetPlatform
            get(): TargetPlatform = error("Cannot get a ${TargetPlatform::class.java} for ${Unspecified::class}")
    },

    ;

    abstract val targetPlatform: TargetPlatform


    companion object {
        val ALL_SPECIFIED: List<KMPTestPlatform>
            get() = entries.filter(KMPTestPlatform::isSpecified)
    }
}
