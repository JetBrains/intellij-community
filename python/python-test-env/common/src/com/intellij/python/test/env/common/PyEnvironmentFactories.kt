// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.common

import com.intellij.python.test.env.core.CachingPyEnvironmentFactory
import com.intellij.python.test.env.core.DefaultPyEnvironmentFactory
import com.intellij.python.test.env.core.PyEnvironmentSpec
import com.intellij.python.test.env.conda.CondaPyEnvironmentProvider
import com.intellij.python.test.env.conda.CondaPyEnvironmentSpec
import com.intellij.python.test.env.plain.PlainPyEnvironmentProvider
import com.intellij.python.test.env.plain.PlainPyEnvironmentSpec
import com.intellij.python.test.env.plain.VirtualenvPyEnvironmentProvider
import com.intellij.python.test.env.plain.VirtualenvPyEnvironmentSpec
import com.intellij.python.test.env.uv.UvPyEnvironmentProvider
import com.intellij.python.test.env.uv.UvPyEnvironmentSpec
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi

@OptIn(ExperimentalPathApi::class)
fun createPyEnvironmentFactory(): CachingPyEnvironmentFactory {
  val workingDir = Files.createTempDirectory("pytest_java_" + System.nanoTime() + "_")
  val cachingPyEnvironmentFactory = CachingPyEnvironmentFactory(
    DefaultPyEnvironmentFactory(
      workingDir,
      listOf(
        { spec: PyEnvironmentSpec<*> -> spec is PlainPyEnvironmentSpec } to PlainPyEnvironmentProvider(),
        { spec: PyEnvironmentSpec<*> -> spec is VirtualenvPyEnvironmentSpec } to VirtualenvPyEnvironmentProvider(),
        { spec: PyEnvironmentSpec<*> -> spec is UvPyEnvironmentSpec } to UvPyEnvironmentProvider(),
        { spec: PyEnvironmentSpec<*> -> spec is CondaPyEnvironmentSpec } to CondaPyEnvironmentProvider(),
      )
    )
  )
  return cachingPyEnvironmentFactory
}
