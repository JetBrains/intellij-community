// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.observable.properties.ObservableProperty
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

/**
 * Params used by [PythonAddInterpreterModel] and inheritors
 */
@ApiStatus.Internal
data class PyInterpreterModelParams(
  val scope: CoroutineScope,
  val uiContext: CoroutineContext,
  val projectPathProperty: ObservableProperty<String>? = null,
)