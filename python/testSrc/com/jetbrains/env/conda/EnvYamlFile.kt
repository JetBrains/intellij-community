// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.conda

import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * See content of [yamlFile]
 */
internal const val yamlEnvName = "envFromFile"
internal val yamlFile: Path
  get() = File(PyCondaTest::class.java.classLoader.getResource("com/jetbrains/env/conda/environment.yml")!!.path).toPath().also {
    assert(it.exists()) { "Can't file environment file in tests" }
  }