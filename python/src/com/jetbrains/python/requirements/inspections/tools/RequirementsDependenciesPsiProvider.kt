// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.inspections.tools

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import com.intellij.python.pyproject.PY_PROJECT_TOML_PROJECT
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.dependencies.DependenciesPsiProvider
import com.jetbrains.python.inspections.dependencies.DependencyMap
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.psi.injectionParent
import com.jetbrains.python.requirements.RequirementsFile
import com.jetbrains.python.requirements.RequirementsLanguage
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

/**
 * For TOML injections, only `[project].dependencies` (PEP 621 main project dependencies) should
 * be checked for "must be installed" / "is outdated" problems. All other dependency-bearing
 * sections registered by [com.jetbrains.python.requirements.injection.TomlRequirementsInjectionSupport]
 * — `build-system`, `project.optional-dependencies`, `dependency-groups`,
 * `tool.uv.dev-dependencies`, and `tool.hatch.envs.*` — describe optional, conditional, or
 * build-time dependencies that may legitimately not be installed (and that the user has not
 * actively asked the IDE to keep in lock-step with the active interpreter).
 *
 * Classifying by TOML structure (rather than by what the package manager currently reports as
 * "main declared") avoids false negatives when the lockfile is stale: a freshly added entry in
 * `[project].dependencies` is recognized as main even before `uv lock`/`uv sync` runs.
 */
private fun PsiElement.isInUninspectedTomlSection(): Boolean {
  val keyValue = findParentOfType<TomlKeyValue>() ?: return false
  val sectionName = keyValue.findParentOfType<TomlTable>()?.header?.key?.text ?: return false
  val fieldName = keyValue.key.text
  return !(sectionName == PY_PROJECT_TOML_PROJECT && fieldName == "dependencies")
}

/**
 * Dependencies PSI provider for requirements.txt. Uses [isInUninspectedTomlSection] for 
 * scope filtering — previously the outdated check fired in `[build-system]`, `[dependency-groups]`, 
 * etc. because that filter only existed in the not-installed inspection; that was a bug, since 
 * there's no reason to nag about an outdated build-system or dev dependency relative to the active 
 * project interpreter.
 */
internal class RequirementsDependenciesPsiProvider : DependenciesPsiProvider<RequirementsFile>(
  RequirementsFile::class.java,
  RequirementsLanguage.INSTANCE,
) {
  override fun provideDependencies(file: RequirementsFile): DependencyMap? {
    val injectionParent = file.injectionParent()

    if (injectionParent != null && injectionParent.isInUninspectedTomlSection()) {
      return null
    }

    val requirements = file.requirements()
    val dependenciesMap = mutableMapOf<PyRequirement, PsiElement>()

    for (req in requirements) {
      val pyRequirement = PyRequirementParser.fromLine(req.text) ?: continue
      dependenciesMap += pyRequirement to req
    }

    return dependenciesMap
  }

  override val emptyFileInspectionMessage: @InspectionMessage String
    get() = PyPsiBundle.message("INSP.package.requirements.requirements.file.empty")
}