// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.toArray
import com.jetbrains.python.BaseReference
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.types.*

class PyTestFixtureReference(pyElement: PsiElement, fixture: PyTestFixture, private val importElement: PyImportElement? = null, range: TextRange? = null) : BaseReference(pyElement, range), PsiPolyVariantReference {
  private val functionRef = fixture.function?.let { SmartPointerManager.createPointer(it) }
  private val resolveRef = fixture.resolveTarget?.let { SmartPointerManager.createPointer(it) }

  @Deprecated("Use new constructor")
  constructor(namedParameter: PyNamedParameter, fixture: PyTestFixture) : this(namedParameter, fixture, null)

  override fun resolve() = resolveRef?.element

  fun getFunction() = functionRef?.element

  override fun isSoft() = importElement == null

  override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
    val resultList = mutableListOf<ResolveResult>()
    resolve()?.let { resultList.add(PsiElementResolveResult(it)) }
    importElement?.let { resultList.add(ImportedResolveResult(it, ImportedResolveResult.RATE_NORMAL, it)) }
    return resultList.toArray(emptyArray())
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    if (myElement is PyStringLiteralExpression) {
      return myElement.replace(PyElementGenerator.getInstance(myElement.project).createStringLiteralFromString(newElementName))
    }
    return myElement.replace(PyElementGenerator.getInstance(myElement.project).createParameter(newElementName))!!
  }
}


class PyTextFixtureTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (!context.maySwitchToAST(func)) {
      return null
    }
    val fixtureFunc = param.references.filterIsInstance<PyTestFixtureReference>().firstOrNull()?.resolve() as? PyFunction ?: return null
    val returnType = context.getReturnType(fixtureFunc)
    if (!fixtureFunc.isGenerator) {
      return Ref(returnType)
    }
    else {
      //If generator function returns collection this collection is generator
      // which generates iteratedItemType.
      // We also must open union (toStream)
      val itemTypes = PyTypeUtil.toStream(returnType)
        .map {
          if (it is PyCollectionType && PyTypingTypeProvider.isGenerator(it))
            it.iteratedItemType
          else it
        }.toList()
      return Ref(PyUnionType.union(itemTypes))
    }
  }
}

private abstract class PyTestReferenceProvider : PsiReferenceProvider() {
  override fun acceptsTarget(target: PsiElement): Boolean = target is PyElement
}

private object PyTestReferenceAsParameterProvider : PyTestReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val namedParam = element as? PyNamedParameter ?: return emptyArray()
    val namedFixtureParameterLink = getFixtureLink(namedParam, TypeEvalContext.codeAnalysis(element.project, element.containingFile)) ?: return emptyArray()
    return arrayOf(PyTestFixtureReference(namedParam, namedFixtureParameterLink.fixture, namedFixtureParameterLink.importElement))
  }
}

private object PyTestReferenceAsStringProvider : PyTestReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is PyExpression) return emptyArray()
    val argumentList = element.parent as? PyArgumentList ?: return emptyArray()
    val callExpr = argumentList.parent as? PyCallExpression ?: return emptyArray()
    var shouldProvide = false

    // 'pytest.mark.usefixtures' as decorator
    (callExpr.parent as? PyDecorator)?.let {
      if (it.name == USE_FIXTURES) {
        shouldProvide = true
      }
    }

    // 'pytest.mark.usefixtures' as expression
    if (!shouldProvide && callExpr.callee?.name == USE_FIXTURES) {
      shouldProvide = true
    }

    // else
    if (!shouldProvide) return emptyArray()

    val namedFixtureParameterLink = getFixtureLink(element, TypeEvalContext.codeAnalysis(element.project, element.containingFile)) ?: return emptyArray()
    return arrayOf(PyTestFixtureReference(element, namedFixtureParameterLink.fixture, namedFixtureParameterLink.importElement, TextRange(1, element.textLength - 1)))
  }
}

class PyTestFixtureReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PyParameter::class.java), PyTestReferenceAsParameterProvider,
                                        PsiReferenceRegistrar.HIGHER_PRIORITY)
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PyStringLiteralExpression::class.java), PyTestReferenceAsStringProvider,
                                        PsiReferenceRegistrar.HIGHER_PRIORITY)
  }

}
