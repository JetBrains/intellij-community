// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyModuleInterpreterReader")

package com.jetbrains.python.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.pythonInterpretersIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import org.jetbrains.annotations.ApiStatus

/**
 * The interpreter of the Python project that resides on [module], read from the snapshot. `null` for no module, a
 * module that is not Python, and before the first snapshot.
 *
 * For Java code that cannot suspend and gets a module from the platform, such as an action `update` or a
 * configurable. Kotlin code reads [findEvoPyProjectIfReady].
 */
@ApiStatus.Internal
fun findModuleInterpreterIfReady(module: Module?): PythonInterpreter? = module?.findEvoPyProjectIfReady()?.interpreter

/** [findModuleInterpreterIfReady] for the module of [element]. A library file belongs to no module, so it answers `null`. */
@ApiStatus.Internal
fun findElementInterpreterIfReady(element: PsiElement): PythonInterpreter? =
  findModuleInterpreterIfReady(ModuleUtilCore.findModuleForPsiElement(element))

/** Whether [module] has an interpreter, read from the snapshot, for a caller that needs no more than that. */
@ApiStatus.Internal
fun hasModuleInterpreterIfReady(module: Module?): Boolean = findModuleInterpreterIfReady(module) != null

/** Every interpreter of the snapshot of [project]. Empty before the first snapshot. */
@ApiStatus.Internal
fun findAllInterpretersIfReady(project: Project): Set<PythonInterpreter> = project.pythonInterpretersIfReady().orEmpty()
