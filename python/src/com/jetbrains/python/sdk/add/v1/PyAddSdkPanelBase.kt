// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import org.jetbrains.annotations.SystemIndependent
import java.util.function.Supplier

/**
 * Contains common logic for creating target-based Python SDKs.
 */
internal abstract class PyAddSdkPanelBase(protected val project: Project?,
                                 protected val module: Module?,
                                 private val targetSupplier: Supplier<TargetEnvironmentConfiguration>?)
  : PyAddSdkPanel() {
  protected val defaultProject: Project
    get() = ProjectManager.getInstance().defaultProject

  protected val projectBasePath: @SystemIndependent String?
    get() = newProjectPath ?: module?.basePath ?: project?.basePath

  protected val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = targetSupplier?.get()

  protected val isUnderLocalTarget: Boolean
    get() = targetEnvironmentConfiguration == null

  /**
   * Note: local target is mutable.
   */
  protected val isMutableTarget: Boolean
    get() = targetEnvironmentConfiguration?.let { PythonInterpreterTargetEnvironmentFactory.Companion.isMutable(it) } ?: true

  companion object {
    internal val virtualEnvSdkFlavor: VirtualEnvSdkFlavor
      get() = VirtualEnvSdkFlavor.getInstance()

    internal fun TargetEnvironmentConfiguration?.isLocal(): Boolean = this == null
  }
}