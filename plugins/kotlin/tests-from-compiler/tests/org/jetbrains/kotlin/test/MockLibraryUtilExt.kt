// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused") // used at runtime

package org.jetbrains.kotlin.test

import org.jetbrains.kotlin.idea.base.test.JUnit4Assertions
import java.io.File

object MockLibraryUtilExt {
    @JvmStatic
    @JvmOverloads
    fun compileJvmLibraryToJar(
        sourcesPath: String,
        jarName: String,
        addSources: Boolean = false,
        allowKotlinSources: Boolean = true,
        extraOptions: List<String> = emptyList(),
        extraClasspath: List<String> = emptyList(),
        useJava11: Boolean = false,
    ): File {
        return MockLibraryUtil.compileJvmLibraryToJar(
            sourcesPath,
            jarName,
            addSources,
            allowKotlinSources,
            extraOptions,
            extraClasspath,
            extraModulepath = listOf(),
            useJava11,
            JUnit4Assertions,
        )
    }
}
