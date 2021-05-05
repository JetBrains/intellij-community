// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiArrayType
import com.intellij.util.castSafelyTo
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor
import kotlin.collections.HashSet

internal class DependencyGraphBuilder private constructor(
  private val currentScope: LocalScopeContext = LocalScopeContext(null),
  private var currentDepth: Int,
  val dependents: MutableMap<UElement, MutableSet<Dependent>> = mutableMapOf(),
  val dependencies: MutableMap<UElement, MutableSet<Dependency>> = mutableMapOf(),
) : AbstractUastVisitor() {

  constructor(): this(currentDepth = 0)

  private val elementsProcessedAsReceiver: MutableSet<UExpression> = HashSet()

  private fun createVisitor(scope: LocalScopeContext) =
    DependencyGraphBuilder(scope, currentDepth, dependents, dependencies)

  inline fun checkedDepthCall(node: UElement, body: () -> Boolean): Boolean {
    currentDepth++
    try {
      if (currentDepth > maxBuildDepth) {
        thisLogger().info("build overflow in $node because depth is greater than $maxBuildDepth")
        throw BuildOverflowException
      }

      return body()
    }
    finally {
      currentDepth--
    }
  }

  override fun visitLambdaExpression(node: ULambdaExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    val child = currentScope.createChild()
    val parent = (node.uastParent as? UCallExpression)
    parent
      ?.takeIf { KotlinExtensionConstants.isExtensionFunctionToIgnore(it) }
      ?.let { parent.valueArguments.getOrNull(0) as? ULambdaExpression }
      ?.let {
        it.valueParameters.getOrNull(0)?.name ?: KotlinExtensionConstants.DEFAULT_LAMBDA_ARGUMENT_NAME
      }?.let {
        parent.receiver?.let { receiver ->
          child.declareFakeVariable(parent, it)
          child[it] = setOf(receiver)
        }
      }
    node.body.accept(createVisitor(child))
    return@checkedDepthCall true
  }

  override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType): Boolean = checkedDepthCall(node) {
    if (node.operationKind != UastBinaryExpressionWithTypeKind.TypeCast.INSTANCE) {
      return@checkedDepthCall super.visitBinaryExpressionWithType(node)
    }
    registerDependency(Dependent.CommonDependent(node), node.operand.extractBranchesResultAsDependency())
    return@checkedDepthCall super.visitBinaryExpressionWithType(node)
  }

  override fun visitCallExpression(node: UCallExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    val resolvedMethod = node.resolve() ?: return@checkedDepthCall super.visitCallExpression(node)
    val receiver = (node.uastParent as? UQualifiedReferenceExpression)?.receiver

    for ((i, parameter) in resolvedMethod.parameterList.parameters.withIndex()) {
      val argument = node.getArgumentForParameter(i) ?: continue
      if (argument is UExpressionList && argument.kind == UastSpecialExpressionKind.VARARGS) {
        argument.expressions.forEach {
          registerDependency(
            Dependent.CallExpression(i, node, (parameter.type as PsiArrayType).componentType),
            Dependency.ArgumentDependency(it, node)
          )
        }
      }
      else {
        registerDependency(Dependent.CallExpression(i, node, parameter.type), Dependency.ArgumentDependency(argument, node))
      }

      argument.takeIf { it == receiver }?.let { elementsProcessedAsReceiver.add(it) }
    }

    return@checkedDepthCall super.visitCallExpression(node)
  }

  override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    node.selector.accept(this)
    if (node.receiver !in elementsProcessedAsReceiver) {
      registerDependency(Dependent.CommonDependent(node.selector), Dependency.CommonDependency(node.receiver))
    }
    else {
      // this element unnecessary now, remove it to avoid memory leaks
      elementsProcessedAsReceiver.remove(node.receiver)
    }
    registerDependency(Dependent.CommonDependent(node), Dependency.CommonDependency(node.selector))
    node.receiver.accept(this)
    if (node.getOutermostQualified() == node) {
      (node.getQualifiedChain().first() as? USimpleNameReferenceExpression)?.identifier?.takeIf { it in currentScope }?.let {
        currentScope.setLastPotentialUpdate(it, node)
      }
    }
    return@checkedDepthCall true
  }

  override fun visitParenthesizedExpression(node: UParenthesizedExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    registerDependency(Dependent.CommonDependent(node), node.expression.extractBranchesResultAsDependency())
    return@checkedDepthCall super.visitParenthesizedExpression(node)
  }

  override fun visitReturnExpression(node: UReturnExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    node.returnExpression?.extractBranchesResultAsDependency()?.let { registerDependency(Dependent.CommonDependent(node), it) }
    return@checkedDepthCall super.visitReturnExpression(node)
  }

  override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    if (node.uastParent is UReferenceExpression && (node.uastParent as? UQualifiedReferenceExpression)?.receiver != node)
      return@checkedDepthCall true

    currentScope[node.identifier]?.let {
      registerDependency(Dependent.CommonDependent(node), Dependency.BranchingDependency(it).unwrapIfSingle())
    }

    val potentialDependenciesCandidates = currentScope.getLastPotentialUpdate(node.identifier)
    if (potentialDependenciesCandidates != null) {
      registerDependency(Dependent.CommonDependent(node), Dependency.PotentialSideEffectDependency(potentialDependenciesCandidates))
    }
    return@checkedDepthCall super.visitSimpleNameReferenceExpression(node)
  }

  override fun visitLocalVariable(node: ULocalVariable): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    val name = node.name
    currentScope.declare(node)
    val initializer = node.uastInitializer ?: return@checkedDepthCall super.visitLocalVariable(node)
    val initElements = initializer.extractBranchesResultAsDependency().elements
    currentScope[name] = initElements
    updatePotentialEqualReferences(name, initElements)
    return@checkedDepthCall super.visitLocalVariable(node)
  }

  override fun visitBinaryExpression(node: UBinaryExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    if (node.operator == UastBinaryOperator.ASSIGN &&
        (node.leftOperand is UReferenceExpression || node.leftOperand is UArrayAccessExpression)
    ) {
      node.rightOperand.accept(this)
      val extractedBranchesResult = node.rightOperand.extractBranchesResultAsDependency()
      (node.leftOperand as? USimpleNameReferenceExpression)
        ?.takeIf { it.identifier in currentScope }
        ?.let {
          currentScope[it.identifier] = extractedBranchesResult.elements
          updatePotentialEqualReferences(it.identifier, extractedBranchesResult.elements)
          currentScope.setLastPotentialUpdateAsAssignment(it.identifier, extractedBranchesResult.elements)
        }
      registerDependency(Dependent.Assigment(node.leftOperand), extractedBranchesResult)
      return true
    }
    registerDependency(
      Dependent.BinaryOperatorDependent(node, isDependentOfLeftOperand = true),
      Dependency.CommonDependency(node.leftOperand)
    )
    registerDependency(
      Dependent.BinaryOperatorDependent(node, isDependentOfLeftOperand = false),
      Dependency.CommonDependency(node.rightOperand)
    )
    return@checkedDepthCall super.visitBinaryExpression(node)
  }

  override fun visitIfExpression(node: UIfExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    node.condition.accept(this)
    val left = currentScope.createChild()
    val right = currentScope.createChild()
    node.thenExpression?.accept(createVisitor(left))
    node.elseExpression?.accept(createVisitor(right))

    currentScope.mergeWith(right, left)

    return@checkedDepthCall true
  }

  override fun visitExpressionList(node: UExpressionList) = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    if (node.kind.name != UAST_KT_ELVIS_NAME) {
      return super.visitExpressionList(node)
    }

    val firstExpression = (node.expressions.first() as? UDeclarationsExpression)
                            ?.declarations
                            ?.first()
                            ?.castSafelyTo<ULocalVariable>()
                            ?.uastInitializer
                            ?.extractBranchesResultAsDependency() ?: return@checkedDepthCall super.visitExpressionList(node)
    val ifExpression = node.expressions.getOrNull(1)
                         ?.extractBranchesResultAsDependency() ?: return@checkedDepthCall super.visitExpressionList(node)

    registerDependency(Dependent.CommonDependent(node), firstExpression.and(ifExpression))

    return@checkedDepthCall super.visitExpressionList(node)
  }

  override fun visitSwitchExpression(node: USwitchExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    node.expression?.accept(this)

    val childrenScopes = mutableListOf<LocalScopeContext>()
    for (clause in node.body.expressions.filterIsInstance<USwitchClauseExpressionWithBody>()) {
      val childScope = currentScope.createChild()
      clause.accept(createVisitor(childScope))
      childrenScopes.add(childScope)
    }

    currentScope.mergeWith(childrenScopes)

    return@checkedDepthCall true
  }

  override fun visitForEachExpression(node: UForEachExpression): Boolean {
    ProgressManager.checkCanceled()
    node.iteratedValue.accept(this)

    return visitLoopExpression(node)
  }

  override fun visitForExpression(node: UForExpression): Boolean {
    ProgressManager.checkCanceled()
    node.declaration?.accept(this)
    node.condition?.accept(this)
    node.update?.accept(this)

    return visitLoopExpression(node)
  }

  override fun visitDoWhileExpression(node: UDoWhileExpression): Boolean {
    ProgressManager.checkCanceled()
    node.condition.accept(this)

    return visitLoopExpression(node)
  }

  override fun visitWhileExpression(node: UWhileExpression): Boolean {
    ProgressManager.checkCanceled()
    node.condition.accept(this)

    return visitLoopExpression(node)
  }

  private fun visitLoopExpression(node: ULoopExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    val child = currentScope.createChild()

    node.body.accept(createVisitor(child))

    currentScope.mergeWith(currentScope, child)

    return@checkedDepthCall true
  }

  override fun visitBlockExpression(node: UBlockExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()

    when (node.uastParent) {
      is ULoopExpression, is UIfExpression, is USwitchExpression, is ULambdaExpression ->
        return@checkedDepthCall super.visitBlockExpression(node)
    }

    val child = currentScope.createChild()
    val visitor = createVisitor(child)

    for (expression in node.expressions) {
      expression.accept(visitor)
    }

    currentScope.update(child)

    return@checkedDepthCall true
  }

  override fun visitTryExpression(node: UTryExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    val tryChild = currentScope.createChild()
    node.tryClause.accept(createVisitor(tryChild))
    val scopeChildren = mutableListOf(tryChild)
    node.catchClauses.mapTo(scopeChildren) { clause ->
      currentScope.createChild().also { clause.accept(createVisitor(it)) }
    }
    currentScope.mergeWith(scopeChildren)
    node.finallyClause?.accept(this)

    return@checkedDepthCall true
  }

  // Ignore class nodes
  override fun visitClass(node: UClass): Boolean = true

  // Ignore field nodes
  override fun visitField(node: UField): Boolean = true

  private fun updatePotentialEqualReferences(name: String, initElements: Set<UElement>) {
    currentScope.clearPotentialReferences(name)
    val potentialEqualReferences = initElements
      .mapNotNull {
        when (it) {
          is UQualifiedReferenceExpression -> (it.receiver as? USimpleNameReferenceExpression)?.identifier?.takeIf { id -> id in currentScope }?.let { id -> id to it }
          is USimpleNameReferenceExpression -> it.identifier.takeIf { id -> id in currentScope }?.let { id -> id to null } // simple reference => same references
          else -> null
        }
      }
    for ((potentialEqualReference, evidence) in potentialEqualReferences) {
      currentScope.setPotentialEquality(name, potentialEqualReference,
                                        DependencyEvidence(potentialEqualReferences.size == 1, evidence, potentialEqualReference))
    }
  }

  private fun registerDependency(dependent: Dependent, dependency: Dependency) {
    if (dependency !is Dependency.PotentialSideEffectDependency) {
      for (el in dependency.elements) {
        dependents.getOrPut(el) { HashSet() }.add(dependent)
      }
    }
    dependencies.getOrPut(dependent.element) { HashSet() }.add(dependency)
  }

  companion object {
    val maxBuildDepth = Registry.intValue("uast.usage.graph.default.recursion.depth.limit", 30)

    object BuildOverflowException : RuntimeException("graph building is overflowed", null, false, false)
  }
}

private typealias SideEffectChangeCandidate = Dependency.PotentialSideEffectDependency.SideEffectChangeCandidate
private typealias DependencyEvidence = Dependency.PotentialSideEffectDependency.DependencyEvidence
private typealias CandidatesTree = Dependency.PotentialSideEffectDependency.CandidatesTree

private class LocalScopeContext(private val parent: LocalScopeContext?) {
  private val definedInScopeVariables = HashSet<UElement>()
  private val definedInScopeVariablesNames = HashSet<String>()

  private val lastAssignmentOf = mutableMapOf<UElement, Set<UElement>>()
  private val lastDeclarationOf = mutableMapOf<String?, UElement>()

  private val lastPotentialUpdatesOf = mutableMapOf<String, CandidatesTree>()

  private val referencesModel: ReferencesModel = ReferencesModel(parent?.referencesModel)

  operator fun set(variable: String, values: Set<UElement>) {
    getDeclaration(variable)?.let { lastAssignmentOf[it] = values }
  }

  operator fun set(variable: UElement, values: Set<UElement>) {
    lastAssignmentOf[variable] = values
  }

  operator fun get(variable: String): Set<UElement>? = getDeclaration(variable)?.let { lastAssignmentOf[it] ?: parent?.get(variable) }

  operator fun get(variable: UElement): Set<UElement>? = lastAssignmentOf[variable] ?: parent?.get(variable)

  fun declare(variable: UVariable) {
    definedInScopeVariables.add(variable)
    variable.name?.let {
      definedInScopeVariablesNames.add(it)
      lastDeclarationOf[it] = variable
    }
  }

  fun declareFakeVariable(element: UElement, name: String) {
    definedInScopeVariablesNames.add(name)
    lastDeclarationOf[name] = element
  }

  fun getDeclaration(variable: String): UElement? = lastDeclarationOf[variable] ?: parent?.getDeclaration(variable)

  operator fun contains(variable: String): Boolean = variable in definedInScopeVariablesNames || parent?.let { variable in it } == true

  fun createChild() = LocalScopeContext(this)

  fun setLastPotentialUpdate(variable: String, updateElement: UElement) {
    lastPotentialUpdatesOf[variable] = CandidatesTree.fromCandidate(SideEffectChangeCandidate(updateElement, DependencyEvidence(true)))
    for ((reference, evidence) in getAllPotentialEqualReferences(variable)) {
      val newCandidate = SideEffectChangeCandidate(updateElement, evidence)
      val candidatesForReference = lastPotentialUpdatesOf[reference]
      lastPotentialUpdatesOf[reference] = candidatesForReference?.addToBegin(newCandidate) ?: CandidatesTree.fromCandidate(newCandidate)
    }
  }

  fun setLastPotentialUpdateAsAssignment(variable: String, updateElements: Collection<UElement>) {
    if (updateElements.size == 1) {
      lastPotentialUpdatesOf[variable] = CandidatesTree.fromCandidate(
        SideEffectChangeCandidate(updateElements.first(), DependencyEvidence(true))
      )
    }
    else {
      lastPotentialUpdatesOf[variable] = CandidatesTree.fromCandidates(
        updateElements.mapTo(mutableSetOf()) { SideEffectChangeCandidate(it, DependencyEvidence(true)) }
      )
    }
  }

  fun getLastPotentialUpdate(variable: String): CandidatesTree? =
    lastPotentialUpdatesOf[variable] ?: parent?.getLastPotentialUpdate(variable)

  fun setPotentialEquality(assigneeReference: String, targetReference: String, evidence: DependencyEvidence) {
    referencesModel.setPossibleEquality(assigneeReference, targetReference, evidence)
  }

  fun clearPotentialReferences(reference: String) {
    referencesModel.clearReference(reference)
  }

  fun getAllPotentialEqualReferences(reference: String): Map<String, DependencyEvidence> {
    return referencesModel.getAllPossiblyEqualReferences(reference)
  }

  val variables: Iterable<UElement>
    get() {
      return generateSequence(this) { it.parent }
        .flatMap { it.definedInScopeVariables.asSequence() }
        .asIterable()
    }

  val variablesNames: Iterable<String>
    get() = generateSequence(this) { it.parent }
      .flatMap { it.definedInScopeVariablesNames }
      .asIterable()

  fun mergeWith(others: Iterable<LocalScopeContext>) {
    for (variable in variables) {
      this[variable] = HashSet<UElement>().apply {
        for (other in others) {
          other[variable]?.let { addAll(it) }
        }
      }
    }
    for (variableName in variablesNames) {
      mutableSetOf<CandidatesTree>().apply {
        for (other in others) {
          other.getLastPotentialUpdate(variableName)?.let {
            add(it)
          }
        }
      }.takeUnless { it.isEmpty() }?.let { candidates ->
          lastPotentialUpdatesOf[variableName] = CandidatesTree.merge(candidates)
      }
    }
  }

  fun mergeWith(vararg others: LocalScopeContext) {
    mergeWith(others.asIterable())
  }

  fun update(other: LocalScopeContext) {
    for (variable in variables) {
      other[variable]?.let { this[variable] = it }
    }
  }

  private class ReferencesModel(private val parent: ReferencesModel?) {
    private class Target

    private val referencesTargets = mutableMapOf<String, MutableMap<Target, DependencyEvidence>>()
    private val targetsReferences = mutableMapOf<Target, MutableSet<String>>()

    private fun getAllReferences(target: Target): Set<String> =
      parent?.getAllReferences(target).orEmpty() + targetsReferences[target].orEmpty()

    private fun getAllTargets(reference: String): Map<Target, DependencyEvidence> =
      listOfNotNull(referencesTargets[reference], parent?.getAllTargets(reference)).fold(emptyMap()) { result, current ->
        (result.keys + current.keys).associateWith { (result[it] ?: current[it])!! }
      }

    fun setPossibleEquality(assigneeReference: String, targetReference: String, evidence: DependencyEvidence) {
      val targets = getAllTargets(targetReference).toMutableMap()
      if (targets.isEmpty()) {
        val newTarget = Target()
        referencesTargets[targetReference] = mutableMapOf(newTarget to DependencyEvidence(true)) // equal by default
        referencesTargets.getOrPut(assigneeReference) { mutableMapOf() }[newTarget] = evidence

        targetsReferences[newTarget] = mutableSetOf(assigneeReference, targetReference)
        return
      }
      referencesTargets.getOrPut(assigneeReference) { mutableMapOf() }
        .putAll(targets.mapValues { (_, evidenceForTarget) -> evidence.copy(requires = evidenceForTarget) })

      for (target in targets.keys) {
        targetsReferences.getOrPut(target) { mutableSetOf() }.add(assigneeReference)
      }
    }

    fun clearReference(reference: String) {
      val targets = referencesTargets[reference] ?: return
      referencesTargets.remove(reference)

      for (target in targets.keys) {
        targetsReferences[target]?.let { references ->
          references.remove(reference)
          if (references.isEmpty()) {
            targetsReferences.remove(target)
          }
        }
      }
    }

    fun getAllPossiblyEqualReferences(reference: String): Map<String, DependencyEvidence> =
      getAllTargets(reference)
        .map { (target, evidence) -> getAllReferences(target).map { it to evidence } }
        .flatten()
        .filter { it.first != reference }
        .toMap()

  }
}

private const val UAST_KT_ELVIS_NAME = "elvis"