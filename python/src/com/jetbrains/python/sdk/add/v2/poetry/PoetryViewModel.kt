// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.poetry

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonToolViewModel
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import kotlinx.coroutines.CoroutineScope

class PoetryViewModel<P : PathHolder>(
  fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
) : PythonToolViewModel {
  val poetryExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "poetry",
    backProperty = poetryExecutable,
    propertyGraph = propertyGraph,
    defaultPathSupplier = { getPoetryExecutable(fileSystem) }
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)
  }
}