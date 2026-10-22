// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.quickfixes

import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.python.pyproject.model.evolution.findPythonInterpreter
import com.intellij.python.requirements.PyPackageVersion
import com.intellij.python.requirements.PyPackageVersionNormalizer
import com.jetbrains.python.inspections.dependencies.PyDependencyDeclarations
import com.jetbrains.python.packaging.PyPackageName
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.ui.PythonPackageManagerUI

/** Resolves the UI-level package manager for the Python project of [file], or `null` when no interpreter is ready. */
internal suspend fun Project.pythonPackageManagerUIFor(file: VirtualFile): PythonPackageManagerUI? {
  val interpreter = findPythonInterpreter(file, mainForOrphans = false) ?: return null
  return PythonPackageManagerUI.forPythonInterpreter(this, interpreter)
}

/**
 * Picks the version pip actually installed for each requested package, from the snapshot returned by
 * `updatePackagesBackground`. Other constraints may have steered pip to a version different from the
 * one the inspection advertised, so trusting this snapshot keeps the file in sync with reality.
 *
 * Keyed by [PyPackageName] with a [PyPackageVersion] value, so the version written to the file is
 * always PEP 440-normalized and callers can't mix up a raw name/version string.
 */
internal fun installedVersionsByName(
  installed: List<PythonPackage>,
  requestedNames: Collection<PyPackageName>,
): Map<PyPackageName, PyPackageVersion> {
  val requested = requestedNames.toSet()
  return installed.asSequence()
    .mapNotNull { pkg ->
      val name = PyPackageName.from(pkg.name).takeIf { it in requested } ?: return@mapNotNull null
      val version = PyPackageVersionNormalizer.normalize(pkg.version) ?: return@mapNotNull null
      name to version
    }
    .toMap()
}

/**
 * Rewrites every requirements.txt-style declaration in [file] whose package appears in
 * [installedByName], using the installed version as the new pin. Must be called off the EDT:
 * [writeCommandAction] switches to it internally.
 */
internal suspend fun pinDeclarationsForFile(
  project: Project,
  file: VirtualFile,
  @NlsContexts.Command commandName: String,
  installedByName: Map<PyPackageName, PyPackageVersion>,
) {
  writeCommandAction(project, commandName) {
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@writeCommandAction
    for ((requirement, declaration) in PyDependencyDeclarations.forFile(psiFile)) {
      val installedVersion = installedByName[PyPackageName.from(requirement.name)] ?: continue
      UpdateDependencyDeclaration.apply(declaration, installedVersion.presentableText)
    }
  }
}

/** Rewrites a single declaration kept alive by [declarationPointer] under a write command action. */
internal suspend fun pinSingleDeclaration(
  project: Project,
  declarationPointer: SmartPsiElementPointer<PsiElement>,
  @NlsContexts.Command commandName: String,
  newVersion: PyPackageVersion,
) {
  writeCommandAction(project, commandName) {
    val declaration = declarationPointer.element ?: return@writeCommandAction
    UpdateDependencyDeclaration.apply(declaration, newVersion.presentableText)
  }
}
