// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.tools

import com.intellij.openapi.diagnostic.fileLogger
import com.jetbrains.python.sdk.Directory
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.readLines

private const val TAGS_FILE = "tags.txt"

/**
 * Loads [TAGS_FILE] from [pythonEnv] which should be a path to a python installation (either binary or directory) created by
 * `community/python/setup-test-environment`.
 *
 * Returns set of tags i.e. `django` if interpreter has django.
 */
fun loadEnvTags(pythonEnv: Path): Set<String> {
  val envDir: Directory = if (pythonEnv.isDirectory()) pythonEnv else pythonEnv.parent
  val tagsFile = envDir.resolve(TAGS_FILE)
  try {
    return tagsFile.readLines().toSet()
  }
  catch (_: IOException) {
    fileLogger().warn("Can't read $tagsFile")
    return emptySet()
  }
}
