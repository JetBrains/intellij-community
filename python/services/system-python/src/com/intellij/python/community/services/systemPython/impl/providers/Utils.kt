// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl.providers

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.fs.EelFileSystemApi.StatError
import com.intellij.platform.eel.fs.stat
import com.intellij.platform.eel.getOrNull
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.collections.map
import kotlin.io.path.pathString


internal val pypyNamePattern: Pattern = Pattern.compile("pypy$")
internal val python3NamePattern: Pattern = Pattern.compile("python3$")
internal val python3XNamePattern: Pattern = Pattern.compile("python3\\.[0-9]+$")

internal fun useLegacyPythonProvider(): Boolean {
  return Registry.`is`("python.use.system.legacy.provider")
}

internal suspend fun collectPythonsInPaths(eelApi: EelApi, paths: List<Path>, names: List<Pattern>): Set<Path> {
  val pythons = mutableSetOf<Path>()

  for (path in paths) {
    val directory = EelPath.parse(path.pathString, eelApi.descriptor)
    if (eelApi.fs.stat(directory).eelIt() is StatError) {
      continue
    }

    val entries = eelApi.fs.listDirectory(directory)
      .getOrNull()

    entries
      ?.map { directory.resolve(it).asNioPath() }
      ?.filter { names.firstOrNull { name -> name.matcher(it.fileName.toString()).matches() } != null }
      ?.let { pythons.addAll(it) }
  }

  return pythons
}