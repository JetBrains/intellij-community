// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyRunnerUtil")

package com.jetbrains.python.run

import com.intellij.execution.configurations.RunProfileState
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal fun RunProfileState.getModule(): Module? = (this as? PythonCommandLineState)?.config?.module