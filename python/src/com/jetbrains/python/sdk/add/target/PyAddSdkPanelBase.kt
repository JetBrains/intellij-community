// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.readableFs.TargetConfigurationReadableFs
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.associateWithModule
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.getInterpreterVersion
import org.jetbrains.annotations.SystemIndependent
import java.util.function.Supplier

/**
 * Contains common logic for creating target-based Python SDKs.
 */
abstract class PyAddSdkPanelBase(protected val project: Project?,
                                 protected val module: Module?,
                                 private val targetSupplier: Supplier<TargetEnvironmentConfiguration>?)
  : PyAddSdkPanel(), PyAddTargetBasedSdkView {
  protected val defaultProject: Project
    get() = ProjectManager.getInstance().defaultProject

  protected val projectBasePath: @SystemIndependent String?
    get() = newProjectPath ?: module?.basePath ?: project?.basePath

  protected val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = targetSupplier?.get()

  /**
   *  For targets providing access to FS returns instance to map target path to abstraction used by validation.
   *  Otherwise return null, so [validateExecutableFile] and [validateEmptyDir] skips validations
   */
  protected val pathInfoProvider: TargetConfigurationReadableFs? = targetEnvironmentConfiguration as? TargetConfigurationReadableFs

  protected val isUnderLocalTarget: Boolean
    get() = targetEnvironmentConfiguration == null

  /**
   * Note: local target is mutable.
   */
  protected val isMutableTarget: Boolean
    get() = targetEnvironmentConfiguration?.let { PythonInterpreterTargetEnvironmentFactory.isMutable(it) } ?: true

  companion object {
    internal val virtualEnvSdkFlavor: VirtualEnvSdkFlavor
      get() = VirtualEnvSdkFlavor.getInstance()

    internal fun TargetEnvironmentConfiguration?.isLocal(): Boolean = this == null

    internal fun createSdkForTarget(project: Project?,
                                    environmentConfiguration: TargetEnvironmentConfiguration,
                                    interpreterPath: String,
                                    existingSdks: Collection<Sdk>,
                                    sdkName: String? = null): Sdk {
      // TODO [targets] Should flavor be more flexible?
      val data = PyTargetAwareAdditionalData(PyFlavorAndData(PyFlavorData.Empty, virtualEnvSdkFlavor)).also {
        it.interpreterPath = interpreterPath
        it.targetEnvironmentConfiguration = environmentConfiguration
      }

      val sdkVersion: String? = data.getInterpreterVersion(project, interpreterPath)

      val name: String
      if (!sdkName.isNullOrEmpty()) {
        name = sdkName
      }
      else {
        name = PythonInterpreterTargetEnvironmentFactory.findDefaultSdkName(project, data, sdkVersion)
      }

      val sdk = SdkConfigurationUtil.createSdk(existingSdks, generateSdkHomePath(data), PythonSdkType.getInstance(), data, name)

      if (project != null && project.modules.isNotEmpty() &&
          PythonInterpreterTargetEnvironmentFactory.by(environmentConfiguration)?.needAssociateWithModule() ?: false) {
        sdk.associateWithModule(project.modules[0], null)
      }

      sdk.versionString = sdkVersion

      data.isValid = true

      return sdk
    }
    // TODO [targets] Add identifier PyTargetAwareAdditionalData
    private fun generateSdkHomePath(data: PyTargetAwareAdditionalData): String = data.interpreterPath
  }
}