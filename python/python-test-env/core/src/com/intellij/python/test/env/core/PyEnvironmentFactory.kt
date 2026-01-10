// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.test.env.core

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyEnvironmentFactory: AutoCloseable {

  suspend fun createEnvironment(spec: PyEnvironmentSpec<*>): PyEnvironment {
    return createEnvironment(this, spec)
  }

  suspend fun createEnvironment(factory: PyEnvironmentFactory, spec: PyEnvironmentSpec<*>): PyEnvironment

}