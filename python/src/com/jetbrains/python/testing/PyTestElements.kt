// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.ThreeState
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * Checks if element could be test target
 * @param testCaseClassRequired see [PythonUnitTestDetectorsBasedOnSettings] docs
 */
fun isTestElement(element: PsiElement, testCaseClassRequired: ThreeState, typeEvalContext: TypeEvalContext): Boolean = when (element) {
  is PyFile -> PythonUnitTestDetectorsBasedOnSettings.isTestFile(element, testCaseClassRequired, typeEvalContext)
  is PsiDirectory -> isTestFolder(element, testCaseClassRequired, typeEvalContext)
  is PyFunction -> PythonUnitTestDetectorsBasedOnSettings.isTestFunction(element,
                                                                         testCaseClassRequired, typeEvalContext)
  is PyClass -> {
    PythonUnitTestDetectorsBasedOnSettings.isTestClass(element, testCaseClassRequired, typeEvalContext)
  }
  else -> false
}

/**
 * If an element is a subelement of the folder explicitly marked as test root -- use it
 *
 * @param findSource if true, will also check if an element is a subelement of the source root, then use it as working dir
 */
@ApiStatus.Internal
fun getExplicitlyConfiguredTestRoot(element: PsiFileSystemItem, findSource: Boolean = false): VirtualFile? {
  val vfDirectory = element.virtualFile
  val module = ModuleUtil.findModuleForPsiElement(element) ?: return null
  val manager = ModuleRootManager.getInstance(module)
  val findSourceByType = {type: JavaSourceRootType -> manager.getSourceRoots(type).firstOrNull { VfsUtil.isAncestor(it, vfDirectory, false) } }

  findSourceByType(JavaSourceRootType.TEST_SOURCE)?.let { return it }
  if (findSource) {
    findSourceByType(JavaSourceRootType.SOURCE)?.let { return it }
  }

  return null
}

private fun isTestFolder(
  element: PsiDirectory,
  testCaseClassRequired: ThreeState,
  typeEvalContext: TypeEvalContext,
): Boolean {
  return (getExplicitlyConfiguredTestRoot(element) != null) || element.name.contains("test", true) || element.children.any {
    it is PyFile && PythonUnitTestDetectorsBasedOnSettings.isTestFile(it, testCaseClassRequired, typeEvalContext)
  }
}
