// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

sealed class Dependent : UserDataHolderBase() {
  abstract val element: UElement

  data class CallExpression(val resolvedIndex: Int, val call: UCallExpression, val type: PsiType) : Dependent() {
    override val element: UCallExpression get() = call
  }

  data class Assigment(val assignee: UExpression) : Dependent() {
    override val element: UExpression get() = assignee
  }

  data class CommonDependent(override val element: UElement) : Dependent()

  data class BinaryOperatorDependent(val binaryExpression: UBinaryExpression, val isDependentOfLeftOperand: Boolean) : Dependent() {
    override val element: UBinaryExpression get() = binaryExpression

    val currentOperand: UExpression get() = if (isDependentOfLeftOperand) binaryExpression.leftOperand else binaryExpression.rightOperand
    val anotherOperand: UExpression get() = if (isDependentOfLeftOperand) binaryExpression.rightOperand else binaryExpression.leftOperand
  }
}


sealed class Dependency : UserDataHolderBase() {
  abstract val elements: Set<UElement>

  data class CommonDependency(
    val element: UElement,
    override val referenceInfo: DependencyOfReference.ReferenceInfo? = null
  ) : Dependency(), DependencyOfReference {
    override val elements: Set<UElement> = setOf(element)
  }

  data class ArgumentDependency(val element: UElement, val call: UCallExpression) : Dependency() {
    override val elements: Set<UElement> = setOf(element)
  }

  data class BranchingDependency(
    override val elements: Set<UElement>,
    override val referenceInfo: DependencyOfReference.ReferenceInfo? = null
  ) : Dependency(), DependencyOfReference {
    fun unwrapIfSingle(): Dependency =
      if (elements.size == 1) {
        CommonDependency(elements.single(), referenceInfo)
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

  data class PotentialSideEffectDependency(
    val candidates: CandidatesTree,
    override val referenceInfo: DependencyOfReference.ReferenceInfo? = null
  ) : Dependency(), DependencyOfReference {
    data class SideEffectChangeCandidate(
      val updateElement: UElement,
      val dependencyEvidence: DependencyEvidence,
      val dependencyWitnessValues: Collection<UValueMark> = emptyList(),
    )

    data class DependencyEvidence(
      val evidenceElement: UReferenceExpression? = null,
      val requires: Collection<DependencyEvidence> = emptyList()
    ) {
      companion object : () -> DependencyEvidence {
        private val DEFAULT = DependencyEvidence()

        override fun invoke(): DependencyEvidence = DEFAULT
      }
    }

    /**
     * Represents tree of possible update candidates. All branches represents superposition of possible updates, which exist simultaneously,
     * but real candidates should be chosen via [selectPotentialCandidates] method.
     */
    class CandidatesTree private constructor(private val root: Node) {
      internal sealed class Node {
        abstract val next: Set<Node>

        class ServiceNode(override val next: Set<Node> = emptySet()) : Node()

        class CandidateNode(val candidate: SideEffectChangeCandidate, override val next: Set<Node> = emptySet()) : Node()
      }

      /**
       * Select first proven candidate on each path to leaf.
       */
      fun selectPotentialCandidates(candidateChecker: (SideEffectChangeCandidate) -> Boolean): Collection<SideEffectChangeCandidate> {
        return selectFromBranches(root, candidateChecker)
      }

      fun addToBegin(candidate: SideEffectChangeCandidate): CandidatesTree {
        return CandidatesTree(Node.CandidateNode(candidate, next = setOf(root)))
      }

      internal fun allNodes(): Sequence<Node> =
        generateSequence(listOf(root)) { nextLevel -> nextLevel.flatMap { it.next }.takeUnless { it.isEmpty() } }.flatten()

      internal fun allElements(): Sequence<UElement> {
        return allNodes()
          .filterIsInstance<Node.CandidateNode>()
          .map { it.candidate.updateElement }
      }

      companion object {
        private fun selectFromBranches(node: Node,
                                       evidenceChecker: (SideEffectChangeCandidate) -> Boolean): Collection<SideEffectChangeCandidate> {
          return if (node is Node.CandidateNode && evidenceChecker(node.candidate)) {
            listOf(node.candidate)
          }
          else {
            node.next.flatMap { selectFromBranches(it, evidenceChecker) }
          }
        }

        internal fun fromCandidates(candidates: Iterable<SideEffectChangeCandidate>): CandidatesTree {
          return CandidatesTree(Node.ServiceNode(candidates.map { Node.CandidateNode(it) }.toSet()))
        }

        internal fun merge(trees: Iterable<CandidatesTree>): CandidatesTree {
          return CandidatesTree(Node.ServiceNode(trees.map { it.root }.toSet()))
        }

        internal fun fromCandidate(candidate: SideEffectChangeCandidate): CandidatesTree {
          return CandidatesTree(Node.CandidateNode(candidate))
        }
      }
    }

    override val elements: Set<UElement>
      get() = candidates.allElements().toSet()
  }

  fun and(other: Dependency): Dependency {
    return BranchingDependency(elements + other.elements)
  }
}

interface DependencyOfReference {
  val referenceInfo: ReferenceInfo?

  data class ReferenceInfo(val identifier: String, val possibleReferencedValues: Collection<UValueMark>)
}

class UValueMark {
  override fun toString(): String {
    return "UValueMark@${System.identityHashCode(this)}"
  }
}