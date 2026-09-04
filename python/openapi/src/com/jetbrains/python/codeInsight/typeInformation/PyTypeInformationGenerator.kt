// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typeInformation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

/**
 * Supplies interpreter-specific type information for Python environments.
 *
 * Implementations are expected to own their dependency discovery and generation
 * details. Applicability checks should be lightweight enough to run serially on
 * a background thread. The Python IDE selects an applicable implementation, runs
 * it, and refreshes the interpreter after a successful generation. Implementations
 * may be provided by companion plugins rather than the Python IDE itself.
 */
@ApiStatus.Experimental
interface PyTypeInformationGenerator {
  /** A stable, user-facing name for the generated type-information source. */
  val presentableName: @NlsSafe String

  /**
   * Returns whether this generator can handle [sdk]. Implementations should avoid
   * prompting or mutating the environment from this method.
   */
  suspend fun isApplicable(project: Project, sdk: Sdk): Boolean

  suspend fun generate(project: Project, sdk: Sdk): PyTypeInformationGenerationResult

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PyTypeInformationGenerator> =
      ExtensionPointName.create("Pythonid.typeInformationGenerator")
  }
}

@ApiStatus.Experimental
sealed interface PyTypeInformationGenerationResult {
  data object Success : PyTypeInformationGenerationResult

  data class Failure(
    val details: String,
  ) : PyTypeInformationGenerationResult
}
