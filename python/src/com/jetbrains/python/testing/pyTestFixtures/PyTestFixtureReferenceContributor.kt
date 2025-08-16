// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.toArray
import com.jetbrains.python.BaseReference
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.COROUTINE
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider.GENERATOR
import com.jetbrains.python.psi.*
import com.jetbrains.python.psi.resolve.ImportedResolveResult
import com.jetbrains.python.psi.types.*

class PyTestFixtureReference(pyElement: PsiElement, fixture: PyTestFixture, private val importElement: PyElement? = null, range: TextRange? = null) : BaseReference(pyElement, range), PsiPolyVariantReference {
  private val functionRef = fixture.function?.let { SmartPointerManager.createPointer(it) }
  private val resolveRef = fixture.resolveTarget?.let { SmartPointerManager.createPointer(it) }

  override fun resolve(): PyElement? = resolveRef?.element

  fun getFunction(): PyFunction? = functionRef?.element

  override fun isSoft(): Boolean = importElement == null

  override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
    val resultList = mutableListOf<ResolveResult>()
    resolve()?.let { resultList.add(PsiElementResolveResult(it)) }
    importElement?.let { resultList.add(ImportedResolveResult(it, ImportedResolveResult.RATE_NORMAL, it as PyImportedNameDefiner)) }
    return resultList.toArray(emptyArray())
  }

  override fun getVariants(): Array<Any> {
    // Provide completion variants for fixtures, especially inside pytest.mark.usefixtures strings
    val element = myElement
    val project = element.project
    val context = TypeEvalContext.codeAnalysis(project, element.containingFile)

    // If we are in a parameter, variants are handled elsewhere; be conservative and return empty
    if (element !is PyStringLiteralExpression) return emptyArray()

    val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return emptyArray()

    // Try to find the function we are attached to (decorated function)
    val func = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)

    val fixtureNames: List<String> = if (func != null) {
      // Use existing API that already filters fixtures applicable for a function and class context
      getFixtures(module, func, context)
        .filterNot { fixture ->
          val fn = fixture.function ?: return@filterNot false
          isFromPytestPackage(fn)
        }
        .map { it.name }
    }
    else {
      // Fallback: collect all top-level fixtures available in module scope
      findDecoratorsByName(module, TEST_FIXTURE_DECORATOR_NAMES)
        .filterNot { dec -> dec.target?.let { isFromPytestPackage(it) } == true }
        .mapNotNull { dec -> getTestFixtureName(dec) ?: dec.target?.name }
        .toList()
    }

    // Filter out builtin/reserved pytest fixtures and the special 'request' pseudo-fixture
    val filtered = fixtureNames.filterNot { name ->
      reservedFixturesSet.contains(name) || reservedFixtureClassSet.contains(name) || name == REQUEST_FIXTURE
    }

    return filtered.distinct().sorted().toTypedArray()
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    if (myElement is PyStringLiteralExpression) {
      return myElement.replace(PyElementGenerator.getInstance(myElement.project).createStringLiteralFromString(newElementName))
    }
    val annotationString = (myElement as? PyNamedParameter)?.annotation?.value?.name
    return myElement.replace(PyElementGenerator.getInstance(myElement.project)
                               .createParameter(newElementName, null, annotationString, LanguageLevel.getDefault()))!!
  }
}


class PyTextFixtureTypeProvider : PyTypeProviderBase() {
  override fun getParameterType(param: PyNamedParameter, func: PyFunction, context: TypeEvalContext): Ref<PyType>? {
    if (!context.maySwitchToAST(func)) {
      return null
    }
    val fixtureFunc = param.references.filterIsInstance<PyTestFixtureReference>().firstOrNull()?.resolve() as? PyFunction ?: return null
    val returnType = context.getReturnType(fixtureFunc)

    // Async or Generator type
    coroutineOrGeneratorElementType(returnType)?.let { return it }

    // generator as Iterator or Iterable
    if (fixtureFunc.isGenerator && returnType is PyCollectionType) return Ref(returnType.iteratedItemType)

    return Ref(returnType)
  }

  private fun coroutineOrGeneratorElementType(coroutineOrGeneratorType: PyType?): Ref<PyType>? {
    val type = if (coroutineOrGeneratorType is PyUnionType) coroutineOrGeneratorType.excludeNull() else coroutineOrGeneratorType
    val genericType = PyUtil.`as`(type, PyCollectionType::class.java)
    val classType = PyUtil.`as`(type, PyClassType::class.java)
    if (genericType != null && classType != null) {
      val qName = classType.getClassQName()
      if (ArrayUtil.contains(qName, "typing.Awaitable", GENERATOR)) {
        return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 0, null))
      }
      if (COROUTINE == qName) {
        return Ref.create(ContainerUtil.getOrElse(genericType.getElementTypes(), 2, null))
      }
    }
    return null
  }
}

private abstract class PyTestReferenceProvider : PsiReferenceProvider() {
  override fun acceptsTarget(target: PsiElement): Boolean = target is PyElement
}

private object PyTestReferenceAsParameterProvider : PyTestReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    val namedParam = element as? PyNamedParameter ?: return emptyArray()
    val namedFixtureParameterLink = getFixtureLink(namedParam, TypeEvalContext.codeAnalysis(element.project, element.containingFile))
                                    ?: return emptyArray()
    val annotationLength = namedParam.annotation?.textLength ?: 0
    return arrayOf(PyTestFixtureReference(namedParam, namedFixtureParameterLink.fixture, namedFixtureParameterLink.importElement, TextRange(0, element.textLength - annotationLength)))
  }
}

private object PyTestReferenceAsStringProvider : PyTestReferenceProvider() {
  override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
    if (element !is PyStringLiteralExpression) return emptyArray()
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

    val namedFixtureParameterLink = getFixtureLink(element, TypeEvalContext.codeAnalysis(element.project, element.containingFile))

    return arrayOf(
      if (namedFixtureParameterLink != null)
        PyTestFixtureReference(element, namedFixtureParameterLink.fixture, namedFixtureParameterLink.importElement, TextRange(1, element.textLength - 1))
      else
      // Provide a soft reference to enable completion even when nothing is resolvable yet (e.g., empty string)
        PyTestFixtureReference(element, PyTestFixture(null, null, element.stringValue ?: ""), null, TextRange(1, element.textLength - 1))
    )
  }
}

private fun isFromPytestPackage(element: PyElement): Boolean {
  val vFile = element.containingFile?.virtualFile ?: return false
  val path = vFile.path.replace('\\', '/')
  // Treat anything under top-level packages "pytest" or "_pytest" as internal pytest implementation
  return path.contains("/_pytest/") || path.contains("/pytest/")
}

class PyTestFixtureReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PyParameter::class.java), PyTestReferenceAsParameterProvider,
                                        PsiReferenceRegistrar.HIGHER_PRIORITY)
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PyStringLiteralExpression::class.java), PyTestReferenceAsStringProvider,
                                        PsiReferenceRegistrar.HIGHER_PRIORITY)
  }

}
