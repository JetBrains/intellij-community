// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.kmp

enum class KMPTestPlatform(
    val directiveName: String,
    val testDataSuffix: String?,
    val isSpecified: Boolean = true
) {
    Jvm(directiveName = "JVM", testDataSuffix = null),
    Js(directiveName = "JS", testDataSuffix = "js"),
    NativeLinux(directiveName = "NATIVE", testDataSuffix = "native"),
    CommonNativeJvm(directiveName = "COMMON_NATIVE+JVM", testDataSuffix = "common_nj"),
    Unspecified(directiveName = "UNSPECIFIED", testDataSuffix = null, isSpecified = false),

    ;

    companion object {
        val ALL_SPECIFIED: List<KMPTestPlatform>
            get() = entries.filter(KMPTestPlatform::isSpecified)
    }
}