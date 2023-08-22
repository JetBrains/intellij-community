// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.variablesview.usertyperenderers.codeinsight

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.intellij.util.containers.init
import com.jetbrains.python.PyNames
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.QualifiedNameFinder
import com.jetbrains.python.psi.resolve.fromModule
import com.jetbrains.python.psi.resolve.resolveQualifiedName
import com.jetbrains.python.psi.search.PySearchUtilBase
import com.jetbrains.python.psi.stubs.PyClassNameIndex
import com.jetbrains.python.psi.types.PyModuleType
import com.jetbrains.python.psi.types.TypeEvalContext


class PyTypeNameResolver(val project: Project) {

  companion object {
    private const val BUILTINS_MODULE_FOR_PYTHON2 = "__builtin__"
    private const val BUILTINS_MODULE_FOR_PYTHON3 = "builtins"
  }

  private fun getClass(file: PyFile, name: String): PyClass? {
    val moduleType = PyModuleType(file)
    val resolveContext = PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(file.project))
    val results = moduleType.resolveMember(name, null, AccessDirection.READ, resolveContext)
    val firstPyClass = results?.firstOrNull { it.element is PyClass }?.element
    return firstPyClass as? PyClass
  }

  private fun getClassFromModule(clsName: String, moduleComponents: List<String>): PyClass? {
    val pyFiles = getElementsFromModule(moduleComponents, project).filterIsInstance<PyFile>()
    return pyFiles.firstNotNullOfOrNull { getClass(it, clsName) }
  }

  fun resolve(qualifiedName: String): PyClass? {
    val components = qualifiedName.split(".")
    val moduleComponents = components.init()
    val clsName = components.last()

    if (moduleComponents.isEmpty()) {
      val cls = getClassFromModule(clsName, listOf(BUILTINS_MODULE_FOR_PYTHON2))
      if (cls != null) return cls
      return getClassFromModule(clsName, listOf(BUILTINS_MODULE_FOR_PYTHON3))
    }

    return getClassFromModule(clsName, moduleComponents)
  }
}

fun getClassesNumberInModuleRootWithName(cls: PyClass, clsCanonicalImportPath: QualifiedName, project: Project): Int {
  val clsName = cls.name ?: return 0
  val scope = PySearchUtilBase.defaultSuggestionScope(cls)
  return PyClassNameIndex.find(clsName, project, scope)
    .mapNotNull { QualifiedNameFinder.findCanonicalImportPath(it, null) }
    .filter { canonicalImportPath ->
      canonicalImportPath.firstComponent == clsCanonicalImportPath.firstComponent
    }
    .groupBy { it }
    .size
}

fun getElementsFromModule(moduleComponents: List<String>, project: Project): List<PsiElement> {
  val module = ModuleManager.getInstance(project).modules.firstOrNull { it.name == project.name } ?: return listOf()
  val context = fromModule(module)
  val psiElements = resolveQualifiedName(QualifiedName.fromComponents(moduleComponents), context)

  val elements = mutableListOf<PsiElement>()
  for (element in psiElements) {
    when (element) {
      is PsiDirectory -> {
        val initPy = element.findFile(PyNames.INIT_DOT_PY)
        if (initPy is PyFile) elements.add(initPy)
        val initPyi = element.findFile(PyNames.INIT_DOT_PYI)
        if (initPyi is PyFile) elements.add(initPyi)
        elements.add(element)
      }
      is PyFile -> elements.add(element)
    }
  }

  return elements
}