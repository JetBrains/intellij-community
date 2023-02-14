// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.util.Ref
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.python.BaseReference
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.types.*

class PyTestFixtureReference(namedParameter: PyNamedParameter, fixture: PyTestFixture) : BaseReference(namedParameter) {
  private val functionRef = fixture.function?.let { SmartPointerManager.createPointer(it) }
  private val resolveRef = fixture.resolveTarget?.let { SmartPointerManager.createPointer(it) }

  override fun resolve() = resolveRef?.element

  fun getFunction() = functionRef?.element

  override fun isSoft() = true

  override fun handleElementRename(newElementName: String) = myElement.replace(
    PyElementGenerator.getInstance(myElement.project).createParameter(newElementName))!!
}


class PyTextFixtureTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (! context.maySwitchToAST(func)) {
      return null
    }
    val fixtureFunc = param.references.filterIsInstance<PyTestFixtureReference>().firstOrNull()?.getFunction() ?: return null
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

private object PyTestReferenceProvider : PsiReferenceProvider() {
  override fun acceptsTarget(target: PsiElement): Boolean = target is PyElement

  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val namedParam = element as? PyNamedParameter ?: return emptyArray()
    val fixture = getFixture(namedParam, TypeEvalContext.codeAnalysis(element.project, element.containingFile)) ?: return emptyArray()
    return arrayOf(PyTestFixtureReference(namedParam, fixture))
  }
}

class PyTestFixtureReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PyParameter::class.java), PyTestReferenceProvider,
                                        PsiReferenceRegistrar.HIGHER_PRIORITY)
  }

}
