// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiType
import org.jetbrains.uast.*
import java.util.*

sealed class Dependent : UserDataHolderBase() {
  abstract val element: UElement

  data class CallExpression(val resolvedIndex: Int, val call: UCallExpression, val type: PsiType) : Dependent() {
    override val element get() = call
  }

  data class Assigment(val assignee: UExpression) : Dependent() {
    override val element get() = assignee
  }

  data class CommonDependent(override val element: UElement) : Dependent()

  data class BinaryOperatorDependent(val binaryExpression: UBinaryExpression, val isDependentOfLeftOperand: Boolean) : Dependent() {
    override val element get() = binaryExpression

    val currentOperand: UExpression get() = if (isDependentOfLeftOperand) binaryExpression.leftOperand else binaryExpression.rightOperand
    val anotherOperand: UExpression get() = if (isDependentOfLeftOperand) binaryExpression.rightOperand else binaryExpression.leftOperand
  }
}

sealed class Dependency : UserDataHolderBase() {
  abstract val elements: Set<UElement>

  data class CommonDependency(val element: UElement) : Dependency() {
    override val elements = setOf(element)
  }

  data class ArgumentDependency(val element: UElement, val call: UCallExpression) : Dependency() {
    override val elements = setOf(element)
  }

  data class BranchingDependency(override val elements: Set<UElement>) : Dependency() {
    fun unwrapIfSingle(): Dependency =
      if (elements.size == 1) {
        CommonDependency(elements.single())
      }
      else {
        this
      }
  }

  /**
   * To handle cycles properly do not get [UastLocalUsageDependencyGraph.dependencies] from original graph, use [connectedGraph] instead.
   */
  data class ConnectionDependency(val dependencyFromConnectedGraph: Dependency,
                                  val connectedGraph: UastLocalUsageDependencyGraph) : Dependency() {
    init {
      check(dependencyFromConnectedGraph !is ConnectionDependency) {
        "Connect via ${dependencyFromConnectedGraph.javaClass.simpleName} does not make sense"
      }
    }

    override val elements: Set<UElement>
      get() = dependencyFromConnectedGraph.elements
  }

  /**
   * Represents list of branches, where each branch is sorted by candidates priority
   */
  data class PotentialSideEffectDependency(val candidates: Set<SortedSet<SideEffectChangeCandidate>>) : Dependency() {
    data class SideEffectChangeCandidate(
      val priority: Int,
      val updateElement: UElement,
      val dependencyEvidence: DependencyEvidence
    ) : Comparable<SideEffectChangeCandidate> {
      override fun compareTo(other: SideEffectChangeCandidate): Int = -priority.compareTo(other.priority)
    }

    data class DependencyEvidence(
      val strict: Boolean,
      val evidenceElement: UReferenceExpression? = null,
      val dependencyWitnessIdentifier: String? = null,
      val requires: DependencyEvidence? = null
    )

    override val elements: Set<UElement>
      get() = candidates.flatMap { it.map { candidate -> candidate.updateElement } }.toSet()
  }

  fun and(other: Dependency): Dependency {
    return BranchingDependency(elements + other.elements)
  }
}