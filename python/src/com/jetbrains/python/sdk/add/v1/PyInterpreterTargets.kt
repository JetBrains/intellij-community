// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory

internal val TargetEnvironmentConfiguration?.isMutableTarget: Boolean
  get() = this?.let { PythonInterpreterTargetEnvironmentFactory.isMutable(it) } ?: true