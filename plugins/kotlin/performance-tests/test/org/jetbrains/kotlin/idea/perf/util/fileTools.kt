// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.perf.util

import com.intellij.util.io.exists
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

fun String.lastPathSegment() =
    Paths.get(this).last().toString()

fun exists(path: String, vararg paths: String) =
    Paths.get(path, *paths).toAbsolutePath().exists()

fun Path.copyRecursively(targetDirectory: Path) {
    val src = this
    Files.walk(src)
        .forEach { source -> Files.copy(source, targetDirectory.resolve(src.relativize(source)), StandardCopyOption.REPLACE_EXISTING) }
}

fun File.allFilesWithExtension(ext: String): Iterable<File> =
    walk().filter { it.isFile && it.extension.equals(ext, ignoreCase = true) }.toList()

fun runGit(vararg extraArgs: String): String? {
    val gitPath = System.getenv("TEAMCITY_GIT_PATH") ?: "git"
    val args = listOf(gitPath) + extraArgs
    val processBuilder = ProcessBuilder(*args.toTypedArray())
    val process = processBuilder.start()
    var line: String?
    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
        line = reader.readLine()
    }
    var value: String? = null
    if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
        value = line
    }
    return value
}
