// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.env.PyEnvTestCase
import com.jetbrains.env.PyEnvTestSettings
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * Returns first CPython 3.x installed with gradle script to the dir with env variable (see [PyEnvTestSettings])
 */
@TestOnly
@RequiresBackgroundThread
fun getCPython3(): Result<Path> =
  PyEnvTestSettings
    .fromEnvVariables()
    .pythons
    .map { it.toPath() }
    .firstOrNull { isCPython3(it) }
    ?.let { Result.success(it) }
  ?: Result.failure(Throwable("No python found. See ${PyEnvTestSettings::class} class for more info"))


@RequiresBackgroundThread
private fun isCPython3(env: Path): Boolean =
  PyEnvTestCase.loadEnvTags(env.toString()).let {
    "conda" !in it && "python3" in it
  }
