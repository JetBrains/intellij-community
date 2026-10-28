// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiElement
import com.intellij.python.pyproject.model.evolution.findEvoPyProjectIfReady
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.sdk.legacy.PythonSdkUtil

/**
 * The interpreter of the module at the project root, for a file that belongs to no module, such as a scratch file.
 *
 * Replaces `ProjectRootManager.getProjectSdk()`, which reads `project-jdk-name` from `.idea/misc.xml`. That attribute
 * is missing or stale in the projects PY-89831 reports.
 *
 * Internal to resolve, which cannot suspend. It answers `null` until the first structure lands.
 * [PyResolveCachesInvalidator] drops what was resolved in that window. Everything else asks
 * [com.intellij.python.pyproject.model.evolution.findMainPythonInterpreter], which waits.
 */
internal fun Project.mainPythonSdk(): Sdk? = findMainEvoPyProjectIfReady()?.interpreter?.resolveSdk()

/** The interpreter of [mainPythonSdk]. */
internal fun Project.mainPythonInterpreter(): PythonInterpreter? = findMainEvoPyProjectIfReady()?.interpreter

/** The interpreter of [pythonSdkIfReady]. */
internal fun Module.pythonInterpreterIfReady(): PythonInterpreter? = findEvoPyProjectIfReady()?.interpreter

/**
 * The SDK of the interpreter of the Python project that resides on this module, read from the snapshot. `null` before
 * the first snapshot and for a module that is not Python.
 *
 * Internal to resolve and code insight, which cannot suspend. [PyResolveCachesInvalidator] drops what was resolved
 * before a snapshot changed the interpreter of the module.
 */
internal fun Module.pythonSdkIfReady(): Sdk? = findEvoPyProjectIfReady()?.interpreter?.resolveSdk()

/** The SDK of this interpreter. Resolve and the language level still read the roots and the version of an SDK. */
@Suppress("DEPRECATION")
internal fun PythonInterpreter.resolveSdk(): Sdk = getSdkAPI()

/** [pythonSdkIfReady] for the module of this element. */
internal fun PsiElement.pythonSdkIfReady(): Sdk? = ModuleUtilCore.findModuleForPsiElement(this)?.pythonSdkIfReady()

/** Whether the file of [element] is in the skeletons of the interpreter of its module, see [pythonSdkIfReady]. */
internal fun isElementInSkeletons(element: PsiElement): Boolean {
  val virtualFile = element.containingFile?.virtualFile ?: return false
  val sdk = element.pythonSdkIfReady() ?: return false
  return PythonSdkUtil.isFileInSkeletons(virtualFile, sdk)
}
