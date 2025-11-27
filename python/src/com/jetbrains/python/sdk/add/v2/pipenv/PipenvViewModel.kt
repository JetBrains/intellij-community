// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.pipenv

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.platform.eel.provider.localEel
import com.jetbrains.python.getOrNull
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonToolViewModel
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.pipenv.getPipEnvExecutable
import kotlinx.coroutines.CoroutineScope

class PipenvViewModel<P : PathHolder>(
  fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
) : PythonToolViewModel {
  val pipenvExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "pipenv",
    backProperty = pipenvExecutable,
    propertyGraph = propertyGraph,
    defaultPathSupplier = {
      when (fileSystem) {
        is FileSystem.Eel -> {
          if (fileSystem.eelApi == localEel) getPipEnvExecutable()?.let { PathHolder.Eel(it) } as P?
          else null // getPipEnvExecutable() works only with localEel currently
        }
        else -> null
      }
    }
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)
  }
}