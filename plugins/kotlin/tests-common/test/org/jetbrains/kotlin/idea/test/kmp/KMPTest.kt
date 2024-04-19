// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.kmp

import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

interface KMPTest {
    val testPlatform: KMPTestPlatform

    fun setUp() {
        val platform = testPlatform
        if (platform.isSpecified) {
            Registry.get("kotlin.k2.kmp.enabled").setValue(true);
        }
    }

    companion object {
        fun withPlatformExtension(file: Path, platform: KMPTestPlatform): Path {
            if (platform == KMPTestPlatform.Unspecified) return file
            val withPlatformExtension =
                file.parent.resolve(file.nameWithoutExtension + "." + platform.testDataSuffix + "." + file.extension)
            if (withPlatformExtension.exists()) return withPlatformExtension
            return file
        }
    }
}

