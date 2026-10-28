// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.classRoots
import com.intellij.python.sdk.backend.isDisposed
import com.intellij.python.sdk.backend.languageLevel
import com.jetbrains.python.psi.LanguageLevel

/**
 * The language level of each Python module and of each interpreter, as one snapshot of [EvoPyProjectModel] states them.
 * [PythonLanguageLevelPusher] pushes the level of a module to its source roots, and the level of an interpreter to its
 * class roots. Two equal layouts push the same levels, so the pusher compares layouts to skip a push.
 *
 * A module without an interpreter is not here, and its files get the level of the project, see [projectLevel].
 */
internal data class PythonLanguageLevelLayout(
  val moduleLevels: Map<Module, LanguageLevel>,
  val interpreterLevels: Map<PythonInterpreter, LanguageLevel>,
  /** The level of each class root of an interpreter, for a file in no module, see [levelOfInterpreterFile]. */
  private val rootLevels: Map<VirtualFile, LanguageLevel>,
) {
  /** The level of a file that belongs to no interpreter: the highest level of a module, or the default level. */
  val projectLevel: LanguageLevel
    get() = moduleLevels.values.maxWithOrNull(LanguageLevel.VERSION_COMPARATOR) ?: LanguageLevel.getDefault()

  /** The level of the interpreter whose class root holds [file], or `null` when no class root holds it. */
  fun levelOfInterpreterFile(file: VirtualFile): LanguageLevel? =
    generateSequence(file) { it.parent }.firstNotNullOfOrNull { rootLevels[it] }

  companion object {
    /** The layout before the first snapshot, and of a project without Python modules. */
    @JvmField
    val EMPTY: PythonLanguageLevelLayout = PythonLanguageLevelLayout(emptyMap(), emptyMap(), emptyMap())

    /** The layout of [snapshot]. An interpreter whose SDK is disposed has no roots and no version, so it is not here. */
    fun of(project: Project, snapshot: EvoPyProjectModel.Snapshot): PythonLanguageLevelLayout {
      val interpreterLevels = snapshot.interpreters.filterNot { it.isDisposed }.associateWith { it.languageLevel(project) }
      val moduleLevels = snapshot.evoPyProjects
        .filterNot { it.pyProject.residesOnModule.isDisposed }
        .mapNotNull { evoPyProject -> evoPyProject.interpreter?.let(interpreterLevels::get)?.let { evoPyProject.pyProject.residesOnModule to it } }
        .toMap()
      val rootLevels = HashMap<VirtualFile, LanguageLevel>()
      for ((interpreter, level) in interpreterLevels) {
        for (root in interpreter.classRoots(project)) rootLevels.putIfAbsent(root, level)
      }
      return PythonLanguageLevelLayout(moduleLevels, interpreterLevels, rootLevels)
    }
  }
}
