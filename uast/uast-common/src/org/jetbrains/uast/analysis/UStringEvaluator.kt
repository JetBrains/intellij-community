// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PartiallyKnownString
import com.intellij.psi.util.StringEntry
import com.intellij.util.Plow
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
      val builderEvaluator = configuration.getBuilderEvaluatorForCall(element)
      if (builderEvaluator != null) {
        return calculateBuilder(graph, element, configuration, builderEvaluator)
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
      }
      else {
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
        else -> {
        }
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

    val scope = if (configuration.usagesSearchScope is GlobalSearchScope) {
      GlobalSearchScope.getScopeRestrictedByFileTypes(
        configuration.usagesSearchScope,
        *UastLanguagePlugin.getInstances().mapNotNull { it.language.associatedFileType }.toTypedArray()
      )
    }
    else {
      configuration.usagesSearchScope
    }
    return Plow.of<PsiReference> { processor -> ReferencesSearch.search(method.sourcePsi!!, scope).forEach(processor) }
      .limit(3)
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

  private fun calculateBuilder(
    graph: UastLocalUsageDependencyGraph,
    element: UElement,
    configuration: Configuration,
    builderEvaluator: BuilderLikeExpressionEvaluator<PartiallyKnownString?>
  ): PartiallyKnownString? {
    if (element is UCallExpression) {
      val methodEvaluator = builderEvaluator.methodDescriptions.entries.firstOrNull { (pattern, _) -> pattern.accepts(element.resolve()) }
      if (methodEvaluator != null) {
        val dependencies = graph.dependencies[element].orEmpty()
        return when (val dependency = dependencies.firstOrNull { it !is Dependency.PotentialSideEffectDependency && it !is Dependency.ArgumentDependency }) {
          is Dependency.BranchingDependency -> {
            val branchResult = dependency.elements.mapNotNull { calculateBuilder(graph, it, configuration, builderEvaluator) }.collapse(
              element)
            methodEvaluator.value(element, branchResult, this, configuration)
          }
          is Dependency.CommonDependency -> {
            val result = calculateBuilder(graph, dependency.element, configuration, builderEvaluator)
            methodEvaluator.value(element, result, this, configuration)
          }
          is Dependency.PotentialSideEffectDependency -> null // there should not be anything
          else -> methodEvaluator.value(element, null, this, configuration)
        }
      }
    }

    val dependencies = graph.dependencies[element].orEmpty()
    val dependency = (dependencies.firstOrNull { it is Dependency.PotentialSideEffectDependency }.takeIf { builderEvaluator.allowSideEffects }
                      ?: dependencies.firstOrNull { it !is Dependency.PotentialSideEffectDependency && it !is Dependency.ArgumentDependency })

    return when (dependency) {
      is Dependency.BranchingDependency -> {
        PartiallyKnownString(StringEntry.Unknown(
          element.sourcePsi!!, element.ownTextRange,
          dependency.elements.mapNotNull {
            calculateBuilder(graph, it, configuration, builderEvaluator)
          }.takeUnless { it.isEmpty() }
        ))
      }
      is Dependency.CommonDependency -> calculateBuilder(graph, dependency.element, configuration, builderEvaluator)
      is Dependency.PotentialSideEffectDependency -> if (!builderEvaluator.allowSideEffects) null
      else {
        dependency.candidates
          .selectPotentialCandidates { provePossibleDependency(it, builderEvaluator) }
          .mapNotNull { candidate ->
            calculateBuilder(graph, candidate.updateElement, configuration, builderEvaluator)
          }
          .collapse(element)
      }
      else -> null
    }
  }

  private fun provePossibleDependency(
    evidence: Dependency.PotentialSideEffectDependency.DependencyEvidence,
    builderEvaluator: BuilderLikeExpressionEvaluator<PartiallyKnownString?>
  ): Boolean {
    return (evidence.evidenceElement == null || builderEvaluator.isExpressionReturnSelf(evidence.evidenceElement)) &&
           (evidence.requires == null || provePossibleDependency(evidence.requires, builderEvaluator))
  }

  fun interface DeclarationValueProvider {
    fun provideValue(element: UDeclaration): PartiallyKnownString?
  }

  fun interface MethodCallEvaluator {
    fun provideValue(evaluator: UStringEvaluator, configuration: Configuration, callExpression: UCallExpression): PartiallyKnownString?
  }

  interface BuilderLikeExpressionEvaluator<T> {
    val buildMethod: ElementPattern<PsiMethod>

    val allowSideEffects: Boolean

    val methodDescriptions: Map<ElementPattern<PsiMethod>, (UCallExpression, T, UStringEvaluator, Configuration) -> T>

    fun isExpressionReturnSelf(expression: UReferenceExpression): Boolean = false
  }

  data class Configuration(
    val methodCallDepth: Int = 1,
    val parameterUsagesDepth: Int = 1,
    val valueProviders: Iterable<DeclarationValueProvider> = emptyList(),
    val usagesSearchScope: SearchScope = LocalSearchScope.EMPTY,
    val methodsToAnalyzePattern: ElementPattern<PsiMethod> = PlatformPatterns.alwaysFalse(),
    val methodEvaluators: Map<ElementPattern<UCallExpression>, MethodCallEvaluator> = emptyMap(),
    val builderEvaluators: List<BuilderLikeExpressionEvaluator<PartiallyKnownString?>> = emptyList()
  ) {
    internal fun getEvaluatorForCall(callExpression: UCallExpression): MethodCallEvaluator? {
      return methodEvaluators.entries.firstOrNull { (pattern, _) -> pattern.accepts(callExpression) }?.value
    }

    internal fun getBuilderEvaluatorForCall(callExpression: UCallExpression): BuilderLikeExpressionEvaluator<PartiallyKnownString?>? {
      return builderEvaluators.firstOrNull { it.buildMethod.accepts(callExpression.resolve()) }
    }
  }
}

private val UElement.ownTextRange: TextRange
  get() = TextRange(0, sourcePsi!!.textLength)

private fun List<PartiallyKnownString>.collapse(element: UElement): PartiallyKnownString? = when {
  isEmpty() -> null
  size == 1 -> single()
  else -> {
    val maxIndex = this.maxOf { it.segments.lastIndex }
    val segments = mutableListOf<StringEntry>()
    for (segmentIndex in 0..maxIndex) {
      val segment = this.mapNotNull { it.segments.getOrNull(segmentIndex) }.firstOrNull() ?: break
      if (map { it.segments.getOrNull(segmentIndex) }.any { !it.areEqual(segment) }) {
        break
      }
      segments.add(segment)
    }

    if (segments.size != maxIndex + 1) {
      segments.add(
        StringEntry.Unknown(element.sourcePsi!!, element.ownTextRange,
                            map { PartiallyKnownString(it.segments.subList(segments.size, it.segments.size)) })
      )
    }

    PartiallyKnownString(segments)
  }
}

private fun StringEntry?.areEqual(other: StringEntry?): Boolean {
  if (this == null && other == null) return true
  if (this?.javaClass != other?.javaClass) return false
  if (this is StringEntry.Unknown && other is StringEntry.Unknown) {
    return this.sourcePsi == other.sourcePsi && this.range == other.range
  }
  if (this is StringEntry.Known && other is StringEntry.Known) {
    return this.sourcePsi == other.sourcePsi && this.range == other.range && this.value == other.value
  }
  return false
}