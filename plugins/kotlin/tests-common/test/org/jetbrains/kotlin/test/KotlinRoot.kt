/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.test

import com.intellij.openapi.application.PathManager
import java.io.File
import java.nio.file.Path

object KotlinRoot {
    @JvmField
    val REPO: File = File(PathManager.getHomePath()).canonicalFile

    @JvmField
    val DIR: File = REPO.resolve("community/plugins/kotlin").takeIf { it.exists() }
        ?: File(PathManager.getCommunityHomePath()).resolve("plugins/kotlin").canonicalFile

    @JvmField
    val DIR_PATH: Path = DIR.toPath()
}
