// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.util

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import java.io.File

fun VirtualFile.compileScriptsIntoDirectory(scripts: List<File>, additionalOptions: List<String> = emptyList()) {
    if (!isDirectory) {
        error("Directory is expected on the path: $path")
    }

    val outputDirectory = VfsUtil.virtualToIoFile(this)
    val optionsSet = setOf(
        "-script", "-Xdefault-script-extension=kts", "-d", outputDirectory.absolutePath
    ) + additionalOptions

    KotlinCompilerStandalone(
        scripts,
        target = outputDirectory,
        classpath = listOf(
            TestKotlinArtifacts.kotlinScriptRuntime,
            TestKotlinArtifacts.kotlinScriptingJvm,
            TestKotlinArtifacts.kotlinScriptingCommon
        ),
        options = optionsSet.toList()
    ).compile()
}