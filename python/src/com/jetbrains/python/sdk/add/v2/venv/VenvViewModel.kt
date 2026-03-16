package com.jetbrains.python.sdk.add.v2.venv

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.FolderValidator
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonToolViewModel
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first

class VenvViewModel<P : PathHolder>(
  fileSystem: FileSystem<P>,
  propertyGraph: PropertyGraph,
  projectPathFlows: ProjectPathFlows,
) : PythonToolViewModel {
  val backProperty: ObservableMutableProperty<ValidatedPath.Folder<P>?> = propertyGraph.property(null)
  val inheritSitePackages: GraphProperty<Boolean> = propertyGraph.property(false)
  val makeAvailableForAllProjects: GraphProperty<Boolean> = propertyGraph.property(false)

  val venvValidator: FolderValidator<P> = FolderValidator(
    fileSystem = fileSystem,
    backProperty = backProperty,
    propertyGraph = propertyGraph,
    defaultPathSupplier = {
      val projectPath = projectPathFlows.projectPathWithDefault.first()
      fileSystem.suggestVenv(projectPath)
    },
    pathValidator = fileSystem::validateVenv
  )

  override fun initialize(scope: CoroutineScope) {
    venvValidator.initialize(scope)
  }
}