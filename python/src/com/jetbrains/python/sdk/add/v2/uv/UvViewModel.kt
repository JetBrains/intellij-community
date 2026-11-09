// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.FolderValidator
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonToolViewModel
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.uv.UvMode
import com.jetbrains.python.sdk.uv.hasPyProjectToml
import com.intellij.python.uv.backend.UvPyTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal class UvViewModel<P : PathHolder>(
  fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
  private val projectPathFlows: ProjectPathFlows<*>,
) : PythonToolViewModel {
  val uvExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = propertyGraph.property(null)
  val uvVenvPath: ObservableMutableProperty<ValidatedPath.Folder<P>?> = propertyGraph.property(null)
  val inheritSitePackages: GraphProperty<Boolean> = propertyGraph.property(false)

  /** The box "Use pyproject.toml (uv init)". [initialize] selects the box when the project directory holds a `pyproject.toml`. */
  val projectMode: GraphProperty<Boolean> = propertyGraph.property(true)

  /** The mode that [projectMode] selects. */
  val mode: UvMode
    get() = if (projectMode.get()) UvMode.Project else UvMode.Pip()

  val toolValidator: ToolValidator<P> = ToolValidator(
    fileSystem = fileSystem,
    tool = UvPyTool.getInstance(),
    backProperty = uvExecutable,
    propertyGraph = propertyGraph,
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
    projectPathFlows.projectPathWithDefault.onEach { projectMode.set(hasPyProjectToml(it)) }.launchIn(scope)
  }
}
