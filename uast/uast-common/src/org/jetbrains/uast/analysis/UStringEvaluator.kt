// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.util.castSafelyTo
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

@ApiStatus.Experimental
@ApiStatus.Internal
class UStringEvaluator {
  fun calculateValue(element: UElement, configuration: Configuration = Configuration()): PartiallyKnownString? {
    val graph = element.getContainingUMethod()?.let { UastLocalUsageDependencyGraph.getGraphByUElement(it) } ?: return null
    return calculate(graph, element, configuration)
  }

  private fun calculate(graph: UastLocalUsageDependencyGraph,
                        element: UElement,
                        configuration: Configuration): PartiallyKnownString? {
    if (element is ULiteralExpression && element.isString) {
      return (element.value as? String)?.let {
        PartiallyKnownString(StringEntry.Known(it, element.sourcePsi!!, element.ownTextRange))
      }
    }

    if (element is UPolyadicExpression && element.operator == UastBinaryOperator.PLUS) {
      val entries = mutableListOf<StringEntry>()

      for (operand in element.operands) {
        calculate(graph, operand, configuration)?.segments?.let { entries += it }
      }
      return PartiallyKnownString(entries)
    }

    val entries = mutableListOf<StringEntry>()

    if (graph.dependencies[element] == null && element is UReferenceExpression) {
      val declaration = element.resolve()?.toUElement()
      val value = when {
        declaration is UField && declaration.isFinal && declaration.isStatic -> {
          listOfNotNull(UastLocalUsageDependencyGraph.getGraphByUElement(declaration)?.let { graphForField ->
            declaration.uastInitializer?.let { initializer ->
              calculate(graphForField, initializer, configuration)
            }
          })
        }
        declaration is UParameter && declaration.uastParent is UMethod -> {
          analyzeUsages(declaration.uastParent as UMethod, declaration, configuration)
        }
        declaration is UDeclaration -> {
          configuration.valueProviders.mapNotNull { it.provideValue(declaration) }
        }
        else -> null
      }
      if (value != null && value.size == 1) {
        return value.first()
      }
      else {
        return PartiallyKnownString(StringEntry.Unknown(element.sourcePsi!!, element.ownTextRange, value?.takeUnless { it.isEmpty() }))
      }
    }

    if (element is UCallExpression) {
      val methodEvaluator = configuration.getEvaluatorForCall(element)
      if (methodEvaluator != null) {
        return methodEvaluator.provideValue(this, configuration, element)
      }
      if (element.resolve()?.let { configuration.methodsToAnalyzePattern.accepts(it) } == true) {
        return PartiallyKnownString(
          StringEntry.Unknown(element.sourcePsi, element.ownTextRange,
                              analyzeMethod(graph, element, configuration).takeUnless { it.isEmpty() })
        )
      }
    }

    for (dependency in graph.dependencies[element].orEmpty()) {
      val (originalDependency, graphToUse) = if (dependency is Dependency.ConnectionDependency) {
        dependency.dependencyFromConnectedGraph to dependency.connectedGraph
      } else {
        dependency to graph
      }
      when (originalDependency) {
        is Dependency.ArgumentDependency -> continue
        is Dependency.BranchingDependency -> {
          val possibleValues = dependency
            .elements
            .mapNotNull { calculate(graphToUse, it, configuration) }

          entries += StringEntry.Unknown(element.sourcePsi!!, element.ownTextRange, possibleValues)
        }
        is Dependency.CommonDependency -> {
          calculate(graphToUse, originalDependency.element, configuration)?.segments?.let { entries += it }
        }
        else -> {}
      }
    }

    if (entries.isEmpty()) {
      return PartiallyKnownString(StringEntry.Unknown(element.sourcePsi!!, element.ownTextRange))
    }
    return PartiallyKnownString(entries)
  }

  private fun analyzeUsages(method: UMethod,
                            parameter: UParameter,
                            configuration: Configuration): List<PartiallyKnownString> {
    if (configuration.parameterUsagesDepth < 2) return emptyList()

    val parameterIndex = method.uastParameters.indexOf(parameter)
    return ReferencesSearch.search(method.sourcePsi!!, configuration.usagesSearchScope).asSequence()
      .take(3)
      .mapNotNull { it.element.parent.toUElement() as? UCallExpression }
      .mapNotNull {
        it.getArgumentForParameter(parameterIndex)?.let { argument ->
          argument.getContainingUMethod()?.let { currentMethod ->
            UastLocalUsageDependencyGraph.getGraphByUElement(currentMethod)?.let { graph ->
              calculate(graph, argument, configuration.copy(methodCallDepth = configuration.parameterUsagesDepth - 1))
            }
          }
        }
      }
      .toList()
  }

  private fun analyzeMethod(graph: UastLocalUsageDependencyGraph,
                            methodCall: UCallExpression,
                            configuration: Configuration): List<PartiallyKnownString> {
    if (configuration.methodCallDepth < 2) return emptyList()

    val resolvedMethod = methodCall.resolve().toUElementOfType<UMethod>() ?: return emptyList()
    val mergedGraph = UastLocalUsageDependencyGraph.connectMethodWithCaller(resolvedMethod, graph, methodCall) ?: return emptyList()

    val results = mutableListOf<PartiallyKnownString>()
    resolvedMethod.accept(object : AbstractUastVisitor() {
      override fun visitReturnExpression(node: UReturnExpression): Boolean {
        if (node.jumpTarget != resolvedMethod) return false
        node.returnExpression?.let {
          results.addIfNotNull(
            calculate(mergedGraph, it, configuration.copy(methodCallDepth = configuration.methodCallDepth - 1)))
        }
        return super.visitReturnExpression(node)
      }
    })

    return results
  }

  private val UElement.ownTextRange: TextRange
    get() = TextRange(0, sourcePsi!!.textLength)

  fun interface DeclarationValueProvider {
    fun provideValue(element: UDeclaration): PartiallyKnownString?
  }

  fun interface MethodCallEvaluator {
    fun provideValue(evaluator: UStringEvaluator, configuration: Configuration, callExpression: UCallExpression): PartiallyKnownString?
  }

  data class Configuration(
    val methodCallDepth: Int = 1,
    val parameterUsagesDepth: Int = 1,
    val valueProviders: Iterable<DeclarationValueProvider> = emptyList(),
    val usagesSearchScope: SearchScope = LocalSearchScope.EMPTY,
    val methodsToAnalyzePattern: ElementPattern<PsiMethod> = PlatformPatterns.alwaysFalse(),
    val methodEvaluators: Map<ElementPattern<UCallExpression>, MethodCallEvaluator> = emptyMap()
  ) {
    internal fun getEvaluatorForCall(callExpression: UCallExpression): MethodCallEvaluator? {
      return methodEvaluators.entries.firstOrNull { (pattern, _) -> pattern.accepts(callExpression) }?.value
    }
  }
}