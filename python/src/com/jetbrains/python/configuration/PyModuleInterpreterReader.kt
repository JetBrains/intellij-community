// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyModuleInterpreterReader")

package com.jetbrains.python.configuration

import com.intellij.psi.PsiElement
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.pythonInterpretersIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import org.jetbrains.annotations.ApiStatus

/**
 * The SDK of the interpreter of the Python project that resides on [module], read from the snapshot. `null` for no
 * module, a module that is not Python, and before the first snapshot.
 *
 * For Java code that cannot suspend, gets a module from the platform, and still takes an SDK, such as an action
 * `update` or a configurable. Kotlin code reads [findEvoPyProjectIfReady] and keeps the interpreter.
 */
@ApiStatus.Internal
fun findModuleSdkIfReady(module: Module?): Sdk? {
  @Suppress("DEPRECATION") // The Java callers still take an SDK.
  return findModuleInterpreterIfReady(module)?.getSdkAPI()
}

/** [findModuleSdkIfReady] for the module of [element]. A library file belongs to no module, so it answers `null`. */
@ApiStatus.Internal
fun findElementSdkIfReady(element: PsiElement): Sdk? = findModuleSdkIfReady(ModuleUtilCore.findModuleForPsiElement(element))

/** [findModuleSdkIfReady] for a Java caller that takes the interpreter. */
@ApiStatus.Internal
fun findModuleInterpreterIfReady(module: Module?): PythonInterpreter? = module?.findEvoPyProjectIfReady()?.interpreter

/** [findModuleSdkIfReady] for the main Python project of [project], for a Java caller that has no module. */
@ApiStatus.Internal
fun findMainSdkIfReady(project: Project): Sdk? {
  @Suppress("DEPRECATION") // The Java callers still take an SDK.
  return project.findMainEvoPyProjectIfReady()?.interpreter?.getSdkAPI()
}

/** The module of the main Python project of [project], read from the snapshot, for a Java caller. */
@ApiStatus.Internal
fun findMainModuleIfReady(project: Project): Module? = project.findMainEvoPyProjectIfReady()?.pyProject?.residesOnModule

/** The SDKs of every interpreter of the snapshot of [project]. Empty before the first snapshot. */
@ApiStatus.Internal
fun findAllSdksIfReady(project: Project): List<Sdk> {
  @Suppress("DEPRECATION") // The callers still take an SDK.
  return project.pythonInterpretersIfReady().orEmpty().map { it.getSdkAPI() }.distinct()
}

/** Every interpreter of the snapshot of [project]. Empty before the first snapshot. */
@ApiStatus.Internal
fun findAllInterpretersIfReady(project: Project): Set<PythonInterpreter> = project.pythonInterpretersIfReady().orEmpty()
