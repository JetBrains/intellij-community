// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl.providers

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.venvReader.Directory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileSystemNotFoundException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.listDirectoryEntries


internal val pypyNamePattern: Pattern = Pattern.compile("pypy$")
internal val python3NamePattern: Pattern = Pattern.compile("python3$")
internal val python3XNamePattern: Pattern = Pattern.compile("python3\\.[0-9]+$")

internal fun useLegacyPythonProvider(): Boolean {
  return Registry.`is`("python.use.system.legacy.provider")
}

internal suspend fun collectPythonsInPaths(paths: List<Directory>, names: List<Pattern>): Set<Path> =
  withContext(Dispatchers.IO) {
    paths
      .flatMap {
        try {
          it.listDirectoryEntries()
        }
        catch (_: NotDirectoryException) {
          emptyList()
        }
        catch (_: NoSuchFileException) { // Directory can't be read or doesn't exist
          emptyList()
        }
        catch (e: FileSystemNotFoundException) {
          // This is a temporary hack: Eel might throw this exception in tests when fs gets deregistered
          // it will be removed as soon as we arrange it with eel
          logger.warn("Path $it filesystem is inaccessible", e)
          emptyList()
        }
        catch (e: IOException) {
          logger.warn("Path $it is inaccessible", e)
          emptyList()
        }
      }
      .filter { child -> names.any { it.matcher(child.fileName.toString()).matches() } }.toSet()
  }

private val logger = fileLogger()