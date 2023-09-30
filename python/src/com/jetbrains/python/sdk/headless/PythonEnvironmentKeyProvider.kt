// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.headless

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.openapi.project.Project
import com.jetbrains.python.PyBundle
import java.util.function.Supplier

class PythonEnvironmentKeyProvider : EnvironmentKeyProvider {
  object Keys {
    val sdkKey: EnvironmentKey = EnvironmentKey.create("python.interpreter.path")
  }

  override val knownKeys: Map<EnvironmentKey, Supplier<String>> =
    mapOf(Keys.sdkKey to PyBundle.messagePointer("environment.key.description.python.interpreter.path"))

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> = emptyList()
}