// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.FunctionParameter
import com.jetbrains.python.PyNames
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.PyResolveUtil
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.pyi.PyiFile
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus

/**
 * Contains list of well-behaved decorators from Pythons standard library, that don't change
 * signature of underlying function/class or use it implicitly somewhere (e.g. register as a callback).
 * 
 * @author Mikhail Golubev
 */
object PyKnownDecoratorUtil {
  /**
   * Map decorators of element to [PyKnownDecorator].
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   * of theirs qualified names.
   * @return list of known decorators in declaration order with duplicates (with any)
   */
  @JvmStatic
  fun getKnownDecorators(element: PyDecoratable, context: TypeEvalContext): List<PyKnownDecorator> {
    val decoratorList = element.decoratorList
    if (decoratorList == null) {
      return emptyList()
    }

    return decoratorList.decorators
      .flatMap { asKnownDecorators(it, context) }
  }

  @JvmStatic
  fun asKnownDecorators(decorator: PyDecorator, context: TypeEvalContext): List<PyKnownDecorator> {
    val qualifiedName = decorator.qualifiedName
    if (qualifiedName == null) {
      return emptyList()
    }
    // Avoid resolving property accessor decorators to prevent an infinite recursion
    val lastComponent = qualifiedName.lastComponent
    if (PyNames.GETTER == lastComponent || PyNames.SETTER == lastComponent || PyNames.DELETER == lastComponent) {
      return emptyList()
    }
    if (context.maySwitchToAST(decorator)) {
      val containingFile = decorator.containingFile
      val resolved: List<PsiElement?>
      if (containingFile is PyiFile) {
        // In .pyi files it's safe to resolve decorators such as "@overload" flow-insensitively.
        resolved = PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, containingFile as ScopeOwner, context)
      }
      else {
        resolved = PyUtil.multiResolveTopPriority(
          decorator.callee!!,
          PyResolveContext.defaultContext(context)
        )
      }
      return resolved
        .filterIsInstance<PyQualifiedNameOwner>()
        .mapNotNull { it.qualifiedName }
        .map { QualifiedName.fromDottedString(it) }
        .mapNotNull { findByQualifiedName(it) }
    }
    else {
      return asKnownDecorators(qualifiedName)
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun asKnownDecorators(qualifiedName: QualifiedName): List<PyKnownDecorator> {
    // The method might have been called during building of PSI stub indexes. Thus, we can't leave this file's boundaries.
    // TODO Use proper local resolve to imported names here
    val lastComponent = qualifiedName.lastComponent
    if (lastComponent == null) {
      return emptyList()
    }
    return findByShortName(lastComponent)
  }

  /**
   * Check that given element has any non-standard (read "unreliable") decorators.
   *
   * @param element decoratable element to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   * of theirs qualified names.
   * @see PyKnownDecorator
   */
  @JvmStatic
  fun hasUnknownDecorator(element: PyDecoratable, context: TypeEvalContext): Boolean {
    return !allDecoratorsAreKnown(element, getKnownDecorators(element, context))
  }

  /**
   * Checks that given function has any decorators from `abc` module.
   *
   * @param element Python function to check
   * @param context type evaluation context. If it doesn't allow switch to AST, decorators will be compared by the text of the last component
   * of theirs qualified names.
   * @see PyKnownDecorator
   */
  @JvmStatic
  fun hasAbstractDecorator(element: PyDecoratable, context: TypeEvalContext): Boolean {
    return getKnownDecorators(element, context).any { it.isAbstract }
  }

  @JvmStatic
  fun hasGeneratorBasedCoroutineDecorator(function: PyFunction, context: TypeEvalContext): Boolean {
    return getKnownDecorators(function,
                              context).any { it.isGeneratorBasedCoroutine }
  }

  @JvmStatic
  fun isResolvedToGeneratorBasedCoroutine(
    receiver: PyCallExpression,
    resolveContext: PyResolveContext,
    typeEvalContext: TypeEvalContext,
  ): Boolean {
    return StreamEx
      .of((receiver).multiResolveCalleeFunction(resolveContext))
      .select(PyFunction::class.java)
      .anyMatch { function -> hasGeneratorBasedCoroutineDecorator(function, typeEvalContext) }
  }

  @JvmStatic
  fun hasRedeclarationDecorator(function: PyFunction, context: TypeEvalContext): Boolean {
    return getKnownDecorators(function, context).contains(PyKnownDecorator.TYPING_OVERLOAD)
  }

  @JvmStatic
  fun hasUnknownOrChangingSignatureDecorator(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
    val decorators = getKnownDecorators(decoratable, context)
    return !allDecoratorsAreKnown(decoratable, decorators) || decorators.contains(PyKnownDecorator.UNITTEST_MOCK_PATCH)
  }

  @JvmStatic
  fun hasUnknownOrUpdatingAttributesDecorator(decoratable: PyDecoratable, context: TypeEvalContext): Boolean {
    val decorators = getKnownDecorators(decoratable, context)

    if (!allDecoratorsAreKnown(decoratable, decorators)) {
      return true
    }

    return decorators.any {
      it === PyKnownDecorator.FUNCTOOLS_LRU_CACHE ||  // cache_clear, cache_info
      it === PyKnownDecorator.FUNCTOOLS_SINGLEDISPATCH
    }
  }

  private fun allDecoratorsAreKnown(element: PyDecoratable, decorators: List<PyKnownDecorator>): Boolean {
    val decoratorList = element.decoratorList
    return if (decoratorList == null)
      decorators.isEmpty()
    else
      decoratorList.decorators.size == decorators
        .groupBy { it.shortName }.size
  }

  @Suppress("DEPRECATION")
  private fun findByShortName(shortName: String): List<PyKnownDecorator> {
    return PyKnownDecoratorProvider.EP_NAME.extensionList.stream()
      .flatMap { knownDecoratorProvider ->
        val decorators = knownDecoratorProvider.knownDecorators
        if (!decorators.isEmpty()) {
          return@flatMap decorators.stream()
        }
        // Fallback to the old implementation that will be removed in the future release
        val knownDecorator = knownDecoratorProvider.toKnownDecorator(shortName)
        if (!knownDecorator.isNullOrEmpty() && (knownDecorator != shortName)) {
          return@flatMap StreamEx.of(findByShortName(knownDecorator))
        }
        StreamEx.empty()
      }
      .filter { knownDecorator -> knownDecorator.shortName == shortName }
      .toList()
  }

  private fun findByQualifiedName(qualifiedName: QualifiedName): PyKnownDecorator? {
    return PyKnownDecoratorProvider.EP_NAME.extensionList.stream()
      .flatMap { knownDecoratorProvider: PyKnownDecoratorProvider? ->
        knownDecoratorProvider!!.knownDecorators.stream()
      }
      .filter { knownDecorator: PyKnownDecorator? -> knownDecorator!!.qualifiedName == qualifiedName }
      .findFirst()
      .orElse(null)
  }

  enum class FunctoolsWrapsParameters(private val myPosition: Int, private val myName: String) : FunctionParameter {
    WRAPPED(0, "wrapped");

    override fun getPosition(): Int {
      return myPosition
    }

    override fun getName(): String {
      return myName
    }
  }
}
