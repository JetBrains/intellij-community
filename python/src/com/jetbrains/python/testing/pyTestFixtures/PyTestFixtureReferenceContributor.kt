// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.util.Ref
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import com.jetbrains.python.BaseReference
import com.jetbrains.python.psi.PyElementGenerator
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyParameter
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext

private class PyTextFixtureReference(namedParameter: PyNamedParameter, fixture: PyTestFixture) : BaseReference(namedParameter) {
  private val functionRef = SmartPointerManager.createPointer(fixture.function)
  private val resolveRef = SmartPointerManager.createPointer(fixture.resolveTarget)

  override fun resolve() = resolveRef.element

  fun getFunction() = functionRef.element

  override fun getVariants() = emptyArray<Any>()

  override fun isSoft() = true

  override fun handleElementRename(newElementName: String) = myElement.replace(
    PyElementGenerator.getInstance(myElement.project).createParameter(newElementName))!!
}


object PyTextFixtureTypeProvider : PyTypeProviderBase() {
  override fun getReferenceType(referenceTarget: PsiElement, context: TypeEvalContext, anchor: PsiElement?): Ref<PyType>? {
    val param = referenceTarget as? PyNamedParameter ?: return null
    val fixtureFunc = param.references.filterIsInstance(PyTextFixtureReference::class.java).firstOrNull()?.getFunction() ?: return null
    return context.getReturnType(fixtureFunc)?.let { Ref(it) }

  }
}

private object PyTestReferenceProvider : PsiReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val namedParam = element as? PyNamedParameter ?: return emptyArray()
    val fixture = getFixture(namedParam, TypeEvalContext.codeAnalysis(element.project, element.containingFile)) ?: return emptyArray()
    return arrayOf(PyTextFixtureReference(namedParam, fixture))
  }
}

object PyTestFixtureReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PyParameter::class.java), PyTestReferenceProvider,
                                        PsiReferenceRegistrar.HIGHER_PRIORITY)
  }

}
