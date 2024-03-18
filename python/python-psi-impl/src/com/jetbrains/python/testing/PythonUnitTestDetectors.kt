// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.util.Processor
import com.jetbrains.python.extensions.inherits
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.types.TypeEvalContext

/**
 * Any "test_" function could be used as test by pytest.
 * See `PythonUnitTestDetectorsBasedOnSettings.isTestFunction`.
 */
fun isTestFunction(function: PyFunction) = function.name?.startsWith("test") == true

/**
 * Inheritor of TestCase class is always test for unittest and could also be launched with pytest.
 * See `PythonUnitTestDetectorsBasedOnSettings.isTestFunction`.
 */
fun isUnitTestCaseClass(clazz: PyClass, context: TypeEvalContext) = clazz.inherits(context, "unittest.TestCase", "unittest.case.TestCase")

/**
 * Checks if class [isUnitTestCaseClass] or both conditions are true: it's name starts/ends with "Test" and it has at least one
 * "test" or setup method.
 * See `PythonUnitTestDetectorsBasedOnSettings.isTestFunction`.
 */
fun isTestClass(clazz: PyClass, context: TypeEvalContext): Boolean {
  if (isUnitTestCaseClass(clazz, context)) {
    return true
  }
  val className = clazz.name ?: return false
  if (!className.startsWith("Test") && !className.endsWith("Test")) {
    return false
  }

  object : Processor<PyFunction> {
    var hasTestFunction: Boolean = false
      private set

    override fun process(function: PyFunction): Boolean {
      if (isTestFunction(function) || function.name?.equals("setUp") == true) {
        hasTestFunction = true
        return false
      }
      return true
    }
  }.apply {
    clazz.visitMethods(this, true, context)
    return hasTestFunction
  }
}

fun isTestFile(file: PyFile,
               context: TypeEvalContext): Boolean {
  return if (file.topLevelClasses.stream().anyMatch { o: PyClass ->
      isTestClass(o, context)
    }) {
    true
  }
  else file.name.startsWith("test_") ||
       file.topLevelFunctions.stream().anyMatch { o: PyFunction ->
         isTestFunction(o)
       }
}

fun isTestElement(element: PsiElement, typeEvalContext: TypeEvalContext): Boolean = when (element) {
  is PyFile -> isTestFile(element, typeEvalContext)
  is PsiDirectory -> element.name.contains("test", true) || element.children.any {
    it is PyFile && isTestFile(it, typeEvalContext)
  }
  is PyFunction -> isTestFunction(element)
  is PyClass -> {
    isTestClass(element, typeEvalContext)
  }
  else -> false
}
