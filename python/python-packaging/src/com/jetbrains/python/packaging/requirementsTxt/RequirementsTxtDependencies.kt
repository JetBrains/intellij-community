// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.requirementsTxt

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.python.requirements.parser.PyRequirementParser
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.common.toPythonPackage
import com.jetbrains.python.requirements.PyDependenciesFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/** The packages that this requirements file declares, one per requirement. */
@ApiStatus.Internal
suspend fun PyDependenciesFile.readDeclaredPackages(): PyResult<List<PythonPackage>> {
  val requirements = readAction { PyRequirementParser.fromFile(virtualFile) }
  return PyResult.success(requirements.map { it.toPythonPackage() })
}

/** Appends [requirement] to this requirements file. Returns `false` when the file did not change. */
@ApiStatus.Internal
suspend fun PyDependenciesFile.addRequirement(project: Project, requirement: PyRequirement): Boolean =
  withContext(Dispatchers.EDT) {
    RequirementsTxtManipulationHelper.addToRequirementsTxt(project, virtualFile, requirement.presentableText)
  }
