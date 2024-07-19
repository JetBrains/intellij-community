// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.core

import com.intellij.ide.starters.local.GeneratorAsset
import com.intellij.ide.starters.local.GeneratorResourceFile

object KotlinAssetsProvider {
    fun getKotlinGradleIgnoreAssets(): List<GeneratorAsset> {
        return listOf(
            GeneratorResourceFile(
                relativePath = ".gitignore",
                resource = javaClass.getResource("/assets/ignore/kotlin.gradle.gitignore.txt") ?: error("kotlin.gradle.gitignore.txt not found")
            )
        )
    }

    fun getKotlinIgnoreAssets(): List<GeneratorAsset> {
        return listOf(
            GeneratorResourceFile(
                relativePath = ".gitignore",
                resource = javaClass.getResource("/assets/ignore/kotlin.gitignore.txt") ?: error("kotlin.gitignore.txt not found")
            )
        )
    }
}