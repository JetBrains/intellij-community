// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.findPythonInterpreterIfReady
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonRuntimeService
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl

/**
 * The only [PyQualifiedNameResolveContext].
 *
 * [interpreter] is set only for a context that has no module and no foothold, see [fromInterpreter]. Resolve then visits
 * the roots of that interpreter. A context with a module or a foothold finds its interpreter from them.
 */
data class PyQualifiedNameResolveContextImpl(
  private val psiManager: PsiManager, private val module: Module?,
  private val foothold: PsiElement?, internal val interpreter: PythonInterpreter? = null,
  private val relativeLevel: Int = -1,
  private val withoutRoots: Boolean = false,
  private val withoutForeign: Boolean = false,
  // withMembers means that re-imports from __init__.py file will be handled correctly.
  private val withMembers: Boolean = false,
  private val withPlainDirectories: Boolean = false,
  private val withoutStubs: Boolean = false,
) : PyQualifiedNameResolveContext {
  override fun getFoothold(): PsiElement? = foothold

  override fun getRelativeLevel(): Int = relativeLevel

  override fun getModule(): Module? = module

  override fun getProject(): Project = psiManager.project

  override fun getWithoutRoots(): Boolean = withoutRoots

  override fun getWithoutForeign(): Boolean = withoutForeign

  override fun getPsiManager(): PsiManager = psiManager

  override fun getWithMembers(): Boolean = withMembers

  override fun getWithPlainDirectories(): Boolean = withPlainDirectories

  override fun getWithoutStubs(): Boolean = withoutStubs

  override fun isValid(): Boolean = footholdFile?.isValid ?: true

  override fun copyWithoutForeign(): PyQualifiedNameResolveContextImpl = copy(withoutForeign = true)

  override fun copyWithMembers(): PyQualifiedNameResolveContextImpl = copy(withMembers = true)

  override fun copyWithPlainDirectories(): PyQualifiedNameResolveContextImpl = copy(withPlainDirectories = true)

  override fun copyWithRelative(relativeLevel: Int): PyQualifiedNameResolveContextImpl = copy(relativeLevel = relativeLevel)

  override fun copyWithoutRoots(): PyQualifiedNameResolveContextImpl = copy(withoutRoots = true)

  override fun copyWithRoots(): PyQualifiedNameResolveContextImpl = copy(withoutRoots = false)

  override fun copyWithoutStubs(): PyQualifiedNameResolveContextImpl = copy(withoutStubs = true)

  override fun getContainingDirectory(): PsiDirectory? {
    val file = footholdFile ?: return null
    return if (relativeLevel > 0) ResolveImportUtil.stepBackFrom(file, relativeLevel) else file.containingDirectory
  }

  override fun getFootholdFile(): PsiFile? = when (foothold) {
    is PsiDirectory -> foothold.findFile(PyNames.INIT_DOT_PY)
    else -> foothold?.containingFile?.originalFile
  }

  override fun getVisitAllModules(): Boolean {
    val file = foothold
    return file != null && (PythonRuntimeService.getInstance().isInPydevConsole(file) ||
                            PythonRuntimeService.getInstance().isInScratchFile(file) ||
                            file is PyExpressionCodeFragmentImpl)
  }
}

/**
 * The interpreter of the Python console that [foothold] is in, or `null`. The console stores its SDK, and the registry
 * of its project holds the interpreter of that SDK.
 */
internal fun consoleInterpreterOf(foothold: PsiElement): PythonInterpreter? =
  PythonRuntimeService.getInstance().getConsoleSdk(foothold)?.let { foothold.project.findPythonInterpreterIfReady(it) }

/**
 * The interpreter of a context without a module and a foothold, see [PyQualifiedNameResolveContextImpl.interpreter].
 * [PyQualifiedNameResolveContextImpl] is the only implementation, so the cast always succeeds.
 */
internal val PyQualifiedNameResolveContext.storedInterpreter: PythonInterpreter?
  get() = (this as PyQualifiedNameResolveContextImpl).interpreter
