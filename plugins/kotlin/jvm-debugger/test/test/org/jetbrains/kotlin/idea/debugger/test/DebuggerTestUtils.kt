// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:JvmName("DebuggerTestUtils")

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.jarRepository.RemoteRepositoryDescription
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.isStableOrReadyForPreview
import org.jetbrains.kotlin.idea.base.test.KotlinRoot

@JvmField
val DEBUGGER_TESTDATA_PATH_BASE: String =
    KotlinRoot.DIR.resolve("jvm-debugger").resolve("test").resolve("testData").path

internal fun chooseLanguageVersionForCompilation(useK2: Boolean): LanguageVersion {
    return if (useK2) {
        LanguageVersion.values().last { it.usesK2 && it.isStableOrReadyForPreview() }
    } else {
        LanguageVersion.KOTLIN_1_9 // the latest K1 LV
    }
}

val intellijDepsRepository = RemoteRepositoryDescription(
    "intellij-dependencies", "IntelliJ Dependencies",
    "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies"
)
