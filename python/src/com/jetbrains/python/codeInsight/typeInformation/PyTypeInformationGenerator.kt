// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.packaging.management.PythonPackageManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyTypeInformationGenerator {
  val presentableName: @NlsSafe String
  val enginePackageName: @NlsSafe String

  suspend fun isApplicable(packageManager: PythonPackageManager): Boolean

  suspend fun generate(project: Project, sdk: Sdk): PyTypeInformationGenerationResult

  companion object {
    internal val EP_NAME = ExtensionPointName.create<PyTypeInformationGenerator>("Pythonid.typeInformationGenerator")
  }
}

@ApiStatus.Internal
sealed interface PyTypeInformationGenerationResult {
  data object Success : PyTypeInformationGenerationResult

  data class Failure(
    val stage: Stage,
    val details: @NlsSafe String,
  ) : PyTypeInformationGenerationResult

  enum class Stage {
    INSTALL_ENGINE,
    GENERATE,
  }
}
