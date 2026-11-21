// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.util.LocalTimeCounter
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.jetbrains.python.PythonFileType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyDebuggerFacade {
  fun createSmartStepIntoHandler(process: PyDebugProcess): XSmartStepIntoHandler<*>?

  fun createSourcePositionResolver(frame: XStackFrame, project: Project): PySourcePositionResolverBase?

  fun createEditorsProvider(): XDebuggerEditorsProvider

  fun createConfigureTypeRendererLink(typeRendererId: String?, project: Project?, debugValue: PyDebugValue?): XDebuggerTreeNodeHyperlink?

  fun canChooseExceptionClass(): Boolean

  fun chooseExceptionClass(project: Project): String?

  companion object {
    @JvmStatic
    fun getInstance(): PyDebuggerFacade = ApplicationManager.getApplication().getService(PyDebuggerFacade::class.java)
  }
}

@ApiStatus.Internal
interface PySourcePositionResolverBase {
  fun getSourcePositionForName(name: String?, parentType: String?): XSourcePosition?

  fun getSourcePositionForType(typeName: String?): XSourcePosition?
}

internal class PyDefaultDebuggerFacade : PyDebuggerFacade {
  override fun createSmartStepIntoHandler(process: PyDebugProcess): XSmartStepIntoHandler<*>? = null

  override fun createSourcePositionResolver(frame: XStackFrame, project: Project): PySourcePositionResolverBase? = null

  override fun createEditorsProvider(): XDebuggerEditorsProvider = PyPlainDebuggerEditorsProvider

  override fun createConfigureTypeRendererLink(typeRendererId: String?, project: Project?, debugValue: PyDebugValue?): XDebuggerTreeNodeHyperlink? =
    null

  override fun canChooseExceptionClass(): Boolean = false

  override fun chooseExceptionClass(project: Project): String? = null
}

/**
 * Edits an expression as a plain Python file, without a context in the debugged code.
 */
private object PyPlainDebuggerEditorsProvider : XDebuggerEditorsProviderBase() {
  override fun getFileType(): FileType = PythonFileType.INSTANCE

  override fun createExpressionCodeFragment(project: Project, text: String, context: PsiElement?, isPhysical: Boolean): PsiFile =
    PsiFileFactory.getInstance(project)
      .createFileFromText("fragment.py", PythonFileType.INSTANCE, text, LocalTimeCounter.currentTime(), isPhysical)
}
