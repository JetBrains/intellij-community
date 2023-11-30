// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestParametrized

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.jetbrains.python.BaseReference
import com.jetbrains.python.psi.*
import com.jetbrains.python.testing.pyTestFixtures.PARAMETRIZE

private object PyTestReferenceParametrizeProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val namedParam = element as? PyNamedParameter ?: return emptyArray()
    val argName = namedParam.name ?: return emptyArray()
    val testFunc = namedParam.parentOfType<PyFunction>() ?: return emptyArray()
    val parametrizeDecorators = testFunc.decoratorList?.decorators?.filter { it.name == PARAMETRIZE } ?: return emptyArray()
    val argsStrings = parametrizeDecorators.mapNotNull { it.argumentList?.arguments?.get(0) as? PyStringLiteralExpression }

    // if argument is not found in decorator args, then return an empty array
    val argsString = argsStrings.find { isArgumentInString(it, argName) } ?: return emptyArray()

    return arrayOf(PyTestArgumentReference(namedParam, argsString))
  }

  private fun isArgumentInString(stringExpression: PyStringLiteralExpression, argumentName: String): Boolean =
    stringExpression.stringValue.split(",").map { it.trim() }.find { argumentName == it } != null

  /**
   * Is needed for correct renaming argument from `@pytest.mark.parametrize` decorator.
   *
   * For example,
   * ```
   * @pytest.mark.parametrize("arg", [])
   * def test_(arg):
   *     ...
   * ```
   * rename parameter `'arg'` from test function to `'foo'`
   * ```
   * @pytest.mark.parametrize("foo", []) # argument renamed
   * def test_(foo):
   *     ...
   * ```
   */
  class PyTestArgumentReference(pyElement: PsiElement, private val decoratorArgsString: PyStringLiteralExpression) : BaseReference(pyElement) {
    override fun resolve(): PsiElement? = myElement

    override fun handleElementRename(newElementName: String): PsiElement {
      val stringValue = decoratorArgsString.stringValue
      val startOffset = stringValue.indexOf(myElement.text)
      val endOffset = startOffset + myElement.text.length
      val prefix = stringValue.subSequence(0, startOffset).toString()
      val suffix = stringValue.subSequence(endOffset, stringValue.length)
      val newName = prefix + newElementName + suffix
      decoratorArgsString.replace(PyElementGenerator.getInstance(myElement.project).createStringLiteralFromString(newName))

      return myElement.replace(PyElementGenerator.getInstance(myElement.project)
                                 .createParameter(newElementName, null, null, LanguageLevel.forElement(myElement)))
    }
  }
}

class PyTestParametrizedContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PyParameter::class.java), PyTestReferenceParametrizeProvider,
                                        PsiReferenceRegistrar.HIGHER_PRIORITY)
  }
}