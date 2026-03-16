// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.FolderValidator
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonToolViewModel
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.uv.impl.getUvExecutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class UvViewModel<P : PathHolder>(
  fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
  projectPathFlows: ProjectPathFlows,
) : PythonToolViewModel {
  val uvExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)
  val uvVenvPath: ObservableMutableProperty<ValidatedPath.Folder<P>?> = propertyGraph.property(null)

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    toolVersionPrefix = "uv",
    backProperty = uvExecutable,
    propertyGraph = propertyGraph,
    defaultPathSupplier = {
      getUvExecutable(fileSystem, null)
    }
  )

  val uvVenvValidator: FolderValidator<P> = FolderValidator(
    fileSystem = fileSystem,
    backProperty = uvVenvPath,
    propertyGraph = propertyGraph,
    defaultPathSupplier = {
      val projectPath = projectPathFlows.projectPathWithDefault.first()
      fileSystem.suggestVenv(projectPath)
    },
    pathValidator = fileSystem::validateVenv
  )

  override fun initialize(scope: CoroutineScope) {
    toolValidator.initialize(scope)
    uvVenvValidator.initialize(scope)
  }
}
