// Copyright 2000-2025 JetBrains s.r.o. and contributors.
// Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
//
// Portions of this file are derived from work covered by the Eclipse Public License v1.0:
//   Copyright (c) 2016 NumberFour AG. All rights reserved.
//   This program and the accompanying materials are made available under the terms of the
//   Eclipse Public License v1.0, which accompanies this distribution and is available at
//   http://www.eclipse.org/legal/epl-v10.html
//
// Contributors of the EPL-1.0-licensed portions:
//   NumberFour AG - Initial API and implementation
package com.jetbrains.python.psi.types

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.jetbrains.python.codeInsight.typing.isProtocol
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.resolve.RatedResolveResult
import com.jetbrains.python.psi.types.ConstraintReducer.reduce
import com.jetbrains.python.psi.types.PyRecursiveTypeVisitor.PyTypeTraverser
import com.jetbrains.python.psi.types.PyTypeUtil.getEffectiveBound
import com.jetbrains.python.psi.types.PyTypeVarType.Variance
import com.jetbrains.python.psi.types.SubtypeJudgement.isRawSubtype
import com.jetbrains.python.psi.types.SubtypeJudgement.isSubtype
import com.jetbrains.python.psi.types.SubtypeJudgement.sameTypes
import org.jetbrains.annotations.ApiStatus
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.Objects
import java.util.TreeMap


@ApiStatus.Experimental
class CspBuilder(val context: TypeEvalContext) {
  private val initialConstraints = LinkedHashMap<TypeConstraint, ConstraintPriority>()
  private val inferenceVars = InferenceVariablePool()
  private lateinit var cp: ConstraintProblem

  fun addConstraint(left: PyType?, right: PyType?, variance: Variance, priority: ConstraintPriority) {
    if (left == right) {
      return
    }
    val newConstraint = TypeConstraint(left, right, variance)
    if (!initialConstraints.contains(newConstraint) || initialConstraints[newConstraint]!! < priority) {
      initialConstraints[newConstraint] = priority
    }
  }

  /**
   * No support for `Type[ TypeVariable]`.
   *
   * Reason: The current typing model models the 'type of' relation using the [PyInstantiableType.isDefinition] flag.
   * This results in modeling both `TypeVar` and `Type[ TypeVar]` as two `PyTypeVarType`s that share the same
   * [PyTypeVarType.declarationElement] but only differ in their `isDefinition` flag.
   *
   * Possible solutions:
   * 1) Do not support `Type[ TypeVar]` (chosen option)
   * 2) Rectify the type model to model `Type[ TypeVar]` as `PyTypeType` using `PyTypeVarType`
   * 3) Only within the CSP, model `Type[ TypeVar]` as `PyTypeType` using `PyTypeVarType` as the type argument, and substitute when entering/leaving the CSP
   * 4) Within CSP, use two CSP variables to model a [PyTypeVarType] with the `isDefinition` flag and a `PyTypeType` true/false, respectively.
   *   Also use additional constraints to make sure that both of these variables are equal except for the `isDefinition` flag.
   *
   * Reasoning:
   * 3) Negative impact on performance: During the solving process, a large number of subtype checks can occur, and substitution causes traverses of all related types.
   * 4) Introduction of new Variance: `NONVARIANT` would raise complexity of the constraint solving procedure further.
   */
  fun addInferenceVariable(typeVariable: PyTypeVarType) {
    inferenceVars.add(typeVariable)
  }

  fun hasInferenceVariable(typeVariable: PyTypeVarType): Boolean {
    return inferenceVars.contains(typeVariable)
  }

  fun solve() {
    val sortedConstraints = getSortedConstraints()
    cp = ConstraintProblem(sortedConstraints, inferenceVars, context)

    for (constraint in cp.constraints) {
      reduce(constraint, cp)
    }
    TypeBoundIncorporator.incorporate(cp, context)
    TypeBoundResolver.resolve(cp, context)
  }

  private fun getSortedConstraints(): Collection<TypeConstraint> {
    // Sort by priority
    // but also sort all constraints to the end that contain unions or unsafe unions.
    // The reason for the latter is that this will delay their reduce() calls
    // which will in turn increase the number of available bounds and improve the heuristics of [reduceDisjunction()].
    // Both of the above sort criteria are stable, i.e., keep the initial order if possible.

    val sortedConstraints: List<TypeConstraint> = initialConstraints.entries
      .map { e ->
        Triple(e.key, e.value,
               containsUnionsOrUnsafeUnions(e.key.left, context) || containsUnionsOrUnsafeUnions(e.key.right, context))
      }
      .sortedWith(
        compareBy<Triple<TypeConstraint, ConstraintPriority, Boolean>> {
          it.third // containsUnionsOrUnsafeUnions
        }.thenBy {
          it.second // priority
        }
      )
      .map { it.first }

    return sortedConstraints
  }

  private fun containsUnionsOrUnsafeUnions(root: PyType?, context: TypeEvalContext): Boolean {
    if (root is PyUnionType || root is PyUnsafeUnionType) {
      return true
    }
    var result = false
    PyRecursiveTypeVisitor.traverse(root, context, object : PyTypeTraverser() {
      override fun visitPyType(type: PyType): PyRecursiveTypeVisitor.Traversal {
        if (type is PyUnionType || type is PyUnsafeUnionType) {
          result = true
          return PyRecursiveTypeVisitor.Traversal.TERMINATE
        }
        return PyRecursiveTypeVisitor.Traversal.CONTINUE
      }
    })
    return result
  }

  /**
   * Post-computes the instantiation values to a usable mapping from type variables to types.
   *
   * @param keepUnconstrained iff true, unconstrained type variables are not replaced by Any or their default type
   */
  fun getSolution(keepUnconstrained: Boolean): Solution {
    val instantiations = if (cp.failed) cp.instantiations else cp.solution
    val typeVars2TypeRefs: MutableMap<PyTypeVarType, Ref<PyType?>> = LinkedHashMap()
    for (entry in instantiations) {
      val instantiatedType = entry.value
      val instantiatedTypeOrTypeVar: PyType?
      if (instantiatedType is PyUnconstrainedTypeVariable) {
        val originalTypeVar = instantiatedType.typeVariable
        instantiatedTypeOrTypeVar = if (keepUnconstrained) originalTypeVar else originalTypeVar.defaultType?.get()
      }
      else if (instantiatedType is PyTypeVarType) {
        // if the solution is another PyTypeVarType, check the declared default types
        if (instantiatedType.defaultType?.get() != null) {
          instantiatedTypeOrTypeVar = instantiatedType.defaultType?.get()
        }
        else if (entry.key.typeVariable.defaultType?.get() != null) {
          instantiatedTypeOrTypeVar = entry.key.typeVariable.defaultType?.get()
        }
        else {
          instantiatedTypeOrTypeVar = instantiatedType
        }
      }
      else {
        instantiatedTypeOrTypeVar = instantiatedType
      }
      typeVars2TypeRefs[entry.key.typeVariable] = Ref.create(instantiatedTypeOrTypeVar)
    }
    val complete = instantiations.keys.containsAll(cp.inferenceVars.values())
    return Solution(cp.failed, complete, typeVars2TypeRefs)
  }
}

enum class ConstraintPriority {
  HIGH,
  MEDIUM,
  LOW
}

data class Solution(
  val failed: Boolean,
  val complete: Boolean,
  val instantiations: Map<PyTypeVarType, Ref<PyType?>>,
)

/**
 * A type constraint of the form `left variance right` where `left`, and `right` are arbitrary type arguments,
 * and `variance` is one of `<:`, `:>`, or =`.
 * <p>
 * No restrictions apply whether inference variables appear on none, one, or both sides.
 */
private data class TypeConstraint(
  val left: PyType?,
  val right: PyType?,
  val variance: Variance,
)

/**
 * Inference variables are meta-variables for types, i.e., they represent the unknown types searched for while solving a
 * constraint system.
 */
private class InferenceVariable(typeVariable: PyTypeVarType) : PyTypeVarTypeWrapperType(typeVariable)

/**
 * Unconstrained type variables are open solutions, i.e., they represent either the Any type or the default type their
 * type variable declares.
 */
private class PyUnconstrainedTypeVariable(typeVariable: PyTypeVarType) : PyTypeVarTypeWrapperType(typeVariable)

private open class PyTypeVarTypeWrapperType(val typeVariable: PyTypeVarType) : PyType {

  override val name = typeVariable.name

  override val isBuiltin = false

  override fun resolveMember(
    name: String,
    location: PyExpression?,
    direction: AccessDirection,
    resolveContext: PyResolveContext,
  ): List<RatedResolveResult> = emptyList()

  override fun getCompletionVariants(
    completionPrefix: String?, location: PsiElement, context: ProcessingContext,
  ): Array<out Any> = emptyArray()

  override fun assertValid(message: String?) {}

  override fun hashCode(): Int {
    return Objects.hash(typeVariable.scopeOwner, typeVariable.name)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null) return false
    if (other !is PyTypeVarTypeWrapperType) return false
    if (this.javaClass != other.javaClass) return false
    if (typeVariable.name != other.typeVariable.name) return false
    if (typeVariable.scopeOwner != other.typeVariable.scopeOwner) return false
    return true
  }

  override fun toString(): String {
    return typeVariable.name
  }
}


private class InferenceVariablePool {
  val pool = LinkedHashMap<InferenceVariable, InferenceVariable>()

  fun get(typeVar: PyTypeVarType): InferenceVariable? {
    return pool[InferenceVariable(typeVar)]
  }

  fun contains(typeVar: PyTypeVarType): Boolean {
    return contains(InferenceVariable(typeVar))
  }

  fun contains(infVar: InferenceVariable): Boolean {
    return pool.containsKey(infVar)
  }

  fun add(typeVar: PyTypeVarType): InferenceVariable? {
    return add(InferenceVariable(typeVar))
  }

  fun add(infVar: InferenceVariable): InferenceVariable? {
    return pool.putIfAbsent(infVar, infVar)
  }

  fun values(): Collection<InferenceVariable> {
    return pool.values
  }
}

/**
 * Type bounds are similar to {@link PyTypeInferenceConstraint}, but are required to be of a simpler, more unified form:
 * the LHS must always be an inference variable. The RHS, however, may be a proper or improper type.
 */
private data class TypeBound(
  val left: InferenceVariable,
  val right: PyType?,
  val variance: Variance,
  val referencedInfVars: Set<InferenceVariable>,
) {
  constructor(
    left: InferenceVariable,
    right: PyType?,
    variance: Variance,
    context: TypeEvalContext,
  ) : this(left, right, variance, collectInferenceVariables(right, context))
}


private class ConstraintProblem(
  pConstraints: Collection<TypeConstraint>,
  pInferenceVars: InferenceVariablePool,
  pContext: TypeEvalContext,
) {
  val context = pContext

  val inferenceVars = pInferenceVars

  /** Initial set of constraints */
  val constraints: Collection<TypeConstraint> = substituteByInferenceVars(pConstraints, pInferenceVars, pContext)

  /** True iff a constraint/bound has been added that renders this problem unsolvable. */
  var failed = false

  /** All bounds of this constraint problem */
  val bounds = LinkedHashSet<TypeBound>()

  /** Snapshot of all bounds of this constraint problem right before the resolution started */
  val preResolutionBounds = LinkedHashSet<TypeBound>()

  /** Bounds within this bound set, stored per inference variable.  */
  val boundsPerInfVar: SetMultimap<InferenceVariable, TypeBound> = LinkedHashMultimap.create()

  /** Reverse index: for each inf-var γ, which bounds' RHS mention γ */
  val boundsReferencingInfVar: SetMultimap<InferenceVariable, TypeBound> = LinkedHashMultimap.create()

  /** Instantiations among the bounds of this set, i.e., bounds of the form `α = P` with P being a proper type. */
  val instantiations: MutableMap<InferenceVariable, PyType?> = LinkedHashMap()

  /** Instantiations at the end of a successful resolving step. */
  val solution: MutableMap<InferenceVariable, PyType?> = LinkedHashMap()

  /** Used to keep track of what bounds have already been incorporated */
  val incorporatedBounds: MutableSet<TypeBound> = LinkedHashSet()

  /** Incremental worklists */
  val pendingBounds: ArrayDeque<TypeBound> = ArrayDeque()
  val pendingInstantiations: ArrayDeque<InferenceVariable> = ArrayDeque()

  /** Avoids enqueueing the same instantiation repeatedly */
  private val queuedInstantiations: MutableSet<InferenceVariable> = HashSet()

  /** Identity-based IDs for bounds so we can memoize processed pairs cheaply */
  private val boundIds = IdentityHashMap<TypeBound, Int>()

  private var nextBoundId: Int = 1

  /** Memoization: each pair combined at most once */
  val seenBoundPairs: MutableSet<Long> = HashSet()

  /** True iff the argument appears on the left-hand side of one or more bounds */
  fun isBounded(infVar: InferenceVariable?): Boolean {
    return boundsPerInfVar.containsKey(infVar)
  }

  /** True iff an instantiation was recorded via method [.addBound] for the given inference variable. */
  fun isInstantiated(infVar: InferenceVariable?): Boolean {
    return infVar != null && instantiations.containsKey(infVar)
  }

  /** True iff the first argument is constrained by the second (irrespective of whether that second argument is instantiated). */
  fun dependsOnResolutionOf(infVar: InferenceVariable, candidate: InferenceVariable): Boolean {
    for (currBound in boundsPerInfVar.get(infVar)) {
      if (currBound.referencedInfVars.contains(candidate)) {
        return true
      }
    }
    return false
  }

  fun idOf(bound: TypeBound): Int {
    return boundIds.computeIfAbsent(bound) { nextBoundId++ }
  }

  /**
   * Removes the given type bound, identified by object identity.
   *
   * For internal use only.
   * Provided for performance reasons to allow removal of type bounds that do no longer have any effect, i.e., are redundant.
   */
  fun removeBound(bound: TypeBound) {
    bounds.remove(bound)
    boundsPerInfVar.remove(bound.left, bound)
    incorporatedBounds.remove(bound)
    for (ref in bound.referencedInfVars) {
      boundsReferencingInfVar.remove(ref, bound)
    }
  }

  fun addBound(left: InferenceVariable, right: PyType?, variance: Variance) {
    if (left == right) return
    val rightSubst = substitutePyTypeVarTypes(right, inferenceVars, context)
    val newBound = TypeBound(left, rightSubst, variance, context)
    val boundWasAdded = bounds.add(newBound)
    if (!boundWasAdded) return

    boundsPerInfVar.put(left, newBound)
    for (ref in newBound.referencedInfVars) {
      boundsReferencingInfVar.put(ref, newBound)
    }
    pendingBounds.addLast(newBound)

    if (variance == Variance.INVARIANT && isProper(rightSubst, context)) {
      val prev = instantiations.put(left, rightSubst)
      if (prev !== rightSubst && queuedInstantiations.add(left)) {
        pendingInstantiations.addLast(left)
      }
    }
  }

  fun savePreResolutionTypeBounds() {
    if (preResolutionBounds.isEmpty()) {
      preResolutionBounds.addAll(bounds)
    }
  }

  fun saveSolution() {
    solution.putAll(instantiations)
  }

  // TODO: add reason as parameter, e.g. the current stack of constraints
  fun fail() {
    failed = true
  }
}


/**
 * This element is based on an EPL-1.0-licensed Java source by NumberFour AG (2016).
 * (@link https://github.com/eclipse-n4js/n4js/blob/master/plugins/org.eclipse.n4js/src/org/eclipse/n4js/typesystem/constraints/Reducer.java).
 * Ported to Kotlin; structure and semantics remain substantially the same.
 */
private object ConstraintReducer {

  // TODO: add traceability e.g. by storing/stacking information about original constraints and before/after checking if the cp is failed
  fun reduce(constraint: TypeConstraint, cp: ConstraintProblem) {
    reduce(constraint.left, constraint.right, constraint.variance, cp)
  }

  private fun reduce(left: PyType?, right: PyType?, variance: Variance, cp: ConstraintProblem) {
    if (variance == Variance.INFER_VARIANCE) {
      cp.fail()
      return
    }

    if (isProper(left, cp.context) && isProper(right, cp.context)) {
      reduceProperType(left, right, variance, cp)
      return
    }

    if (left is PyUnsafeUnionType) {
      reduceDisjunction(right, left.members.filterNotNull(), variance.inverse(), cp)
      return
    }
    if (right is PyUnsafeUnionType) {
      reduceDisjunction(left, right.members.filterNotNull(), variance, cp)
      return
    }

    if (left is InferenceVariable) {
      cp.addBound(left, right, variance)
      return
    }
    if (right is InferenceVariable) {
      cp.addBound(right, left, variance.inverse())
      return
    }

    // check top and bottom types. Doing this up-front here simplifies the handling of other special cases
    val isBottomType = (if (variance === Variance.CONTRAVARIANT) right else left).isBottomType()
    val isTopType = (if (variance === Variance.CONTRAVARIANT) left else right).isTopType(cp.context)
    if (isBottomType || isTopType) {
      return
    }

    if (left is PyUnionType) {
      reduceComposedType(right, left, variance.inverse(), cp)
      return
    }
    if (right is PyUnionType) {
      reduceComposedType(left, right, variance, cp)
      return
    }
    if (left is PyIntersectionType) {
      reduceComposedType(right, left, variance.inverse(), cp)
      return
    }
    if (right is PyIntersectionType) {
      reduceComposedType(left, right, variance, cp)
      return
    }

    val isLeftStructural = isStructural(left, cp.context)
    val isRightStructural = isStructural(right, cp.context)
    if ((isLeftStructural && (variance == Variance.CONTRAVARIANT || variance == Variance.INVARIANT))
        || (isRightStructural && (variance == Variance.COVARIANT || variance == Variance.INVARIANT))) {
      throw NotSupportedException()
      // TODO: Support structural types. Test case see PyTypeTest#testDictCallOnDictLiteralResult
    }


    if (left is PyClassLikeType && right is PyClassLikeType) {
      reduceNominalType(left, right, variance, cp)
      return
    }

    if (left is PyCallableType && right is PyCallableType && left.isCallable && right.isCallable) {
      reduceCallableType(left, right, variance, cp)
      return
    }

    reduceProperType(left, right, variance, cp)
  }


  private fun reduceProperType(left: PyType?, right: PyType?, variance: Variance, cp: ConstraintProblem) {
    when (variance) {
      Variance.COVARIANT -> {
        if (!isSubtype(left, right, cp.context)) {
          cp.fail()
          return
        }
      }
      Variance.CONTRAVARIANT -> {
        if (!isSubtype(right, left, cp.context)) {
          cp.fail()
          return
        }
      }
      Variance.INVARIANT, Variance.BIVARIANT -> {
        if (!sameTypes(left, right, cp.context)) {
          cp.fail()
          return
        }
      }
      Variance.INFER_VARIANCE -> {
        UNREACHABLE()
      }
      Variance.BIVARIANT -> {}
    }
    // success
  }

  /** Reduction for two given callable types will be applied on all their parameters and their return type in a pair-wise manner. */
  private fun reduceCallableType(left: PyCallableType, right: PyCallableType, variance: Variance, cp: ConstraintProblem) {
    val lReturnType = left.getReturnType(cp.context)
    val rReturnType = right.getReturnType(cp.context)
    val lIsNone = lReturnType.isNoneType
    val rIsNone = rReturnType.isNoneType

    // derive constraints for types of parameters
    val lParams = left.getParameters(cp.context)
    val rParams = right.getParameters(cp.context)
    if (lParams != null && rParams != null) {
      val valueParsIt = rParams.iterator()
      for (keyPar in lParams) {
        if (valueParsIt.hasNext()) {
          val lParamType = keyPar.getType(cp.context)
          val rParamType = valueParsIt.next().getType(cp.context)
          if (lParamType != null && rParamType != null) {
            val paramVariance = variance.multiply(Variance.CONTRAVARIANT)
            reduce(lParamType, rParamType, paramVariance, cp)
          }
        }
      }
    }

    // derive constraints for return types
    if (lIsNone && rIsNone) {
      // semantics: both return None
      // success
    }
    else if ((variance == Variance.COVARIANT && rIsNone) ||
             (variance == Variance.CONTRAVARIANT && lIsNone)) {
      // semantics: ⟨ {function():α} <: {function():None} ⟩
      // success
    }
    else if (lIsNone || rIsNone) {
      // semantics: ⟨ {function():None} <: {function():α} ⟩
      // --> doomed, unless the non-None return value is optional
      val isRetValOpt = if (lIsNone) rReturnType.isOptional() else lReturnType.isOptional()
      if (!isRetValOpt) {
        cp.fail()
        return
      }
    }
    else {
      val retTypeVariance = variance.multiply(Variance.COVARIANT)
      reduce(lReturnType, rReturnType, retTypeVariance, cp)
    }
  }

  /**
   *  Reduction for parameterized type references according to nominal subtyping rules.
   *
   *  We have a situation like ⟨ G<IV> Φ G<string> ⟩ (with IV being an inference variable) which may result
   *  from code such as:
   *
   *  ```
   *  class G[T]: ...
   *  def f[IV](p: G[IV]) : ...
   *  gStr : G[str]
   *  f(gStr) # "expected type :> actual type" will lead to "G[IV] :> G[str]"
   *  ```
   *
   *  Resulting in the constraint "IV = string".
   *
   *  However, it is not always that simple. The type argument corresponding to the
   *  inference variable ('string' corresponding to 'IV' in the above example) might
   *  not be held by the ParameterizedTypeRef but may instead be contained somewhere
   *  in the inheritance hierarchy of 'value'. For example:
   *
   *  ```
   *  class G[T]: ...
   *  def f[IV](p: G[IV]) : ...
   *  class C(G[str]) : ...
   *  c : C
   *  f(c) # will lead to "G[IV] :> C"
   *  ```
   *
   *  Of course, the inheritance hierarchy might be arbitrarily complex.
   *
   *  Solution:
   *  We derive a constraint for the above situations in several steps
   *  Start with:
   *  ```
   *  G[IV] :> C
   *  ```
   *  Now, map each type argument of 'left' to corresponding type parameter of 'left':
   *  IV <-> T
   *  Then, perform type variable substitution on the current right-hand side ("T" in our example) based on the
   *  substitutions defined by the original right-hand side ("C" in our example):
   *  IV <-> string
   */
  private fun reduceNominalType(pLeft: PyType?, pRight: PyType?, pVariance: Variance, cp: ConstraintProblem) {
    if ((pVariance == Variance.COVARIANT && !isRawSubtype(pLeft, pRight, cp.context))
        || (pVariance == Variance.CONTRAVARIANT && !isRawSubtype(pRight, pLeft, cp.context))
        || (pVariance == Variance.INVARIANT && !(isRawSubtype(pLeft, pRight, cp.context) || isRawSubtype(pRight, pLeft, cp.context)))) {
      cp.fail()
    }

    val left: PyType?
    val right: PyType?
    if (pVariance == Variance.COVARIANT) {
      // normalize ⟨ B <: A ⟩ to ⟨ A :> B ⟩ to make sure the (raw) subtype is on the right-hand side
      left = pRight
      right = pLeft
    }
    else {
      left = pLeft
      right = pRight
    }

    // Here we check supertypes of C, see the example in KDoc above
    // G[IV] :> C
    val substitutions = PyTypeChecker.unifyReceiver(right, cp.context)
    val genericSubstitutions = PyTypeChecker.collectTypeSubstitutions(left as PyClassType, cp.context)
    for (tvMapping in genericSubstitutions.typeVars) {
      val typeVar = tvMapping.key
      if (tvMapping.value != null && substitutions.typeVars.containsKey(typeVar)) {
        val typeArg = tvMapping.value!!.get()
        val typeVarSubst = PyTypeChecker.substitute(typeVar, substitutions, cp.context)
        val defSiteVariance = if (typeVar.variance == Variance.INFER_VARIANCE)
          Variance.COVARIANT // TODO: introduce PyVarianceJudgment
        else
          typeVar.variance
        reduce(typeVarSubst, typeArg, defSiteVariance, cp)
      }
    }
  }


  private fun reduceComposedType(
    left: PyType?,
    right: PyCompositeType, // COMPOSED TYPE
    variance: Variance,
    cp: ConstraintProblem,
  ) {
    when (variance) {
      Variance.INVARIANT -> {
        reduceComposedType(left, right, Variance.COVARIANT, cp)
        reduceComposedType(left, right, Variance.CONTRAVARIANT, cp)
      }
      Variance.COVARIANT -> {
        if (right is PyUnionType) {
          // semantics: disjunction of right with each union member
          reduceDisjunction(left, right.members.filterNotNull(), variance, cp)
        }
        if (right is PyIntersectionType) {
          // semantics: conjunction of right with each union member
          for (member in right.members) {
            if (member != null) {
              reduce(left, member, variance, cp)
            }
          }
        }
      }
      Variance.CONTRAVARIANT -> {
        if (right is PyUnionType) {
          // semantics: conjunction of right with each intersection member
          for (member in right.members) {
            if (member != null) {
              reduce(left, member, variance, cp)
            }
          }
        }
        if (right is PyIntersectionType) {
          // semantics: disjunction of right with each intersection member
          reduceDisjunction(left, right.members.filterNotNull(), variance, cp)
        }
      }
      else -> {
        UNREACHABLE()
      }
    }
  }

  /**
   * Most tricky part. The goal is an implementation that works without back-tracking.
   * The problem this method solves is that the left type must be a subtype (wrt. invariance)
   * of one of the members of the right type. Depending on which right type member is used,
   * new bounds will be added to the constraint problem. If the resulting constraint problem
   * turns out to be unsolvable, there will be no back-tracking to try another type from
   * the given set of member types here. To decide which type is the most promising, this
   * method makes some assumptions taking structural properties into account.
   */
  private fun reduceDisjunction(left: PyType?, members: Collection<PyType>, variance: Variance, cp: ConstraintProblem) {
    if (members.isEmpty()) {
      return
    }
    if (members.size == 1) {
      reduce(left, members.first(), variance, cp)
      return
    }
    // if the left side is a ComposedType that would lead to a conjunction, we have to tear it apart
    // first to avoid incorrect results (for example, A|B <: [A, B, C] as a disjunction must be
    // handled as A <: [A, B, C] AND B <: [A, B, C], both as a disjunction), not as A|B <: X with
    // X being the choice of "most promising" option out of A, B, C).
    if ((variance == Variance.COVARIANT && left is PyUnionType) /* || (variance == Variance.CONTRAVARIANT && left is PyIntersectionType) */) {
      val leftMembers = left.members.filterNotNull()
      for (currLeft in leftMembers) {
        reduceDisjunction(currLeft, members, variance, cp)
      }
      return
    }

    // choose the "most promising" of the disjoint constraints and continue with that
    // (and simply ignore the other possible paths)
    val memberList = ArrayList(members)
    var idx = -1

    if (left is PyCallableType) {
      val bestMatchIdx = TreeMap<Int, Int>()
      for (i in memberList.indices) {
        val currRight: PyType? = memberList[i]
        val subType: PyType? = if (variance == Variance.CONTRAVARIANT) currRight else left
        val superType: PyType? = if (variance == Variance.CONTRAVARIANT) left else currRight
        if (isRawSubtype(subType, superType, cp.context)) {
          val currMatchCnt = computeMatchCount(left, memberList[i], variance, cp)
          bestMatchIdx[currMatchCnt] = i
        }
      }
      if (!bestMatchIdx.isEmpty()) {
        idx = bestMatchIdx.lastEntry().value
      }
    }
    if (left is InferenceVariable) {
      // choose the first that fits the current bounds
      for (i in memberList.indices) {
        if (mightSufficeExistingBounds(left, memberList[i], variance, cp)) {
          idx = i
          break
        }
      }
    }
    if (idx == -1) {
      // choose the first naked inference variable (if present)
      idx = memberList.indexOfFirst { it is InferenceVariable }
    }
    if (idx == -1 && variance == Variance.COVARIANT) {
      // choose the top type 'any' or one of the pseudo-top types: Object, N4Object
      idx = memberList.indexOfFirst { it.isTopType(cp.context) }
    }
    if (idx == -1 && variance == Variance.CONTRAVARIANT) {
      // choose the bottom type 'undefined' or one of the pseudo-bottom types: null
      idx = memberList.indexOfFirst { it.isBottomType() }
    }
    if (idx == -1) {
      // choose the first member type (yes, we're pretty desperate at this point)
      idx = 0
    }
    return reduce(left, memberList[idx], variance, cp)
  }

  private fun computeMatchCount(left: PyType?, right: PyType?, variance: Variance, cp: ConstraintProblem): Int {
    var matchCount = 1000

    if (left is PyClassType && right is PyClassType && left.pyClass === right.pyClass) {
      matchCount += 1000

      if (left is PyCollectionType && right is PyCollectionType) {
        val lTArgs = left.getElementTypes()
        val rTArgs = right.getElementTypes()
        if (lTArgs.size == rTArgs.size) {
          matchCount += 100
          for (i in lTArgs.indices) {
            if (mightSufficeExistingBounds(lTArgs[i], rTArgs[i], variance, cp)) {
              matchCount += 10
            }
          }
        }
      }
      return matchCount
    }

    val superType = if (variance == Variance.COVARIANT) left else right
    val subType = if (variance == Variance.COVARIANT) right else left
    if (superType is PyClassLikeType) {
      // choose the first supertype of left
      val lSTs = superType.getAncestorTypes(cp.context)
      for (leftSuperType in lSTs) {
        if (leftSuperType == subType) {
          return matchCount
        }
        matchCount--
      }
    }
    if (left is PyCallableType && right is PyCallableType) {
      return 100
    }
    if (left is PyStructuralType && right is PyStructuralType) {
      return 100
    }
    return 0
  }

  private fun mightSufficeExistingBounds(left: PyType?, right: PyType?, variance: Variance, cp: ConstraintProblem): Boolean {
    var left = left
    var right = right
    var variance = variance
    if (left !is InferenceVariable) {
      if (right !is InferenceVariable) {
        return false
      }
      val tmp = left
      left = right
      right = tmp
      variance = variance.inverse()
    }
    // left is InferenceVariable

    if (right is PyType) {
      val newTypeBound = TypeBound(left, right, variance, cp.context)
      val newConstraints: List<TypeConstraint> = TypeBoundCombiner.combineAll(newTypeBound, cp)
      for (newConstraint in newConstraints) {
        if (newConstraint.variance === Variance.CONTRAVARIANT) {
          if (!isRawSubtype(newConstraint.right, newConstraint.left, cp.context)) {
            return false
          }
        }
        else if (newConstraint.variance === Variance.COVARIANT) {
          if (!isRawSubtype(newConstraint.left, newConstraint.right, cp.context)) {
            return false
          }
        }
      }
      return true
    }
    return false
  }

}

/**
 * This element is based on an EPL-1.0-licensed Java source by NumberFour AG (2016)
 * (@link https://github.com/eclipse-n4js/n4js/blob/master/plugins/org.eclipse.n4js/src/org/eclipse/n4js/typesystem/constraints/TypeBoundCombiner.java).
 * Ported to Kotlin; structure and semantics remain substantially the same.
 */
private object TypeBoundCombiner {

  /**
   * Side effect free, i.e., no bounds will be removed from this [ConstraintProblem] (as an optimization) when calling
   * this method.
   */
  fun combineAll(newBound: TypeBound, cp: ConstraintProblem): List<TypeConstraint> {
    val result = ArrayList<TypeConstraint>()
    val bounds = cp.boundsPerInfVar.get(newBound.left)
    for (tBound in bounds) {
      val newConstraint = combine(newBound, tBound, null, cp.context)
      if (newConstraint != null) {
        result.add(newConstraint)
      }
    }
    return result
  }

  /**
   * In terms of JLS8, this method embodies the implication rules listed in Sec. 18.3.1 (the other implication rules
   * in JLS8 take as input capture conversion constraints).
   */
  fun combine(boundI: TypeBound, boundJ: TypeBound, cp: ConstraintProblem?, context: TypeEvalContext): TypeConstraint? {
    when (boundI.variance) {
      Variance.INVARIANT -> when (boundJ.variance) {
        Variance.INVARIANT -> return combineInvInv(boundI, boundJ, cp, context)
        Variance.COVARIANT, Variance.CONTRAVARIANT -> return combineInvVar(boundI, boundJ, cp, context)
        else -> {
          UNREACHABLE()
        }
      }
      Variance.COVARIANT -> when (boundJ.variance) {
        Variance.INVARIANT -> return combineInvVar(boundJ, boundI, cp, context) // note: reversed arguments!
        Variance.CONTRAVARIANT -> return combineContraCo(boundJ, boundI) // note: reversed arguments!
        Variance.COVARIANT -> return combineBothCoOrBothContra(boundI, boundJ)
        else -> {
          UNREACHABLE()
        }
      }
      Variance.CONTRAVARIANT -> when (boundJ.variance) {
        Variance.INVARIANT -> return combineInvVar(boundJ, boundI, cp, context) // note: reversed arguments!
        Variance.COVARIANT -> return combineContraCo(boundI, boundJ)
        Variance.CONTRAVARIANT -> return combineBothCoOrBothContra(boundI, boundJ)
        else -> {
          UNREACHABLE()
        }
      }
      else -> {
        UNREACHABLE()
      }
    }
  }

  /**
   * Case: both bounds are equalities.
   */
  private fun combineInvInv(boundS: TypeBound, boundT: TypeBound, cp: ConstraintProblem?, context: TypeEvalContext): TypeConstraint? {
    if (boundS.left === boundT.left) {
      // `α = S` and `α = T` implies `S = T`
      return TypeConstraint(boundS.right, boundT.right, Variance.INVARIANT)
    }
    // inference variables are different
    // -> try to substitute a proper RHS in the RHS of the other bound, to make it a proper type itself
    var newConstraint = combineInvInvWithProperType(boundS, boundT, cp, context)
    if (newConstraint != null) {
      return newConstraint
    }
    newConstraint = combineInvInvWithProperType(boundT, boundS, cp, context)
    if (newConstraint != null) {
      return newConstraint
    }
    return null
  }

  /**
   * Given two type bounds `α = U` and `β = T` with α ≠ β, will return a new constraint `β = T[α:=U]` if
   *
   *  * U is proper, and
   *  * T mentions α.
   *
   * Otherwise, `null` is returned.
   */
  private fun combineInvInvWithProperType(
    boundWithProperRHS: TypeBound,
    boundOther: TypeBound,
    cp: ConstraintProblem?,
    context: TypeEvalContext,
  ): TypeConstraint? {

    val alpha = boundWithProperRHS.left
    val typeU = boundWithProperRHS.right
    val typeT = boundOther.right
    if (isProper(typeU, context) && boundOther.referencedInfVars.contains(alpha)) {
      val beta = boundOther.left
      val substT = substituteInferenceVariable(typeT, alpha, typeU, context) // returns T[α:=U]
      cp?.removeBound(boundOther)
      return TypeConstraint(beta, substT, Variance.INVARIANT)
    }
    return null
  }

  /**
   * Case: first bound is an equality, while the second isn't: `α = S` and `β Φ T` with Φ either `<:` or `:>`.
   */
  private fun combineInvVar(boundS: TypeBound, boundT: TypeBound, cp: ConstraintProblem?, context: TypeEvalContext): TypeConstraint? {
    val alpha = boundS.left
    val beta = boundT.left
    val typeS = boundS.right
    val typeT = boundT.right
    val phi = boundT.variance
    if (alpha === beta) {
      // (1) `α = S` and `α Φ T` implies `S Φ T`
      return TypeConstraint(typeS, typeT, phi)
    }
    // both bounds have different inference variables, i.e., α != β
    if (alpha === typeT) {
      // (2) `α = S` and `β Φ α` implies `β Φ S`
      return TypeConstraint(beta, typeS, phi)
    }
    if (typeS is InferenceVariable) {
      // the first bound is of the form `α = γ` (with γ being another inference variable)
      val gamma = typeS
      if (gamma === beta) {
        // (3) `α = β` and `β Φ T` implies `α Φ T`
        return TypeConstraint(alpha, typeT, phi)
      }
      if (gamma === typeT) {
        // (4) `α = γ` and `β Φ γ` implies `β Φ α`
        return TypeConstraint(beta, alpha, phi)
      }
    }
    // so, S is not an inference variable
    if (isProper(typeS, context) && boundT.referencedInfVars.contains(alpha)) {
      // (5) `α = S` (where S is proper) and `β Φ T` implies `β Φ T[α:=U]`
      val substT = substituteInferenceVariable(typeT, alpha, typeS, context) // returns T[α:=U]
      cp?.removeBound(boundT)
      return TypeConstraint(beta, substT, phi)
    }
    return null
  }

  /**
   * Case: `α :> S` and `β <: T`.></:>
   */
  private fun combineContraCo(boundS: TypeBound, boundT: TypeBound): TypeConstraint? {
    val alpha: InferenceVariable = boundS.left
    val beta: InferenceVariable = boundT.left
    val typeS = boundS.right
    val typeT = boundT.right
    if (alpha === beta) {
      // transitivity, using LHS as bridge:
      // α :> S and α <: T implies S <: T
      return TypeConstraint(typeS, typeT, Variance.COVARIANT)
    }
    // so, α and β are different
    if (typeS is InferenceVariable) {
      val gamma: InferenceVariable = typeS
      if (gamma === typeT) {
        // transitivity, using RHS as bridge:
        // α :> γ and β <: γ implies α :> β
        return TypeConstraint(alpha, beta, Variance.CONTRAVARIANT)
      }
    }
    return null
  }

  /**
   * Case: inequalities of the same direction, i.e. `α <: S ^ β <: T`, or `α :> S ^ β :> T`.
   */
  private fun combineBothCoOrBothContra(boundS: TypeBound, boundT: TypeBound): TypeConstraint? {
    val alpha: InferenceVariable = boundS.left
    val beta: InferenceVariable = boundT.left
    val typeS = boundS.right
    val typeT = boundT.right
    if (alpha === typeT) {
      // α <: S and β <: α implies β <: S
      // α :> S and β :> α implies β :> S
      return TypeConstraint(beta, typeS, boundS.variance)
    }
    if (typeS === beta) {
      // α <: β and β <: T implies α <: T
      // α :> β and β :> T implies α :> T
      return TypeConstraint(alpha, typeT, boundS.variance)
    }
    return null
  }
}


private object TypeBoundIncorporator {

  /**
   * Incremental incorporation:
   * - Apply newly discovered instantiations only to bounds that reference them
   * - Combine only pairs that are "relevant" to newly added bounds
   * - Never do full quadratic passes unless the problem genuinely introduces O(B^2) unique pairs
   */
  fun incorporate(cp: ConstraintProblem, context: TypeEvalContext) {
    if (cp.failed) return

    // Process until fixpoint: queues become empty.
    while (!cp.failed && (cp.pendingInstantiations.isNotEmpty() || cp.pendingBounds.isNotEmpty())) {
      // 1) Apply new instantiations to affected bounds (targeted, not global)
      processPendingInstantiations(cp, context)
      if (cp.failed) return

      // 2) Incorporate newly added bounds (incremental pairwise combination)
      processPendingBounds(cp, context)
    }
  }

  /** Substitute newly instantiated inference variables into bounds that mention them. */
  private fun processPendingInstantiations(cp: ConstraintProblem, context: TypeEvalContext) {
    while (!cp.failed && cp.pendingInstantiations.isNotEmpty()) {
      val instantiatedVar = cp.pendingInstantiations.removeFirst()
      if (instantiatedVar !in cp.instantiations) continue

      // Copy because we'll remove bounds while iterating.
      val impactedBounds = ArrayList(cp.boundsReferencingInfVar.get(instantiatedVar))

      for (bound in impactedBounds) {
        if (cp.failed) return
        // Bound might have been removed already by another substitution
        if (!cp.bounds.contains(bound)) continue

        // Substitute *all currently instantiated vars* mentioned by this bound, not just the one we popped.
        var newRight = bound.right
        for (ref in bound.referencedInfVars) {
          val refInst = cp.instantiations[ref] ?: continue
          newRight = substituteInferenceVariable(newRight, ref, refInst, context)
        }

        // If nothing changed, skip.
        if (newRight === bound.right) continue

        // Remove the old bound and re-reduce as a constraint.
        cp.removeBound(bound)
        reduce(TypeConstraint(bound.left, newRight, bound.variance), cp)
      }
    }
  }

  /**
   * For each newly added bound b, combine it only with "relevant" bounds and reduce any implied constraints.
   * Each unordered pair is combined at most once globally (seenBoundPairs).
   */
  private fun processPendingBounds(cp: ConstraintProblem, context: TypeEvalContext) {
    while (!cp.failed && cp.pendingBounds.isNotEmpty()) {
      val boundI = cp.pendingBounds.removeFirst()
      if (!cp.bounds.contains(boundI)) continue // stale entry

      val idI = cp.idOf(boundI)

      // Build a small candidate set rather than "all bounds":
      // - bounds with the same LHS (common case)
      // - bounds whose LHS is referenced by RHS of I
      // - bounds that reference I.left on their RHS (enables bridge rules)
      val candidates = LinkedHashSet<TypeBound>()

      candidates.addAll(cp.boundsPerInfVar.get(boundI.left))

      for (ref in boundI.referencedInfVars) {
        candidates.addAll(cp.boundsPerInfVar.get(ref))
      }

      candidates.addAll(cp.boundsReferencingInfVar.get(boundI.left))

      // Combine boundI with each candidate boundJ exactly once.
      for (boundJ in candidates) {
        if (cp.failed) return
        if (boundJ === boundI) continue
        if (!cp.bounds.contains(boundJ)) continue

        val idJ = cp.idOf(boundJ)

        val pairKey = pairKey(idI, idJ)
        if (!cp.seenBoundPairs.add(pairKey)) continue // already processed

        val newConstraint = TypeBoundCombiner.combine(boundI, boundJ, cp, context)
        if (newConstraint != null) {
          reduce(newConstraint, cp)
        }
      }
    }
  }

  /** Pack an unordered pair of 32-bit ids into a single Long key. */
  private fun pairKey(a: Int, b: Int): Long {
    val lo = minOf(a, b)
    val hi = maxOf(a, b)
    return (hi.toLong() shl 32) or (lo.toLong() and 0xFFFF_FFFFL)
  }
}


/**
 * This element is based on an EPL-1.0-licensed Java source by NumberFour AG (2016)
 * (@link https://github.com/eclipse-n4js/n4js/blob/master/plugins/org.eclipse.n4js/src/org/eclipse/n4js/typesystem/constraints/InferenceContext.java).
 * Ported to Kotlin; structure and semantics remain substantially the same.
 */
private object TypeBoundResolver {

  /**
   * Performs resolution of all inference variables of the receiving inference context. This might trigger further
   * reduction and incorporation steps.
   *
   * If a solution is found, [ConstraintProblem.bounds] will contain instantiations for all inference variables,
   * and [ConstraintProblem.solution] will contain instantiations for all inference variables. Otherwise, [ConstraintProblem.bounds]
   * will be in an undefined state, and [ConstraintProblem.solution] will be `null`. In both cases,
   * [ConstraintProblem.preResolutionBounds] will contain the state of [ConstraintProblem.bounds] right before resolution started.
   */
  fun resolve(cp: ConstraintProblem, context: TypeEvalContext) {
    cp.savePreResolutionTypeBounds()
    if (cp.failed) {
      return
    }
    while (true) {
      val currVariableSet = getSmallestVariableSet(cp, context) ?: break

      for (currVariable in currVariableSet) {
        val instantiation = chooseInstantiation(cp, currVariable, context)
        instantiate(cp, currVariable, instantiation, context)
        if (cp.failed) {
          return
        }
      }

      TypeBoundIncorporator.incorporate(cp, context)
      if (cp.failed) {
        return
      }
    }
    cp.saveSolution()
    return
  }

  /**
   * Given a set `infVars` of inference variables of the receiving inference context, this method returns
   * the smallest, non-empty set S such that:
   *
   *  * all α ∈ S are uninstantiated inference variables of the receiving inference context, and
   *  * one of the elements in S is also in `infVars`: ∃ α ∈ S: α ∈ `infVars`, and
   *  * if an α ∈ S depends on the resolution of another variable β, then either β is instantiated or β ∈ S, and
   *  * there exists no non-empty proper subset of S with this property.
   *
   * Returns `null` if no such set exists, e.g., because `infVars` was empty or did not contain
   * any uninstantiated inference variables.
   *
   * The returned set, if non-null, is guaranteed to be non-empty.
   */
  private fun getSmallestVariableSet(cp: ConstraintProblem, context: TypeEvalContext): MutableSet<InferenceVariable>? {
    var result: MutableSet<InferenceVariable>? = null
    val deferred = HashSet<InferenceVariable>()
    var min = Int.MAX_VALUE
    val infVars = cp.inferenceVars.values()
    for (currentVariable in infVars) {
      if (cp.isInstantiated(currentVariable)) {
        continue
      }
      // Defer an unbounded currentVariable IFF all other iv that depend on currentVariable have
      // proper bounds AND only proper bounds.
      //
      // For example,
      // α :> β
      // α :> A
      // Because α depends on β, we would first instantiate β (to any) and then choose α=intersection[A, Any].
      // With the following code, we defer processing of β until α has been instantiated to A.

      if (!cp.isBounded(currentVariable)) {
        val gotAtLeastOneDependantIV = infVars.any { iv: InferenceVariable -> cp.dependsOnResolutionOf(iv, currentVariable) }
        if (gotAtLeastOneDependantIV) {
          val defer = infVars
            .filter { iv: InferenceVariable -> cp.dependsOnResolutionOf(iv, currentVariable) }
            .all { iv: InferenceVariable ->
              val bs = cp.boundsPerInfVar.get(iv).filter { b -> !(b.left === iv && b.right === currentVariable) }
              !bs.isEmpty() && bs.all { b: TypeBound -> isProper(b.right, context) }
            }
          if (defer) {
            deferred.add(currentVariable)
            continue
          }
        }
      }

      val set = LinkedHashSet<InferenceVariable>()
      if (addDependencies(cp, currentVariable, min, set, context)) {
        val curr = set.size
        if (curr == 1) {
          return set // 'set' contains only currentVariable -> no need to remove deferred variables
        }
        if (curr < min) {
          result = set
          min = curr
        }
      }
    }
    if (result.isNullOrEmpty()) {
      return null
    }

    // deferred variables may have been added via #addDependencies() above, so remove them
    // note: because deferred variables can only end up in 'result' via #addDependencies(), we can safely
    // remove all deferred variables and be sure that 'result' won't be empty afterward
    result.removeAll(deferred)

    return result
  }

  /**
   * **IMPORTANT:** Given inference variable `infVar` must be uninstantiated; this will *not* be checked
   * (again) by this method!
   *
   * For the given, uninstantiated inference variable `infVar`, this method will add to the given
   * answer-set `addHere` the transitive closure of all uninstantiated inference variables of the receiving
   * inference context on which `infVar` depends directly or indirectly.
   *
   * In addition, a `limit` for the answer-set's size is supplied, which means the collection of
   * dependencies will be interrupted immediately when `addHere`'s size reaches the given limit.
   *
   * Returns `true` on success or `false` if the collection of dependencies was interrupted due to
   * reaching the `limit`. In the latter case, the answer-set will be in an invalid state (contains a
   * random subset of the transitive closure of dependencies) and should be discarded.
   */
  private fun addDependencies(
    cp: ConstraintProblem,
    infVar: InferenceVariable,
    limit: Int,
    addHere: MutableSet<InferenceVariable>,
    context: TypeEvalContext,
  ): Boolean {

    if (addHere.size >= limit) {
      // the candidate answer-set is already not better than the best-yet answer-set (i.e., larger or same size)
      return false
    }
    // add infVar
    if (!addHere.add(infVar)) {
      // candidate variable was already visited for this answer-set; we assume also its dependencies have already
      // been added -> so nothing else to be done here
      return true
    }
    // for all (uninstantiated) variables on which the current one depends, recurse
    for (bound in cp.boundsPerInfVar.get(infVar)) {
      for (dependencyInfVar in bound.referencedInfVars) {
        if (!(cp.isInstantiated(dependencyInfVar))) {
          if (!addDependencies(cp, dependencyInfVar, limit, addHere, context)) {
            // a single uninstantiated variable too many (that currentVariable depends on) causes the current
            // answer-set to be discarded
            return false
          }
        }
      }
    }
    return true
  }

  /** Add bound `variable = proper` and perform reduction (which might trigger incorporation). */
  private fun instantiate(cp: ConstraintProblem, infVar: InferenceVariable, proper: PyType?, context: TypeEvalContext) {
    assert(!(cp.isInstantiated(infVar))) { "attempt to re-instantiate var " + infVar.name }
    assert(isProper(proper, context))
    // add bound `infVar = proper`
    reduce(TypeConstraint(infVar, proper, Variance.INVARIANT), cp)
    // check if 'proper' was accepted by BoundSet 'currentBounds' as an instantiation/solution of 'infVar'
    if (!cp.isInstantiated(infVar)) {
      // No! This should never happen except if 'proper' is not a proper type (and the above assertions are turned off).
      // OR except if 'proper' is a raw type, which is invalid but may occur when dealing with corrupted data (broken AST, etc.).
      // Since 'proper' was intended as an instantiation/solution when this method is called, not having it
      // accepted is a problem, and we should give up on this constraint system:
      cp.fail()
    }
  }

  /**
   * Returns a valid solution for the given inference variable **without** taking into account any dependencies it
   * might have on other inference variables. I.e., this method will consider only [TypeBound]s where the LHS is
   * the given inference variable and the RHS is a proper type.
   *
   * Background: this method is safe to use even for an inference variable α with a dependency on another inference
   * variable β iff β has already been instantiated and its instantiation has been properly reduced and incorporated,
   * because during reduction/incorporation a type bound α <: P or α >: P or α = P (with P being a proper type)
   * will have been created that reflects the impact of the instantiation of β on α (without mentioning β).
   */
  @Suppress("SENSELESS_COMPARISON") // All cases are declared explicitly for systematic reasons
  private fun chooseInstantiation(cp: ConstraintProblem, infVar: InferenceVariable, context: TypeEvalContext): PyType? {
    val lowerBounds = collectLowerBounds(cp, infVar, context)
    val upperBounds = collectUpperBounds(cp, infVar, context)

    if (lowerBounds.isEmpty() && upperBounds.isEmpty()) {
      // neither lower nor upper bounds found -> typeVar is unconstrained
      // val defaultType = infVar.typeVariable.defaultType?.get()
      // return defaultType // default or Any type
      // TODO: The above/out-commented code is the correct solution for this case.
      // However, at the moment, we do not yet support combined CSP solving of nested
      // call sites (example see PyTypingTest#testDecoratorWithArgumentCalledAsFunction()).
      // To enable constraint solving on the outer call site, we return a [PyUnconstrainedTypeVariable]
      // here, that will be substituted with its [PyTypeVarType] later.
      return PyUnconstrainedTypeVariable(infVar.typeVariable)
    }
    else if (lowerBounds.isNotEmpty() && upperBounds.isEmpty()) {
      val lowerBoundsWidened = lowerBounds.map { lowerBound -> PyLiteralType.upcastLiteralToClass(lowerBound) }
      return PyUnionType.union(lowerBoundsWidened)
    }
    else if (lowerBounds.isEmpty() && upperBounds.isNotEmpty()) {
      // It is debatable whether we should just return PyIntersectionType.intersection(*upperBounds)
      // Note however that intersection types are not part of Python (as of 2026).
      // Hence, the following logic makes it mandatory that the user declares a common subtype at some point.
      val commonSubtype = findCommonSubtype(context, *upperBounds)
      if (commonSubtype == null) {
        cp.fail()
        return PyNeverType.NEVER
      }
      else {
        return commonSubtype.get()
      }
    }
    else {
      if (infVar.typeVariable.constraints.isEmpty()) {
        val lowerBoundsWidened = lowerBounds.map { lowerBound -> PyLiteralType.upcastLiteralToClass(lowerBound) }
        val mergedBounds = lowerBoundsWidened.filter { lowerBound -> isSubtypeOfAll(context, lowerBound, *upperBounds) }
        return PyUnionType.union(mergedBounds)
      }
      else {
        val validConstraintMembers = infVar.typeVariable.constraints.filter { tvConstraint ->
          isSubtypeOfAll(context, tvConstraint, *upperBounds) && isSupertypeOfAll(context, tvConstraint, *lowerBounds)
        }
        if (validConstraintMembers.isEmpty()) {
          cp.fail()
          return PyNeverType.NEVER
        }
        if (validConstraintMembers.size > 1) {
          // we found multiple solutions. Use bounds as indicators for the chosen member type(s).
          val membersIndicatedByLBs = validConstraintMembers intersect lowerBounds.toSet()
          val membersIndicatedByUBs = validConstraintMembers intersect upperBounds.toSet()
          val membersIndicatedByABs = membersIndicatedByLBs + membersIndicatedByUBs
          if (membersIndicatedByABs.isNotEmpty()) {
            return PyUnionType.union(membersIndicatedByABs)
          }
        }
        return PyUnionType.union(validConstraintMembers)
      }
    }
  }

  /**
   * Return all lower bounds of the given inference variable, i.e., all type references `TR` appearing as RHS of bounds
   * of the form `infVar :> TR` or `infVar = TR`.
   */
  private fun collectLowerBounds(cp: ConstraintProblem, infVar: InferenceVariable, context: TypeEvalContext): Array<PyType?> {
    return collectBounds(cp, infVar, context) { b: TypeBound ->
      (b.variance === Variance.INVARIANT) || (b.variance === Variance.CONTRAVARIANT && b.right != null)
    }
  }

  /**
   * Return all upper bounds of the given inference variable, i.e., all type references `TR` appearing as RHS of bounds
   * of the form `infVar <: TR` or `infVar = TR`.
   */
  private fun collectUpperBounds(cp: ConstraintProblem, infVar: InferenceVariable, context: TypeEvalContext): Array<PyType?> {
    return collectBounds(cp, infVar, context) { b: TypeBound ->
      (b.variance === Variance.INVARIANT) || (b.variance === Variance.COVARIANT && !b.right.isTopType(context))
    }
  }

  private fun collectBounds(
    cp: ConstraintProblem,
    infVar: InferenceVariable,
    context: TypeEvalContext,
    predicate: (TypeBound) -> Boolean,
  ): Array<PyType?> {

    val infVarBounds = cp.boundsPerInfVar.get(infVar)
    val result = ArrayList<PyType?>()
    for (bound in infVarBounds) {
      if (!predicate(bound)) {
        continue
      }
      val boundType = bound.right
      if (boundType is InferenceVariable) {
        continue
      }
      // It may happen that inference variables depend on each other, hence create a cycle
      // In this case it is necessary to substitute these inference variables by Any type
      val substitutedBoundType = substituteInferenceVariablesBy(boundType, true, false, context)
      result.add(substitutedBoundType)
    }
    return result.toTypedArray()
  }

  private fun isSubtypeOfAll(context: TypeEvalContext, left: PyType?, vararg rights: PyType?): Boolean {
    for (right in rights) {
      if (!isSubtype(left, right, context)) {
        return false
      }
    }
    return true
  }

  private fun isSupertypeOfAll(context: TypeEvalContext, left: PyType?, vararg rights: PyType?): Boolean {
    for (right in rights) {
      if (!isSubtype(right, left, context)) {
        return false
      }
    }
    return true
  }

  /** Returns null iff there is no common supertype */
  fun findCommonSubtype(context: TypeEvalContext, vararg types: PyType?): Ref<PyType?>? {
    if (types.isEmpty()) return null
    if (types.size == 1) return Ref.create(types.first())

    val it = types.iterator()
    var candidate = it.next()
    while (it.hasNext()) {
      val type = it.next()
      if (type === candidate) continue

      when {
        isSubtype(candidate, type, context) -> {
          // the candidate is ok
        }
        isSubtype(type, candidate, context) -> {
          candidate = type
        }
        else -> {
          // Incomparable candidate. We might fail verification and then fall back.
        }
      }
    }

    // Verify that the candidate is a supertype of all inputs.
    for (type in types) {
      if (!isSubtype(candidate, type, context)) {
        return null
      }
    }
    return Ref.create(candidate)
  }
}

private object SubtypeJudgement {

  /** True iff left is a subtype of right. Inference variables, type variables, and alike are replaced by Any */
  fun isRawSubtype(left: PyType?, right: PyType?, context: TypeEvalContext): Boolean {
    // Replace [InferenceVariable]s by their type variables
    val leftNoIVs = substituteInferenceVariablesBy(left, false, true, context)
    val rightNoIVs = substituteInferenceVariablesBy(right, false, true, context)

    // Collect all type variables from both sides, transitively
    val typeVars = LinkedHashMap<PyTypeParameterType, PyType?>()
    val typeVarSubstitutionsCollector: PyTypeTraverser = object : PyTypeTraverser() {
      override fun visitPyType(type: PyType): PyRecursiveTypeVisitor.Traversal {
        if (type is PyTypeVarType) {
          val upperBound = type.getEffectiveBound()
          typeVars[type] = upperBound
        }
        return PyRecursiveTypeVisitor.Traversal.CONTINUE
      }
    }
    leftNoIVs?.acceptTypeVisitor(typeVarSubstitutionsCollector)
    rightNoIVs?.acceptTypeVisitor(typeVarSubstitutionsCollector)

    val leftProper = if (leftNoIVs is PyUnconstrainedTypeVariable) leftNoIVs.typeVariable.defaultType?.get() else leftNoIVs
    val rightProper = if (rightNoIVs is PyUnconstrainedTypeVariable) rightNoIVs.typeVariable.defaultType?.get() else rightNoIVs


    return PyTypeChecker.match(rightProper, leftProper, context, typeVars)
  }

  /**
   * Returns true iff typeA <: typeB and typeB></:> <: typeA.></:>
   */
  fun sameTypes(typeA: PyType?, typeB: PyType?, context: TypeEvalContext): Boolean {
    return isSubtype(typeA, typeB, context) && isSubtype(typeB, typeA, context)
  }

  /** True iff left is a subtype of right */
  fun isSubtype(left: PyType?, right: PyType?, context: TypeEvalContext): Boolean {
    val leftProper = if (left is PyUnconstrainedTypeVariable) left.typeVariable.defaultType?.get() else left
    val rightProper = if (right is PyUnconstrainedTypeVariable) right.typeVariable.defaultType?.get() else right
    return PyTypeChecker.match(rightProper, leftProper, context)
  }

}

/**
 * Returns a copy of `typeRef` in which `infVar` is substituted by `typeArg` or `typeRef`
 * itself if no change has occurred.
 *
 * @return typeRef[infVar:=typeArg]
 */
private fun substituteInferenceVariable(typeRef: PyType?, infVar: InferenceVariable, typeArg: PyType?, context: TypeEvalContext): PyType? {
  if (typeRef == null || typeRef == typeArg) return typeRef

  // Fast path: if the type doesn't reference the inference variable, return as is
  var referencesInfVar = false
  PyRecursiveTypeVisitor.traverse(typeRef, context, object : PyTypeTraverser() {
    override fun visitPyType(type: PyType): PyRecursiveTypeVisitor.Traversal {
      if (type == infVar) {
        referencesInfVar = true
        return PyRecursiveTypeVisitor.Traversal.TERMINATE
      }
      return PyRecursiveTypeVisitor.Traversal.CONTINUE
    }
  })
  if (!referencesInfVar) return typeRef

  return PyCloningTypeVisitor.clone(typeRef, object : PyCloningTypeVisitor(context) {
    override fun visitPyType(type: PyType): PyType? {
      if (type == infVar) {
        return typeArg
      }
      return super.visitPyType(type)
    }
  })
}

private fun substituteInferenceVariablesBy(typeRef: PyType?, byAny: Boolean, byTypeVar: Boolean, context: TypeEvalContext): PyType? {
  if (typeRef == null || (!byAny && !byTypeVar)) return typeRef

  var referencesInfVar = false
  PyRecursiveTypeVisitor.traverse(typeRef, context, object : PyTypeTraverser() {
    override fun visitPyType(type: PyType): PyRecursiveTypeVisitor.Traversal {
      if (type is InferenceVariable) {
        referencesInfVar = true
        return PyRecursiveTypeVisitor.Traversal.TERMINATE
      }
      return PyRecursiveTypeVisitor.Traversal.CONTINUE
    }
  })
  if (!referencesInfVar) return typeRef

  return PyCloningTypeVisitor.clone(typeRef, object : PyCloningTypeVisitor(context) {
    override fun visitPyType(type: PyType): PyType? {
      // substitute inference variables by Any
      if (type is InferenceVariable) {
        if (byAny) {
          return null
        }
        if (byTypeVar) {
          return type.typeVariable
        }
      }
      return super.visitPyType(type)
    }
  })
}


private fun isProper(type: PyType?, context: TypeEvalContext): Boolean {
  return collectInferenceVariables(type, context).isEmpty()
}

private fun isStructural(type: PyType?, context: TypeEvalContext): Boolean {
  return type is PyClassLikeType && type.isProtocol(context)
}

private fun collectInferenceVariables(root: PyType?, context: TypeEvalContext): Set<InferenceVariable> {
  val result = HashSet<InferenceVariable>()
  if (root is InferenceVariable) {
    result.add(root)
  }
  else {
    PyRecursiveTypeVisitor.traverse(root, context, object : PyTypeTraverser() {
      override fun visitPyType(type: PyType): PyRecursiveTypeVisitor.Traversal {
        if (type is InferenceVariable) {
          result.add(type)
        }
        return PyRecursiveTypeVisitor.Traversal.CONTINUE
      }
    })
  }
  return result
}

private fun substituteByInferenceVars(
  constraints: Collection<TypeConstraint>,
  inferenceVars: InferenceVariablePool,
  context: TypeEvalContext,
): LinkedHashSet<TypeConstraint> {
  val newConstraints = LinkedHashSet<TypeConstraint>()
  for (constraint in constraints) {
    val newLeft = substitutePyTypeVarTypes(constraint.left, inferenceVars, context)
    val newRight = substitutePyTypeVarTypes(constraint.right, inferenceVars, context)
    newConstraints.add(TypeConstraint(newLeft, newRight, constraint.variance))
  }
  return newConstraints
}

private fun substitutePyTypeVarTypes(original: PyType?, inferenceVars: InferenceVariablePool, context: TypeEvalContext): PyType? {
  if (original == null) {
    return original
  }
  return PyCloningTypeVisitor.clone(original, object : PyCloningTypeVisitor(context) {
    override fun visitPyClassType(classType: PyClassType): PyType {
      return normalizeType(classType, context) ?: classType
    }

    override fun visitPyTypeVarType(typeVarType: PyTypeVarType): PyType? {
      // substitute inference variables
      if (inferenceVars.contains(typeVarType)) {
        if (typeVarType.isDefinition) {
          throw NotSupportedException()
        }
        return inferenceVars.get(typeVarType)!!
      }
      return super.visitPyTypeVarType(typeVarType)
    }
  })
}

/** Returns true iff this type is Any or object */
private fun PyType?.isTopType(context: TypeEvalContext): Boolean {
  if (this == null) return true // Any

  val cls = this as? PyClassType ?: return false
  val objectType = PyBuiltinCache.getInstance(cls.pyClass).objectType
  return objectType != null && sameTypes(cls, objectType, context)
}

private fun PyType?.isBottomType(): Boolean {
  return this == null || this is PyNeverType // Any or Never
}

private fun PyType?.isOptional(): Boolean {
  if (this.isNoneType) return true

  if (this is PyUnionType) {
    return this.members.any { it.isNoneType }
  }

  return false
}

private fun Variance.inverse(): Variance {
  return when (this) {
    Variance.COVARIANT -> Variance.CONTRAVARIANT
    Variance.CONTRAVARIANT -> Variance.COVARIANT
    else -> {
      this
    }
  }
}

private fun Variance.multiply(other: Variance): Variance {
  if (this == Variance.INVARIANT || other == Variance.INVARIANT)
    return Variance.INVARIANT
  if (this == Variance.CONTRAVARIANT && other == Variance.CONTRAVARIANT)
    return Variance.COVARIANT
  if (this == Variance.CONTRAVARIANT || other == Variance.CONTRAVARIANT)
    return Variance.CONTRAVARIANT
  if (this == Variance.COVARIANT && other == Variance.COVARIANT)
    return Variance.COVARIANT
  UNREACHABLE()
}

/** The author thought, we never get here. */
@Suppress("FunctionName")
private fun UNREACHABLE(msg: String = "Unreachable"): Nothing = error(msg)
