// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

/**
 * Dependency graph of UElements in some scope.
 * Dependencies of element are elements needed to compute value of this element.
 * Handles variable assignments and branching
 */
@ApiStatus.Experimental
class UastLocalUsageDependencyGraph private constructor(
  val dependents: Map<UElement, Set<Dependent>>,
  val dependencies: Map<UElement, Set<Dependency>>,
  val scopesObjectsStates: Map<UElement, UScopeObjectsState>,
  private val psiAnchor: PsiElement?
) {
  val uAnchor: UElement?
    get() = psiAnchor.toUElement()

  companion object {
    private val DEPENDENCY_GRAPH_KEY = Key.create<CachedValue<UastLocalUsageDependencyGraph>>("uast.local.dependency.graph")

    /**
     * Creates or takes from cache of [element] dependency graph
     */
    @JvmStatic
    fun getGraphByUElement(element: UElement): UastLocalUsageDependencyGraph? {
      val sourcePsi = element.sourcePsi ?: return null
      return CachedValuesManager.getCachedValue(sourcePsi, DEPENDENCY_GRAPH_KEY) {
        val graph = try {
          buildFromElement(sourcePsi.toUElement()!!)
        }
        catch (e: DependencyGraphBuilder.Companion.BuildOverflowException) {
          null
        }
        CachedValueProvider.Result.create(graph, PsiModificationTracker.MODIFICATION_COUNT)
      }
    }

    private fun buildFromElement(element: UElement): UastLocalUsageDependencyGraph {
      val visitor = DependencyGraphBuilder()
      try {
        element.accept(visitor)
      }
      finally {
        thisLogger().apply {
          debug {
            "graph size: dependants = ${visitor.dependents.asSequence().map { (_, arr) -> arr.size }.sum()}," +
            " dependencies = ${visitor.dependencies.asSequence().map { (_, arr) -> arr.size }.sum()}"
          }
          debug {
            "visualisation:\n${dumpDependencies(visitor.dependencies)}"
          }
        }
      }
      return UastLocalUsageDependencyGraph(visitor.dependents, visitor.dependencies, visitor.scopesStates, element.sourcePsi)
    }

    /**
     * Connects [method] graph with [callerGraph] graph and provides connections from [uCallExpression].
     * This graph may has cycles. To proper handle them, use [Dependency.ConnectionDependency].
     * It is useful to analyse methods call hierarchy.
     */
    @JvmStatic
    fun connectMethodWithCaller(method: UMethod,
                                callerGraph: UastLocalUsageDependencyGraph,
                                uCallExpression: UCallExpression): UastLocalUsageDependencyGraph? {
      val methodGraph = getGraphByUElement(method) ?: return null

      val parametersToValues = method.uastParameters.mapIndexedNotNull { paramIndex, param ->
        uCallExpression.getArgumentForParameter(paramIndex)?.let { param to it }
      }.toMap()

      val methodAndCallerMaps = MethodAndCallerMaps(method, parametersToValues, methodGraph, callerGraph)
      // TODO: handle user data holders
      return UastLocalUsageDependencyGraph(
        dependents = methodAndCallerMaps.dependentsMap,
        dependencies = methodAndCallerMaps.dependenciesMap,
        scopesObjectsStates = methodGraph.scopesObjectsStates,
        callerGraph.psiAnchor
      )
    }
  }

  private class MethodAndCallerMaps(
    method: UMethod,
    argumentValues: Map<UParameter, UExpression>,
    private val methodGraph: UastLocalUsageDependencyGraph,
    private val callerGraph: UastLocalUsageDependencyGraph
  ) {
    private val parameterUsagesDependencies: Map<UElement, Set<Dependency>>
    private val parameterValueDependents: Map<UElement, Set<Dependent>>

    init {
      val parameterUsagesDependencies = mutableMapOf<UElement, MutableSet<Dependency>>()
      val parameterValueDependents = mutableMapOf<UElement, MutableSet<Dependent>>()

      val searchScope = LocalSearchScope(method.sourcePsi!!)
      for ((parameter, value) in argumentValues) {
        val parameterValueAsDependency = Dependency.ConnectionDependency(value.extractBranchesResultAsDependency(), callerGraph)
        for (reference in ReferencesSearch.search(parameter.sourcePsi!!, searchScope).asSequence().mapNotNull { it.element.toUElement() }) {
          parameterUsagesDependencies[reference] = mutableSetOf(parameterValueAsDependency)
          val referenceAsDependent = Dependent.CommonDependent(reference)
          for (valueElement in parameterValueAsDependency.elements) {
            parameterValueDependents.getOrPut(valueElement) { HashSet() }.add(referenceAsDependent)
          }
        }
      }

      this.parameterUsagesDependencies = parameterUsagesDependencies
      this.parameterValueDependents = parameterValueDependents
    }

    val dependenciesMap: Map<UElement, Set<Dependency>>
      get() = MergedMaps(callerGraph.dependencies, methodGraph.dependencies, parameterUsagesDependencies)

    val dependentsMap: Map<UElement, Set<Dependent>>
      get() = MergedMaps(callerGraph.dependents, methodGraph.dependents, parameterValueDependents)

    private class MergedMaps<T>(val first: Map<UElement, Set<T>>,
                                val second: Map<UElement, Set<T>>,
                                val connection: Map<UElement, Set<T>>) : Map<UElement, Set<T>> {
      override val entries: Set<Map.Entry<UElement, Set<T>>>
        get() = HashSet<Map.Entry<UElement, Set<T>>>().apply {
          addAll(first.entries)
          addAll(second.entries)
          addAll(connection.entries)
        }

      override val keys: Set<UElement>
        get() = HashSet<UElement>().apply {
          addAll(first.keys)
          addAll(second.keys)
          addAll(connection.keys)
        }

      // not exact size
      override val size: Int
        get() = first.size + second.size + connection.size

      override val values: Collection<Set<T>>
        get() {
          val result = ArrayList<Set<T>>()
          result.addAll(first.values)
          result.addAll(second.values)
          result.addAll(connection.values)
          return result
        }

      override fun containsKey(key: UElement): Boolean = key in connection || key in first || key in second

      override fun containsValue(value: Set<T>): Boolean =
        connection.containsValue(value) || first.containsValue(value) || second.containsValue(value)

      override fun get(key: UElement): Set<T>? = connection[key] ?: first[key] ?: second[key]

      override fun isEmpty(): Boolean = first.isEmpty() || second.isEmpty() || connection.isEmpty()
    }
  }
}

data class UScopeObjectsState(
  val lastVariablesUpdates: Map<String, Dependency.PotentialSideEffectDependency.CandidatesTree>,
  val variableToValueMarks: Map<String, Collection<UValueMark>>
)

//region Dump to PlantUML section
@ApiStatus.Internal
fun UastLocalUsageDependencyGraph.dumpVisualisation(): String {
  return dumpDependencies(dependencies)
}

private fun dumpDependencies(dependencies: Map<UElement, Set<Dependency>>): String = buildString {
  val elements = dependencies.keys.toMutableSet().apply {
    addAll(dependencies.values.flatMap { it.flatMap { dependency -> dependency.elements } })
  }
  val elementToID = elements.mapIndexed { index, uElement -> uElement to index }.toMap()
  fun elementName(uElement: UElement) = "UElement_${elementToID[uElement]}"
  append("@startuml").append("\n")
  for ((element, id) in elementToID) {
    append("object UElement_").append(id).appendLine(" {")
    indent().append("render=\"").append(element.asRenderString().escape()).appendLine("\"")
    element.sourcePsi?.let { psiElement ->
      PsiDocumentManager.getInstance(psiElement.project).getDocument(psiElement.containingFile)?.let { document ->
        val line = document.getLineNumber(psiElement.textOffset)
        val begin = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        indent().append("line=").append("\"").append(line + 1)
        append(": ")
        append(document.charsSequence.subSequence(begin, end).toString().escape().trim())
        appendLine("\"")
      }
    }
    appendLine("}")
  }

  val dependencyToID = dependencies.values.flatten().mapIndexed { index, dependency -> dependency to index }.toMap()
  fun dependencyName(dependency: Dependency) = "${dependency.javaClass.simpleName}_${dependencyToID[dependency]}"
  for ((dependency, depIndex) in dependencyToID) {
    append("object ")
    append(dependency.javaClass.simpleName).append("_").append(depIndex)
    if (dependency is DependencyOfReference) {
      appendLine(" {")
      indent().append("values = ").append(dependency.referenceInfo?.possibleReferencedValues).appendLine()
      append("}")
    }
    appendLine()
  }

  val candidatesTreeToID = dependencies.values.flatten()
    .filterIsInstance<Dependency.PotentialSideEffectDependency>()
    .map { it.candidates }
    .mapIndexed { index, branch ->
      branch to index
    }
    .toMap()

  fun candidateTreeName(branch: Dependency.PotentialSideEffectDependency.CandidatesTree) =
    "CandidatesTree_${candidatesTreeToID[branch]}"

  var nodeIndexShift = 0
  for ((tree, id) in candidatesTreeToID) {
    append("package CandidatesTree_").append(id).appendLine(" {")

    val nodeToID = tree.allNodes().withIndex().map { (index, node) -> node to index + nodeIndexShift }.toMap()
    nodeIndexShift += nodeToID.size

    fun candidateNodeName(node: Dependency.PotentialSideEffectDependency.CandidatesTree.Node) =
      "CandidateNode_${nodeToID[node]}"

    for ((node, nodeId) in nodeToID) {
      indent().append("object CandidateNode_").append(nodeId).appendLine(" {")
      indent().indent().append("type = ").append(node.javaClass.simpleName).appendLine()
      if (node is Dependency.PotentialSideEffectDependency.CandidatesTree.Node.CandidateNode) {
        val candidate = node.candidate
        indent().indent().append("witness = ").append(candidate.dependencyWitnessValues).appendLine()
        indent().indent().append("evidence = ").append(candidate.dependencyEvidence.evidenceElement?.asRenderString()?.escape()).appendLine()

        generateSequence(candidate.dependencyEvidence.requires) { reqs -> reqs.flatMap { req -> req.requires }.takeUnless { it.isEmpty() } }
          .flatten()
          .map { it.evidenceElement?.asRenderString()?.escape() }
          .toSet()
          .takeUnless { it.isEmpty() }
          ?.joinTo(this, prefix = "$INDENT${INDENT}require = ", postfix = "\n", separator = " && ")
      }
      indent().append("}\n")
      if (node is Dependency.PotentialSideEffectDependency.CandidatesTree.Node.CandidateNode) {
        val candidate = node.candidate
        indent().edge("CandidateNode_$nodeId", ".u.>", elementName(candidate.updateElement))
      }
    }

    for (node in nodeToID.keys) {
      for (anotherNode in node.next) {
        indent().edge(candidateNodeName(node), "-u->", candidateNodeName(anotherNode))
      }
    }
    append("}\n")
  }

  for (dependency in dependencyToID.keys) {
    when (dependency) {
      is Dependency.ArgumentDependency, is Dependency.BranchingDependency, is Dependency.CommonDependency -> {
        for (element in dependency.elements) {
          edge(dependencyName(dependency), "-u->", elementName(element))
        }
      }
      is Dependency.ConnectionDependency -> {
      }
      is Dependency.PotentialSideEffectDependency -> {
        edge(dependencyName(dependency), ".u.>", candidateTreeName(dependency.candidates))
      }
    }
  }

  for (element in elementToID.keys) {
    for (elementDependency in dependencies[element].orEmpty()) {
      edge(
        elementName(element),
        if (elementDependency is Dependency.PotentialSideEffectDependency) {
          ".u.>"
        }
        else {
          "-u->"
        },
        dependencyName(elementDependency)
      )
    }
  }

  appendLine("@enduml")
}

private fun StringBuilder.edge(begin: String, type: String, end: String) {
  append(begin).append(" ").append(type).append(" ").append(end).appendLine()
}

private const val INDENT = "  "

private fun StringBuilder.indent(): StringBuilder {
  return append(INDENT)
}

private fun String.escape() =
  replace("\"", "<U+0022>")
    .replace("(", "<U+0028>")
    .replace(")", "<U+0029>")
    .replace("{", "<U+007B>")
    .replace("}", "<U+007D>")
//endregion