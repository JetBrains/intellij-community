// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.psi.PyFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PyInterpreterInspectionExtension {

  /**
   * Creates quickfix to create new environment. Please make sure that it's your provider that has to create new environment for the module,
   * i.e. check for the presence of certain files (like Pipfile or pyproject.toml). Return null otherwise.
   */
  fun createMissingSdkFix(module: Module, file: PyFile): PyInterpreterInspectionQuickFixData?

  /**
   * Quickfix that makes the existing environment available to the module, or null.
   */
  fun createEnvironmentAssociationFix(module: Module,
                                      sdk: Sdk,
                                      isPyCharm: Boolean,
                                      associatedModulePath: String?): PyInterpreterInspectionQuickFixData?

  fun createInstallPackagesQuickFix(module: Module): LocalQuickFix?

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<PyInterpreterInspectionExtension>("Pythonid.interpreterInspectionExtension")
  }
}

@ApiStatus.Experimental
data class PyInterpreterInspectionQuickFixData(val quickFix: LocalQuickFix, val message: String)
