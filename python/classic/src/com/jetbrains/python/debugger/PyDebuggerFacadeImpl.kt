// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.ide.util.TreeChooser
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyClassTreeChooserDialog
import com.jetbrains.python.debugger.smartstepinto.PySmartStepIntoHandler
import com.jetbrains.python.debugger.variablesview.usertyperenderers.ConfigureTypeRenderersHyperLink
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyUtil
import java.lang.ref.WeakReference

internal class PyDebuggerFacadeImpl : PyDebuggerFacade {
  override fun createSmartStepIntoHandler(process: PyDebugProcess): XSmartStepIntoHandler<*> = PySmartStepIntoHandler(process)

  override fun createSourcePositionResolver(frame: XStackFrame, project: Project): PySourcePositionResolverBase =
    PySourcePositionResolver(frame, project)

  override fun createEditorsProvider(): XDebuggerEditorsProvider = PyDebuggerEditorsProvider()

  override fun createConfigureTypeRendererLink(typeRendererId: String?, project: Project?, debugValue: PyDebugValue?): XDebuggerTreeNodeHyperlink =
    ConfigureTypeRenderersHyperLink(typeRendererId, project, debugValue)

  override fun canChooseExceptionClass(): Boolean = true

  override fun chooseExceptionClass(project: Project): String? {
    val dialog = PyClassTreeChooserDialog(PyBundle.message("debugger.exception.breakpoint.select.exception.class"),
                                          project,
                                          GlobalSearchScope.allScope(project),
                                          PyExceptionCachingFilter(), null)
    dialog.showDialog()
    val pyClass = dialog.selected ?: return null
    val qualifiedName = pyClass.qualifiedName
    assert(qualifiedName != null) { "Qualified name of the class shouldn't be null" }
    return qualifiedName
  }
}

private class PyExceptionCachingFilter : TreeChooser.Filter<PyClass> {
  private val processedElements = HashMap<Int, Pair<WeakReference<PyClass>, Boolean>>()

  override fun isAccepted(pyClass: PyClass): Boolean {
    pyClass.containingFile.virtualFile ?: return false
    val key = pyClass.hashCode()
    val pair = processedElements[key]
    if (pair == null || pair.first.get() !== pyClass) {
      val isException = ReadAction.compute<Boolean, RuntimeException> { PyUtil.isExceptionClass(pyClass) }
      processedElements[key] = Pair.create(WeakReference(pyClass), isException)
      return isException
    }
    return pair.second
  }
}
