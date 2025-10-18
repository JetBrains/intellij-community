// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.sdk.add.v2.*
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import kotlinx.coroutines.CoroutineScope

class UvState<P : PathHolder>(
  fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
) : ToolState {
  val uvExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "uv",
    backProperty = uvExecutable,
    propertyGraph = propertyGraph,
    defaultPathSupplier = {
      when (fileSystem) {
        is FileSystem.Eel -> {
          if (fileSystem.eelApi == localEel) getUvExecutable()?.let { PathHolder.Eel(it) } as P?
          else null // getUvExecutable() works only with localEel currently
        }
        else -> null
      }
    }
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)
  }
}