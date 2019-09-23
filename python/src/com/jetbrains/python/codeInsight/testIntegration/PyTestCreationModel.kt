// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.testIntegration

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.testIntegration.PyTestCreationModel.Companion.createByElement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.testing.PythonUnitTestUtil

/**
 * Created with [createByElement], then modified my user and provided to [PyTestCreator.createTest] to create test
 */
class PyTestCreationModel(var fileName: String,
                          var targetDir: String,
                          var className: String,
                          var methods: List<String>) {

  init {
    assert(methods.isNotEmpty()) { "Provide at least one method" }
  }

  companion object {
    /**
     * @return model of null if no test could be created for this element
     */
    fun createByElement(element: PsiElement): PyTestCreationModel? {
      if (PythonUnitTestUtil.isTestElement(element, null)) return null //Can't create tests for tests
      val file = element.containingFile as? PyFile ?: return null
      val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
      val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
      val elementsToTest: Sequence<PsiNamedElement> = when {
        function != null -> listOf(function)
        pyClass != null -> pyClass.methods.asList()
        else -> (file.topLevelFunctions + file.topLevelClasses) as List<PsiNamedElement>
      }.asSequence().filterNot { PythonUnitTestUtil.isTestElement(it, null) }

      val functionNames = elementsToTest
        .filterNot { it.name?.startsWith("__") == true }
        .mapNotNull { it.name }
        .map { "test_${it.toLowerCase()}" }.toList()

      return if (functionNames.isEmpty()) null
      else {
        val className = if (PythonUnitTestUtil.isTestCaseClassRequired(file)) "Test${pyClass?.name ?: ""}" else ""
        PyTestCreationModel(fileName = "test_${file.name}",
                            targetDir = getTestFolder(element).path,
                            className = className,
                            methods = functionNames)
      }

    }

    private fun getTestFolder(element: PsiElement): VirtualFile =
      ModuleUtil.findModuleForPsiElement(element)?.let { module ->
        FilenameIndex.getVirtualFilesByName(element.project, "tests", module.moduleContentScope).firstOrNull()
      } ?: element.containingFile.containingDirectory.virtualFile

  }

}
