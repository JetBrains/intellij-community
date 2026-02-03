// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XStackFrame
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyPsiFacade
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.PyClassType
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeParser
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PySourcePositionResolver(
  val currentFrame: XStackFrame,
  val project: Project,
) {
  fun getSourcePositionForName(name: String?, parentType: String?): XSourcePosition? {
    if (name == null) return null
    val currentPosition: XSourcePosition? = currentFrame.sourcePosition ?: return null

    if (currentPosition == null) return null

    val file: PsiFile? = getPsiFile(currentPosition) ?: return null

    if (file == null) return null

    if (parentType.isNullOrEmpty()) {
      val elementRef = resolveInCurrentFrame(name, currentPosition, file)
      return if (elementRef.isNull) null else XDebuggerUtil.getInstance().createPositionByElement(elementRef.get())
    }
    else {
      val parentDef: PyType? = resolveTypeFromString(parentType, file)
      if (parentDef == null) {
        return null
      }
      val context = TypeEvalContext.codeInsightFallback(file.getProject())
      val results =
        parentDef.resolveMember(name, null, AccessDirection.READ, PyResolveContext.defaultContext(context))
      if (!results.isNullOrEmpty()) {
        return XDebuggerUtil.getInstance().createPositionByElement(results.first().element)
      }
      else {
        return typeToPosition(parentDef) // at least try to return parent
      }
    }
  }

  fun getSourcePositionForType(typeName: String?): XSourcePosition? {
    val currentPosition: XSourcePosition? = currentFrame.sourcePosition

    val file = getPsiFile(currentPosition)

    if (typeName == null || file !is PyFile) return null


    val pyType = resolveTypeFromString(typeName, file)
    return if (pyType == null) null else typeToPosition(pyType)
  }

  private fun resolveInCurrentFrame(name: String, currentPosition: XSourcePosition, file: PsiFile): Ref<PsiElement?> {
    val elementRef = Ref.create<PsiElement?>()
    val currentElement = file.findElementAt(currentPosition.getOffset())

    if (currentElement == null) {
      return elementRef
    }


    PyResolveUtil.scopeCrawlUp(object : PsiScopeProcessor {
      override fun execute(element: PsiElement, state: ResolveState): Boolean {
        if ((element is PyImportElement)) {
          if (name == element.getVisibleName()) {
            if (elementRef.isNull) {
              elementRef.set(element)
            }
            return false
          }
          return true
        }
        else {
          if (elementRef.isNull) {
            elementRef.set(element)
          }
          return false
        }
      }
    }, currentElement, name, null)
    return elementRef
  }

  private fun resolveTypeFromString(typeName: String, file: PsiFile): PyType? {
    var typeName = typeName
    typeName = typeName.replace("__builtin__.", "")
    var pyType: PyType? = null
    if (!typeName.contains(".")) {
      pyType = PyTypeParser.getTypeByName(file, typeName)
    }
    if (pyType == null) {
      val generator = PyElementGenerator.getInstance(project)
      val psiFacade = PyPsiFacade.getInstance(project)
      val dummyFile = generator.createDummyFile((LanguageLevel.forElement(file)), "")
      val moduleForFile = ModuleUtilCore.findModuleForPsiElement(file)
      dummyFile.putUserData(ModuleUtilCore.KEY_MODULE, moduleForFile)

      pyType = psiFacade.parseTypeAnnotation(typeName, dummyFile)
    }
    return pyType
  }

  private fun typeToPosition(pyType: PyType?): XSourcePosition? {
    val classType = PyUtil.`as`(pyType, PyClassType::class.java)

    if (classType != null) {
      return XDebuggerUtil.getInstance().createPositionByElement(classType.getPyClass())
    }

    val moduleType = PyUtil.`as`(pyType, PyModuleType::class.java)
    if (moduleType != null) {
      return XDebuggerUtil.getInstance().createPositionByElement(moduleType.module)
    }
    return null
  }

  private fun getPsiFile(currentPosition: XSourcePosition?): PsiFile? {
    if (currentPosition == null) {
      return null
    }

    return PsiManager.getInstance(project).findFile(currentPosition.getFile())
  }
}