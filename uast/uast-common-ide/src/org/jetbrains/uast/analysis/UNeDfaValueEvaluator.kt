// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Plow
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

@ApiStatus.Internal
@ApiStatus.Experimental
class UNeDfaValueEvaluator<T : Any>(private val strategy: UValueEvaluatorStrategy<T>) {
  interface UValueEvaluatorStrategy<T : Any> {
    fun calculateLiteral(element: ULiteralExpression): T? = null

    fun calculatePolyadicExpression(element: UPolyadicExpression): CalculateRequest<T>? = null

    fun constructValueFromList(element: UElement, values: List<T>?): T?

    fun constructUnknownValue(element: UElement): T?
  }

  class CalculateRequest<T>(val arguments: List<UElement>, val collapse: (List<T?>) -> T?)

  fun calculateValue(element: UElement, configuration: UNeDfaConfiguration<T> = UNeDfaConfiguration()): T? {
    val parentElement = element.getContainingUMethod() ?: element.getContainingUVariable() as? UField
    val graph = parentElement?.let { UastLocalUsageDependencyGraph.getGraphByUElement(it) } ?: return null
    return calculate(graph, element, configuration)
  }

  fun canBeDependencyForBuilderOfThisEvaluator(element: UElement, configuration: UNeDfaConfiguration<T>): Boolean {
    val graph = element.getContainingUMethod()?.let { UastLocalUsageDependencyGraph.getGraphByUElement(it) } ?: return false

    graph.visitDependents(element) { currentElement ->
      if (currentElement is UCallExpression) {
        if (configuration.getBuilderEvaluatorForCall(currentElement) != null) {
          return true
        }
      }
    }

    return false
  }

  fun calculateContainingBuilderValue(
    element: UElement,
    configuration: UNeDfaConfiguration<T> = UNeDfaConfiguration(),
    fallbackWithCurrentElement: Boolean = false
  ): T? {
    val graph = element.getContainingUMethod()?.let { UastLocalUsageDependencyGraph.getGraphByUElement(it) } ?: return null

    graph.visitDependents(element, { it !is Dependent.CallExpression }) { currentElement ->
      if (currentElement is UCallExpression) {
        configuration.getBuilderEvaluatorForCall(currentElement)?.let { builder ->
          // TODO: provide objects to analyze only necessary branches, e.g. if our element in one of if branches
          return BuilderEvaluator(graph, configuration, builder).calculateBuilder(currentElement, null, null)
        }
      }
    }

    return if (!fallbackWithCurrentElement) {
      null
    }
    else {
      val builderLikeExpressionEvaluator = configuration.builderEvaluators.singleOrNull() ?: return null
      return BuilderEvaluator(graph, configuration, builderLikeExpressionEvaluator).calculateBuilder(element, null, null)
    }
  }

  private inline fun UastLocalUsageDependencyGraph.visitDependents(
    element: UElement,
    dependentCondition: (Dependent) -> Boolean = { true },
    visit: (UElement) -> Unit
  ) {
    val deque = ArrayDeque<UElement>()
    deque += element

    while (deque.isNotEmpty()) {
      val currentElement = deque.removeFirst()
      visit(currentElement)

      for (dependent in dependents[currentElement].orEmpty()) {
        if (dependentCondition(dependent)) deque += dependent.element
      }
    }
  }

  private fun calculate(graph: UastLocalUsageDependencyGraph,
                        element: UElement,
                        configuration: UNeDfaConfiguration<T>): T? {
    if (element is ULiteralExpression) {
      return strategy.calculateLiteral(element)
    }

    if (element is UPolyadicExpression) {
      val calculateRequest = strategy.calculatePolyadicExpression(element) ?: return null
      val operands = calculateRequest.arguments.map { calculate(graph, it, configuration) }
      return calculateRequest.collapse(operands)
    }

    if (
      element is USimpleNameReferenceExpression &&
      (graph.dependencies[element] == null || // no dependencies -> should check other sources
       element.uastParent is UQualifiedReferenceExpression) // qualified reference can have dependencies, but we should check sources
    ) {
      val declaration = element.resolveToUElement()
      val value = when {
        declaration is UField && configuration.isAppropriateField(declaration) -> {
          listOfNotNull(UastLocalUsageDependencyGraph.getGraphByUElement(declaration)?.let { graphForField ->
            declaration.uastInitializer?.let { initializer ->
              calculate(graphForField, initializer, configuration)
            }
          })
        }
        declaration is UParameter && declaration.uastParent is UMethod && declaration.uastParent == graph.uAnchor -> {
          analyzeUsages(declaration.uastParent as UMethod, declaration, configuration) { usageGraph, argument, usageConfiguration ->
            calculate(usageGraph, argument, usageConfiguration)
          }
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
        return strategy.constructValueFromList(element, value)
      }
    }

    if (element is UCallExpression) {
      val methodEvaluator = configuration.getEvaluatorForCall(element)
      if (methodEvaluator != null) {
        return methodEvaluator.provideValue(this, configuration, element)
      }
      val builderEvaluator = configuration.getBuilderEvaluatorForCall(element)
      if (builderEvaluator != null) {
        return BuilderEvaluator(graph, configuration, builderEvaluator).calculateBuilder(element, null, null)
      }
      val dslEvaluator = configuration.getDslEvaluatorForCall(element)
      if (dslEvaluator != null) {
        val (builderLikeEvaluator, methodDescriptor) = dslEvaluator
        return BuilderEvaluator(graph, configuration, builderLikeEvaluator).calculateDsl(element, methodDescriptor)
      }
      if (element.resolve()?.let { configuration.methodsToAnalyzePattern.accepts(it) } == true) {
        return strategy.constructValueFromList(element, analyzeMethod(graph, element, configuration))
      }
    }

    val dependency = graph.dependencies[element]
      ?.firstOrNull { it !is Dependency.ArgumentDependency && it !is Dependency.PotentialSideEffectDependency }
    val (originalDependency, graphToUse) = if (dependency is Dependency.ConnectionDependency) {
      dependency.dependencyFromConnectedGraph to dependency.connectedGraph
    }
    else {
      dependency to graph
    }
    return when (originalDependency) {
      is Dependency.BranchingDependency -> {
        val possibleValues = originalDependency
          .elements
          .mapNotNull { calculate(graphToUse, it, configuration) }
        strategy.constructValueFromList(element, possibleValues)
      }
      is Dependency.CommonDependency -> {
        calculate(graphToUse, originalDependency.element, configuration)
      }
      else -> {
        strategy.constructUnknownValue(element)
      }
    }
  }

  private fun analyzeUsages(
    method: UMethod,
    parameter: UParameter,
    configuration: UNeDfaConfiguration<T>,
    valueFromArgumentProvider: (UastLocalUsageDependencyGraph, UExpression, UNeDfaConfiguration<T>) -> T?
  ): List<T> {
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
      .mapNotNull {
        when (val uElement = it.element.parent.toUElement()) {
          is UCallExpression -> uElement
          is UQualifiedReferenceExpression -> uElement.selector as? UCallExpression
          else -> null
        }
      }
      .mapNotNull {
        it.getArgumentForParameter(parameterIndex)?.let { argument ->
          argument.getContainingUMethod()?.let { currentMethod ->
            UastLocalUsageDependencyGraph.getGraphByUElement(currentMethod)?.let { graph ->
              valueFromArgumentProvider(graph, argument, configuration.copy(parameterUsagesDepth = configuration.parameterUsagesDepth - 1))
            }
          }
        }
      }
      .toList()
  }

  private fun analyzeMethod(graph: UastLocalUsageDependencyGraph,
                            methodCall: UCallExpression,
                            configuration: UNeDfaConfiguration<T>): List<T> {
    if (configuration.methodCallDepth < 2) return emptyList()

    val resolvedMethod = methodCall.resolve().toUElementOfType<UMethod>() ?: return emptyList()
    val mergedGraph = UastLocalUsageDependencyGraph.connectMethodWithCaller(resolvedMethod, graph, methodCall) ?: return emptyList()

    val results = mutableListOf<T>()
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

  private inner class BuilderEvaluator(
    private val graph: UastLocalUsageDependencyGraph,
    private val configuration: UNeDfaConfiguration<T>,
    private val builderEvaluator: BuilderLikeExpressionEvaluator<T>,
  ) {
    private var declarationEvaluator: ((UDeclaration) -> T?)? = { null }

    fun calculateDsl(
      element: UCallExpression,
      dslMethodDescriptor: DslLikeMethodDescriptor<T>
    ): T? {
      val lambda = dslMethodDescriptor.lambdaDescriptor.lambdaPlace.getLambda(element) ?: return null
      val parameter = dslMethodDescriptor.lambdaDescriptor.getLambdaParameter(lambda) ?: return null
      val (lastVariablesUpdates, variableToValueMarks) = graph.scopesObjectsStates[lambda] ?: return null
      val marks = variableToValueMarks[parameter.name]
      val candidates = lastVariablesUpdates[parameter.name]?.selectPotentialCandidates {
        (marks == null || marks.intersect(it.dependencyWitnessValues).isNotEmpty()) &&
        provePossibleDependency(it.dependencyEvidence, builderEvaluator)
      } ?: return null

      declarationEvaluator = { declaration ->
        if (declaration == parameter) {
          dslMethodDescriptor.lambdaDescriptor.lambdaArgumentValueProvider()
        }
        else {
          null
        }
      }
      return candidates.mapNotNull { calculateBuilder(it.updateElement, it.dependencyWitnessValues, marks) }
        .let { strategy.constructValueFromList(element, it) }
    }

    fun calculateBuilder(
      element: UElement,
      objectsToAnalyze: Collection<UValueMark>?,
      originalObjectsToAnalyze: Collection<UValueMark>?
    ): T? {
      var nextElement = element
      val converters = mutableListOf<(T?) -> T?>()
      val result: T?
      while (true) {
        val curElement = nextElement
        if (curElement is UDeclaration && declarationEvaluator != null) {
          val value = declarationEvaluator?.invoke(curElement)
          if (value != null) {
            result = value
            break
          }
        }

        if (curElement is UCallExpression) {
          val methodEvaluator = builderEvaluator.methodDescriptions.entries.firstOrNull { (pattern, _) ->
            pattern.accepts(curElement.resolve())
          }
          if (methodEvaluator != null) {
            val dependencies = graph.dependencies[curElement].orEmpty()
            val isStrict = objectsToAnalyze == originalObjectsToAnalyze
            when (
              val dependency = dependencies
                .firstOrNull { it !is Dependency.PotentialSideEffectDependency && it !is Dependency.ArgumentDependency }
            ) {
              is Dependency.BranchingDependency -> {
                val branchResult = dependency.elements.mapNotNull {
                  calculateBuilder(it, objectsToAnalyze, originalObjectsToAnalyze)
                }.let { strategy.constructValueFromList(curElement, it) }
                result = methodEvaluator.value.evaluate(curElement, branchResult, this@UNeDfaValueEvaluator, configuration, isStrict)
                break
              }
              is Dependency.CommonDependency -> {
                nextElement = dependency.element
                converters += { methodEvaluator.value.evaluate(curElement, it, this@UNeDfaValueEvaluator, configuration, isStrict) }
                continue 
              }
              is Dependency.PotentialSideEffectDependency -> {
                result = null // there should not be anything
                break
              }
              else -> {
                result = methodEvaluator.value.evaluate(curElement, null, this@UNeDfaValueEvaluator, configuration, isStrict)
                break
              }
            }
          }
        }

        val dependencies = graph.dependencies[curElement].orEmpty()

        if (dependencies.isEmpty() && curElement is UReferenceExpression) {
          val declaration = curElement.resolveToUElement()
          if (declaration is UParameter && declaration.uastParent is UMethod && declaration.uastParent == graph.uAnchor) {
            val usagesResults = analyzeUsages(declaration.uastParent as UMethod, declaration,
                                              configuration) { usageGraph, argument, usageConfiguration ->
              BuilderEvaluator(usageGraph, usageConfiguration, builderEvaluator).calculateBuilder(argument, null, null)
            }
            result = usagesResults.singleOrNull() ?: strategy.constructValueFromList(curElement, usagesResults)
            break
          }
          if (declaration is UField && configuration.isAppropriateField(declaration)) {
            val declarationResult = listOfNotNull(UastLocalUsageDependencyGraph.getGraphByUElement(declaration)?.let { graphForField ->
              declaration.uastInitializer?.let { initializer ->
                BuilderEvaluator(graphForField, configuration, builderEvaluator).calculateBuilder(initializer, null, null)
              }
            })
            result = declarationResult.singleOrNull() ?: strategy.constructUnknownValue(curElement)
            break
          }
        }

        val (dependency, candidates) = selectDependency(dependencies, builderEvaluator) {
          (originalObjectsToAnalyze == null ||
           originalObjectsToAnalyze.intersect(it.dependencyWitnessValues).isNotEmpty()) &&
          provePossibleDependency(it.dependencyEvidence, builderEvaluator)
        }

        if (
          dependency is DependencyOfReference &&
          originalObjectsToAnalyze != null &&
          dependency.referenceInfo?.possibleReferencedValues?.intersect(originalObjectsToAnalyze)?.isEmpty() == true
        ) {
          result = null
          break
        }

        when (dependency) {
          is Dependency.BranchingDependency -> {
            val variants = dependency.elements.mapNotNull {
              calculateBuilder(it, objectsToAnalyze, originalObjectsToAnalyze)
            }.takeUnless { it.isEmpty() }

            result = if (variants?.size == 1) {
              variants.single()
            }
            else {
              strategy.constructValueFromList(curElement, variants)
            }
            break
          }
          is Dependency.CommonDependency -> {
            nextElement = dependency.element
            continue
          }
          is Dependency.PotentialSideEffectDependency -> {
            result = if (!builderEvaluator.allowSideEffects) null
            else {
              candidates
                .mapNotNull { candidate ->
                  calculateBuilder(
                    candidate.updateElement,
                    candidate.dependencyWitnessValues,
                    originalObjectsToAnalyze ?: dependency.referenceInfo?.possibleReferencedValues
                  )
                }
                .let { strategy.constructValueFromList(curElement, it) }
            }
            break
          }
          else -> {
            result = null
            break
          }
        }
      }
      return converters.asReversed().fold(result) { acc, converter -> converter(acc) }
    }
  }

  private fun provePossibleDependency(
    evidence: Dependency.PotentialSideEffectDependency.DependencyEvidence,
    builderEvaluator: BuilderLikeExpressionEvaluator<T>,
    visitedEvidences: MutableSet<Dependency.PotentialSideEffectDependency.DependencyEvidence> = mutableSetOf()
  ): Boolean {
    if (evidence in visitedEvidences) return false // Cyclic evidence
    visitedEvidences += evidence

    val result = (evidence.evidenceElement == null || builderEvaluator.isExpressionReturnSelf(evidence.evidenceElement)) &&
                 (evidence.requires.isEmpty() || evidence.requires.all { provePossibleDependency(it, builderEvaluator, visitedEvidences) })

    visitedEvidences -= evidence
    return result
  }

  private fun selectDependency(
    dependencies: Collection<Dependency>,
    builderEvaluator: BuilderLikeExpressionEvaluator<T>,
    candidateChecker: (Dependency.PotentialSideEffectDependency.SideEffectChangeCandidate) -> Boolean
  ): Pair<Dependency?, Collection<Dependency.PotentialSideEffectDependency.SideEffectChangeCandidate>> =
    dependencies
      .firstOrNull { it is Dependency.PotentialSideEffectDependency }
      .takeIf { builderEvaluator.allowSideEffects }
      ?.let { dependency ->
        (dependency as Dependency.PotentialSideEffectDependency).candidates
          .selectPotentialCandidates(candidateChecker)
          .takeUnless { it.isEmpty() }
          ?.let { candidates ->
            dependency to candidates
          }
      }
    ?: (dependencies.firstOrNull { it !is Dependency.PotentialSideEffectDependency && it !is Dependency.ArgumentDependency } to emptyList())
}