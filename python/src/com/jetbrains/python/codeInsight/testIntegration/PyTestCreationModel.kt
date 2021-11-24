// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.testIntegration

import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.codeInsight.testIntegration.PyTestCreationModel.Companion.createByElement
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.testing.PythonUnitTestDetectorsBasedOnSettings

/**
 * Created with [createByElement], then modified my user and provided to [PyTestCreator.createTest] to create test
 */
class PyTestCreationModel(var fileName: String,
                          var targetDir: String,
                          var className: String,
                          var methods: List<String>) {

  init {
    assert(className.isNotEmpty() || methods.isNotEmpty()) { "Either class or at least one method must be provided" }
  }

  companion object {
    private val String.asFunName
      get():String {
        return replace(Regex("([a-z])([A-Z])"), "$1_$2").toLowerCase()
      }

    /**
     * @return model of null if no test could be created for this element
     */
    fun createByElement(element: PsiElement): PyTestCreationModel? {
      if (PythonUnitTestDetectorsBasedOnSettings.isTestElement(element, null)) return null //Can't create tests for tests
      val fileUnderTest = element.containingFile as? PyFile ?: return null
      val classUnderTest = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
      val functionUnderTest = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
      val elementsUnderTest: Sequence<PsiNamedElement> = when {
        functionUnderTest != null -> listOf(functionUnderTest)
        classUnderTest != null -> classUnderTest.methods.asList()
        else -> (fileUnderTest.topLevelFunctions + fileUnderTest.topLevelClasses)
      }.asSequence().filterIsInstance<PsiNamedElement>().filterNot { PythonUnitTestDetectorsBasedOnSettings.isTestElement(it, null) }


      /**
       * [PyTestCreationModel] has optional field "class" and list of methods.
       * For unitTest we need "class" field to  filled by test name.
       * For pytest we may leave it empty, but we need at least one method.
       */

      val testFunctionNames = elementsUnderTest
        .mapNotNull { it.name }
        .filterNot { it.startsWith("__") }
        .map { "test_${it.asFunName}" }.toMutableList()

      val nameOfClassUnderTest = classUnderTest?.name
      // True for unitTest
      val testCaseClassRequired = PythonUnitTestDetectorsBasedOnSettings.isTestCaseClassRequired(fileUnderTest)
      if (testFunctionNames.isEmpty()) {
        when {
          // No class, no function, what to generate?
          classUnderTest == null -> return null
          // For UnitTest we can generate test class. For pytest we need at least one function
          !testCaseClassRequired -> testFunctionNames.add("test_" + (nameOfClassUnderTest?.asFunName ?: "fun"))
        }
      }
      return PyTestCreationModel(fileName = "test_${fileUnderTest.name}",
                                 targetDir = getTestFolder(element).path,
                                 className = if (testCaseClassRequired) "Test${nameOfClassUnderTest ?: ""}" else "",
                                 methods = testFunctionNames)

    }

    private fun getTestFolder(element: PsiElement): VirtualFile =
      ModuleUtil.findModuleForPsiElement(element)?.let { module ->
        // No need to create tests in site-packages (aka classes root)
        val fileIndex = ProjectFileIndex.getInstance(element.project)
        return@let FilenameIndex.getVirtualFilesByName("tests", module.moduleContentScope).firstOrNull { possibleRoot ->
          possibleRoot.isDirectory && !fileIndex.isInLibrary(possibleRoot)
        }
      } ?: element.containingFile.containingDirectory.virtualFile
  }

}
