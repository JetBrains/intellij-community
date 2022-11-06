// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.uast.analysis

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.IntRef
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.asSafely
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

internal class DependencyGraphBuilder private constructor(
  private val currentScope: LocalScopeContext = LocalScopeContext(null),
  private var currentDepth: Int,
  val dependents: MutableMap<UElement, MutableSet<Dependent>> = mutableMapOf(),
  val dependencies: MutableMap<UElement, MutableSet<Dependency>> = mutableMapOf(),
  private val implicitReceivers: MutableMap<UCallExpression, UThisExpression> = mutableMapOf(),
  val scopesStates: MutableMap<UElement, UScopeObjectsState> = mutableMapOf(),
  private val inlinedVariables: MutableMap<ULambdaExpression, Pair<String, String>> = mutableMapOf()
) : AbstractUastVisitor() {

  constructor() : this(currentDepth = 0)

  private val elementsProcessedAsReceiver: MutableSet<UExpression> = mutableSetOf()

  private fun createVisitor(scope: LocalScopeContext) =
    DependencyGraphBuilder(scope, currentDepth, dependents, dependencies, implicitReceivers, scopesStates, inlinedVariables)

  private inline fun <T> checkedDepthCall(node: UElement, body: () -> T): T {
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

    val parent = (node.uastParent as? UCallExpression)
    val isInlined = parent?.let { KotlinExtensionConstants.isExtensionFunctionToIgnore(it) } == true

    val child = currentScope.createChild(isInlined)
    for (parameter in node.parameters) {
      child.declareFakeVariable(parameter, parameter.name)
      child[parameter.name] = setOf(parameter)
    }

    parent
      ?.takeIf { KotlinExtensionConstants.isExtensionFunctionToIgnore(it) }
      ?.let { parent.valueArguments.getOrNull(0) as? ULambdaExpression }
      ?.let {
        it.parameters.getOrNull(0)?.name
      }?.let {
        (parent.receiver ?: parent.getImplicitReceiver())?.let receiverHandle@{ receiver ->
          val initElements = setOf(receiver)
          child[it] = initElements
          updatePotentialEqualReferences(it, initElements, child)

          val inlined = currentScope.declareInlined(receiver) ?: return@receiverHandle
          child.setPotentialEquality(inlined, it, DependencyEvidence())
          child[inlined] = initElements
          inlinedVariables[node] = it to inlined
        }
      }
    node.body.accept(createVisitor(child))
    scopesStates[node] = child.toUScopeObjectsState()

    if (isInlined) {
      currentScope.updateForInlined(child)
    }
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
          val componentType = (parameter.type as? PsiArrayType)?.componentType
          if (componentType == null) {
            logger<DependencyGraphBuilder>().error(
              "Incorrect `parameter.type` = ${parameter.type} for argument.kind == UastSpecialExpressionKind.VARARGS",
              Attachment("call.txt", kotlin.runCatching { node.sourcePsi?.text ?: "<null>" }.getOrElse { it.stackTraceToString() }),
              Attachment("parameter.txt", "${parameter} of type ${parameter.javaClass}"),
              Attachment(node.sourcePsi?.containingFile?.name ?: "file.txt",
                         kotlin.runCatching { node.sourcePsi?.containingFile?.text ?: "<null>" }.getOrElse { it.stackTraceToString() })
            )
          }
          registerParameterDependency(i, node, it, componentType ?: parameter.type)
        }
      }
      else {
        registerParameterDependency(i, node, argument, parameter.type)
      }
      // TODO: implicit this as receiver argument
      argument.takeIf { it == receiver }?.let { elementsProcessedAsReceiver.add(it) }
    }

    node.getImplicitReceiver()?.accept(this)

    return@checkedDepthCall super.visitCallExpression(node)
  }

  private fun registerParameterDependency(
    argumentIndex: Int,
    callExpression: UCallExpression,
    argument: UExpression,
    paramType: PsiType
  ) {
    registerDependency(
      Dependent.CallExpression(argumentIndex, callExpression, paramType),
      Dependency.ArgumentDependency(argument, callExpression)
    )
    argument.extractBranchesResultAsDependency().takeIf { dep -> dep is Dependency.BranchingDependency }?.let { dep ->
      registerDependency(Dependent.CommonDependent(argument), dep)
    }
  }

  override fun afterVisitCallExpression(node: UCallExpression) {
    node.getImplicitReceiver()?.let { implicitReceiver ->
      val inlinedCall = inlineCall(node, node.uastParent)
      if (inlinedCall.isNotEmpty()) {
        registerDependency(Dependent.CommonDependent(node), Dependency.BranchingDependency(inlinedCall).unwrapIfSingle())
      }
      else {
        registerDependency(Dependent.CommonDependent(node), Dependency.CommonDependency(implicitReceiver))
        if (node.uastParent !is UReferenceExpression) {
          currentScope.setLastPotentialUpdate(KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME, node)
        }
      }
    }
  }

  override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    node.receiver.accept(this)
    node.selector.accept(this)
    if (node.receiver !in elementsProcessedAsReceiver) {
      registerDependency(Dependent.CommonDependent(node.selector), node.receiver.extractBranchesResultAsDependency())
    }
    else {
      // this element unnecessary now, remove it to avoid memory leaks
      elementsProcessedAsReceiver.remove(node.receiver)
    }
    val inlinedExpressions = inlineCall(node.selector, node.uastParent)
    if (inlinedExpressions.isNotEmpty()) {
      registerDependency(Dependent.CommonDependent(node), Dependency.BranchingDependency(inlinedExpressions).unwrapIfSingle())
    }
    else {
      registerDependency(Dependent.CommonDependent(node), Dependency.CommonDependency(node.selector))
    }
    if (inlinedExpressions.isEmpty() && node.getOutermostQualified() == node) {
      node.getQualifiedChainWithImplicits().first().referenceOrThisIdentifier?.takeIf { it in currentScope }?.let {
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

    registerDependenciesForIdentifier(node.identifier, node)
    return@checkedDepthCall super.visitSimpleNameReferenceExpression(node)
  }

  override fun visitThisExpression(node: UThisExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    registerDependenciesForIdentifier(KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME, node)
    return@checkedDepthCall super.visitThisExpression(node)
  }

  private fun registerDependenciesForIdentifier(identifier: String, node: UExpression) {
    val referenceInfo = DependencyOfReference.ReferenceInfo(identifier, currentScope.getReferencedValues(identifier))

    currentScope[identifier]?.let {
      registerDependency(
        Dependent.CommonDependent(node),
        Dependency.BranchingDependency(
          it,
          referenceInfo
        ).unwrapIfSingle()
      )
    }

    val potentialDependenciesCandidates = currentScope.getLastPotentialUpdate(identifier)
    if (potentialDependenciesCandidates != null) {
      registerDependency(Dependent.CommonDependent(node),
                         Dependency.PotentialSideEffectDependency(potentialDependenciesCandidates, referenceInfo))
    }
  }

  override fun visitVariable(node: UVariable): Boolean = checkedDepthCall(node) {
    if (node !is ULocalVariable && !(node is UParameter && node.uastParent is UMethod)) {
      return@checkedDepthCall super.visitVariable(node)
    }

    ProgressManager.checkCanceled()
    node.uastInitializer?.accept(this)
    val name = node.name ?: return@checkedDepthCall super.visitVariable(node)
    currentScope.declare(node)
    val initializer = node.uastInitializer ?: return@checkedDepthCall super.visitVariable(node)
    val initElements = initializer.extractBranchesResultAsDependency().inlineElementsIfPossible().elements
    currentScope[name] = initElements
    updatePotentialEqualReferences(name, initElements)
    currentScope.setLastPotentialUpdateAsAssignment(name, initElements)
    return@checkedDepthCall true
  }

  override fun visitBinaryExpression(node: UBinaryExpression): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    if (node.operator == UastBinaryOperator.ASSIGN &&
        (node.leftOperand is UReferenceExpression || node.leftOperand is UArrayAccessExpression)
    ) {
      node.rightOperand.accept(this)
      val extractedBranchesResult = node.rightOperand.extractBranchesResultAsDependency().inlineElementsIfPossible()
      (node.leftOperand as? USimpleNameReferenceExpression)
        ?.takeIf { it.identifier in currentScope }
        ?.let {
          currentScope[it.identifier] = extractedBranchesResult.elements
          updatePotentialEqualReferences(it.identifier, extractedBranchesResult.elements)
          currentScope.setLastPotentialUpdateAsAssignment(it.identifier, extractedBranchesResult.elements)
        }
      registerDependency(Dependent.Assigment(node.leftOperand), extractedBranchesResult)
      return@checkedDepthCall true
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

  private fun Dependency.inlineElementsIfPossible(): Dependency {
    val newElements = elements.flatMap { element ->
      when (element) {
        is UQualifiedReferenceExpression -> inlineCall(element.selector, element.uastParent)
        is UCallExpression -> inlineCall(element, element.uastParent)
        else -> listOf()
      }.takeUnless { it.isEmpty() } ?: listOf(element)
    }.toSet()

    return Dependency.BranchingDependency(newElements).unwrapIfSingle()
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
                            ?.asSafely<ULocalVariable>()
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
      if (expression.getExpressionType() != null) { // it is real expression, not statement
        registerEmptyDependency(expression)
      }
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

  override fun afterVisitMethod(node: UMethod) {
    scopesStates[node] = currentScope.toUScopeObjectsState()
  }

  private fun inlineCall(selector: UExpression, parent: UElement?): Set<UExpression> {
    val call = selector as? UCallExpression ?: return emptySet()
    if (KotlinExtensionConstants.isLetOrRunCall(call)) {
      val lambda = call.getArgumentForParameter(1) ?: return emptySet()
      return mutableSetOf<UExpression>().apply {
        lambda.accept(object : AbstractUastVisitor() {
          override fun visitReturnExpression(node: UReturnExpression): Boolean {
            if (node.jumpTarget != lambda) return super.visitReturnExpression(node)
            node.returnExpression?.let {
              val inlinedCall = inlineCall(it, node)
              if (inlinedCall.isNotEmpty()) {
                addAll(inlinedCall)
              }
              else {
                add(it)
              }
            }
            return super.visitReturnExpression(node)
          }
        })
      }
    }
    if (KotlinExtensionConstants.isAlsoOrApplyCall(call)) {
      val lambda = call.getArgumentForParameter(1) as? ULambdaExpression ?: return emptySet()
      val paramName = lambda.parameters.singleOrNull()?.name ?: return emptySet()
      val inlinedVarName = inlinedVariables[lambda]?.takeIf { it.first == paramName }?.second ?: return emptySet()
      val scopesState = scopesStates[lambda] ?: return emptySet()
      val candidates = scopesState.lastVariablesUpdates[paramName] ?: return emptySet()
      val values = scopesState.variableToValueMarks[paramName] ?: return emptySet()
      val fakeReferenceExpression = UFakeSimpleNameReferenceExpression(parent, inlinedVarName, inlinedVarName)
      currentScope[inlinedVarName]?.let {
        registerDependency(
          Dependent.CommonDependent(fakeReferenceExpression),
          Dependency.BranchingDependency(it).unwrapIfSingle()
        )
      }
      registerDependency(
        Dependent.CommonDependent(fakeReferenceExpression),
        Dependency.PotentialSideEffectDependency(candidates, DependencyOfReference.ReferenceInfo(paramName, values))
      )
      return setOf(fakeReferenceExpression)
    }
    return emptySet()
  }

  private fun updatePotentialEqualReferences(name: String, initElements: Set<UElement>, scope: LocalScopeContext = currentScope) {
    scope.clearPotentialReferences(TEMP_VAR_NAME)

    fun getInlined(element: UElement, id: String): String? =
      element.getParentOfType<ULambdaExpression>()
        ?.let { inlinedVariables[it] }
        ?.takeIf { it.first == id }
        ?.second

    fun identToReferenceInfo(element: UElement, identifier: String): Pair<String, UReferenceExpression?>? {
      return (identifier.takeIf { id -> id in scope } ?: getInlined(element, identifier))
        ?.let { id -> id to null } // simple reference => same references
    }

    val potentialEqualReferences = initElements
      .mapNotNull {
        when (it) {
          is UQualifiedReferenceExpression -> it.getQualifiedChainWithImplicits().firstOrNull()?.referenceOrThisIdentifier?.let { identifier ->
            (identifier.takeIf { id -> id in scope } ?: getInlined(it, identifier))?.let { id -> id to it }
          }
          is USimpleNameReferenceExpression -> identToReferenceInfo(it, it.identifier)
          is UThisExpression -> identToReferenceInfo(it, KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME)
          is UCallExpression -> {
            it.getImplicitReceiver()
              ?.takeIf { KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME in scope }
              ?.let { implicitThis ->
                KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME to UFakeQualifiedReferenceExpression(implicitThis, it, it.uastParent)
              }
          }
          else -> null
        }
      }
    for ((potentialEqualReference, evidence) in potentialEqualReferences) {
      scope.setPotentialEquality(TEMP_VAR_NAME, potentialEqualReference, DependencyEvidence(evidence))
    }
    scope.clearPotentialReferences(name)
    scope.setPotentialEquality(name, TEMP_VAR_NAME, DependencyEvidence())
    scope.clearPotentialReferences(TEMP_VAR_NAME)
  }

  private fun registerEmptyDependency(element: UElement) {
    dependents.putIfAbsent(element, mutableSetOf())
  }

  private fun registerDependency(dependent: Dependent, dependency: Dependency) {
    if (dependency !is Dependency.PotentialSideEffectDependency) {
      for (el in dependency.elements) {
        dependents.getOrPut(el) { mutableSetOf() }.add(dependent)
      }
    }
    dependencies.getOrPut(dependent.element) { mutableSetOf() }.add(dependency)
  }

  private fun UCallExpression.getImplicitReceiver(): UExpression? {
    return if (
      hasImplicitReceiver(this) &&
      (KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME in currentScope || this in implicitReceivers)
    ) {
      implicitReceivers.getOrPut(this) { UFakeThisExpression(uastParent) }
    }
    else {
      null
    }
  }

  private fun UQualifiedReferenceExpression.getQualifiedChainWithImplicits(): List<UExpression> {
    val chain = getQualifiedChain()
    val firstElement = chain.firstOrNull()
    return if (firstElement is UCallExpression) {
      listOfNotNull(firstElement.getImplicitReceiver()) + chain
    }
    else {
      chain
    }
  }

  companion object {
    val maxBuildDepth = Registry.intValue("uast.usage.graph.default.recursion.depth.limit", 30)

    object BuildOverflowException : RuntimeException("graph building is overflowed", null, false, false)
  }
}

private typealias SideEffectChangeCandidate = Dependency.PotentialSideEffectDependency.SideEffectChangeCandidate
private typealias DependencyEvidence = Dependency.PotentialSideEffectDependency.DependencyEvidence
private typealias CandidatesTree = Dependency.PotentialSideEffectDependency.CandidatesTree

private const val INLINED_PREFIX = "inlined/"

private class LocalScopeContext(
  private val parent: LocalScopeContext?,
  private val inlinedId: IntRef = IntRef(),
  private val isInlined: Boolean = false
) {
  private val definedInScopeVariables = mutableSetOf<UElement>()
  private val definedInScopeVariablesNames = mutableSetOf<String>()

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
      referencesModel.assignValueIfNotAssigned(it)
      lastDeclarationOf[it] = variable
    }
  }

  fun declareFakeVariable(element: UElement, name: String) {
    definedInScopeVariablesNames.add(name)
    referencesModel.assignValueIfNotAssigned(name)
    lastDeclarationOf[name] = element
  }

  fun declareInlined(element: UElement): String? {
    if (!isInlined) {
      val name = "$INLINED_PREFIX${inlinedId.get()}"
      inlinedId.inc()
      declareFakeVariable(element, name)
      return name
    }
    return parent?.declareInlined(element)
  }

  fun getDeclaration(variable: String): UElement? = lastDeclarationOf[variable] ?: parent?.getDeclaration(variable)

  operator fun contains(variable: String): Boolean = variable in definedInScopeVariablesNames || parent?.let { variable in it } == true

  fun createChild(isInlined: Boolean = false) = LocalScopeContext(this, inlinedId, isInlined)

  fun setLastPotentialUpdate(variable: String, updateElement: UElement) {
    lastPotentialUpdatesOf[variable] = CandidatesTree.fromCandidate(
      SideEffectChangeCandidate(
        updateElement,
        DependencyEvidence(),
        dependencyWitnessValues = referencesModel.getAllTargetsForReference(variable)
      )
    )
    for ((reference, evidenceAndWitness) in referencesModel.getAllPossiblyEqualReferences(variable)) {
      val (evidence, witness) = evidenceAndWitness
      val newCandidate = SideEffectChangeCandidate(updateElement, evidence, witness)
      val candidatesForReference = lastPotentialUpdatesOf[reference]
      lastPotentialUpdatesOf[reference] = candidatesForReference?.addToBegin(newCandidate) ?: CandidatesTree.fromCandidate(newCandidate)
    }
  }

  fun setLastPotentialUpdateAsAssignment(variable: String, updateElements: Collection<UElement>) {
    if (updateElements.size == 1) {
      lastPotentialUpdatesOf[variable] = CandidatesTree.fromCandidate(
        SideEffectChangeCandidate(
          updateElements.first(),
          DependencyEvidence(),
          dependencyWitnessValues = referencesModel.getAllTargetsForReference(variable))
      )
    }
    else {
      lastPotentialUpdatesOf[variable] = CandidatesTree.fromCandidates(
        updateElements.mapTo(mutableSetOf()) {
          SideEffectChangeCandidate(it, DependencyEvidence(), dependencyWitnessValues = referencesModel.getAllTargetsForReference(variable))
        }
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
      this[variable] = mutableSetOf<UElement>().apply {
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
    mergeWith(other)
  }

  fun updateForInlined(other: LocalScopeContext) {
    update(other)
    referencesModel.updateForReferences(other.referencesModel, variablesNames.filter { it.startsWith(INLINED_PREFIX) })
  }

  fun getReferencedValues(identifier: String): Collection<UValueMark> {
    return referencesModel.getAllTargetsForReference(identifier)
  }

  fun toUScopeObjectsState(): UScopeObjectsState {
    val variableValueMarks = definedInScopeVariablesNames.associateWith { referencesModel.getAllTargetsForReference(it) }
    val lastUpdates = definedInScopeVariablesNames.mapNotNull { getLastPotentialUpdate(it)?.let { tree -> it to tree } }.toMap()
    return UScopeObjectsState(lastUpdates, variableValueMarks)
  }

  private class ReferencesModel(private val parent: ReferencesModel?) {
    private val referencesTargets = mutableMapOf<String, MutableMap<UValueMark, DependencyEvidence>>()
    private val targetsReferences = mutableMapOf<UValueMark, MutableMap<String, DependencyEvidence>>()

    private fun getAllReferences(referencedValue: UValueMark): Map<String, DependencyEvidence> =
      parent?.getAllReferences(referencedValue).orEmpty() + targetsReferences[referencedValue].orEmpty()

    private fun getAllTargets(reference: String): Map<UValueMark, DependencyEvidence> =
      listOfNotNull(referencesTargets[reference], parent?.getAllTargets(reference)).fold(emptyMap()) { result, current ->
        (result.keys + current.keys).associateWith { (result[it] ?: current[it])!! }
      }

    fun assignValueIfNotAssigned(reference: String) {
      if (getAllTargets(reference).isNotEmpty()) return

      val evidence = DependencyEvidence()
      val newTarget = UValueMark()
      referencesTargets[reference] = mutableMapOf(newTarget to evidence)
      targetsReferences[newTarget] = mutableMapOf(reference to evidence)
    }

    fun setPossibleEquality(assigneeReference: String, targetReference: String, evidence: DependencyEvidence) {
      val targets = getAllTargets(targetReference).toMutableMap()
      if (targets.isEmpty()) {
        val newTarget = UValueMark()
        val targetEvidence = DependencyEvidence()
        referencesTargets[targetReference] = mutableMapOf(newTarget to targetEvidence) // equal by default
        referencesTargets.getOrPut(assigneeReference) { mutableMapOf() }[newTarget] = evidence

        targetsReferences[newTarget] = mutableMapOf(
          assigneeReference to evidence,
          targetReference to targetEvidence
        )
        return
      }
      referencesTargets.getOrPut(assigneeReference) { mutableMapOf() }
        .putAll(targets.mapValues { (_, evidenceForTarget) -> evidence.copy(requires = listOf(evidenceForTarget)) })

      for (target in targets.keys) {
        targetsReferences.getOrPut(target) { mutableMapOf() }[assigneeReference] = evidence
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

    fun getAllPossiblyEqualReferences(reference: String): Map<String, Pair<DependencyEvidence, Collection<UValueMark>>> {
      val allTargets = getAllTargets(reference)
      return allTargets
        .map { (target, evidence) ->
          getAllReferences(target).map { (currentReference, referenceEvidence) ->
            currentReference to (combineEvidences(evidence, referenceEvidence) to allTargets.keys)
          }
        }
        .flatten()
        .filter { it.first != reference }
        .toMap()
    }

    fun getAllTargetsForReference(reference: String): Collection<UValueMark> {
      return getAllTargets(reference).keys
    }

    private val references: Sequence<String>
      get() = generateSequence(this) { it.parent }
        .flatMap { it.referencesTargets.keys }
        .distinct()

    // For now works only for inlined variables
    fun updateForReferences(other: ReferencesModel, references: List<String>) {
      check(other.parent == this)

      val newTargets = mutableSetOf<UValueMark>()
      for (reference in references) {
        other.referencesTargets[reference]?.let {
          clearReference(reference)
          referencesTargets[reference] = it
          newTargets += it.keys
        }
      }

      val ownReferences = this.references.toSet()
      for (target in newTargets) {
        other.targetsReferences[target]?.filterKeys { it in ownReferences }?.takeUnless { it.isEmpty() }?.let {
          targetsReferences[target] = it.toMutableMap()
        }
      }
    }
  }
}

private fun combineEvidences(ownEvidence: DependencyEvidence, otherEvidence: DependencyEvidence): DependencyEvidence =
  ownEvidence.copy(requires = ownEvidence.requires + otherEvidence)

private const val UAST_KT_ELVIS_NAME = "elvis"

private const val TEMP_VAR_NAME = "@$,()"

private fun hasImplicitReceiver(callExpression: UCallExpression): Boolean =
  callExpression.receiver == null && callExpression.receiverType != null

private val UExpression?.referenceOrThisIdentifier: String?
  get() = when (this) {
    is USimpleNameReferenceExpression -> identifier
    is UThisExpression -> KotlinExtensionConstants.LAMBDA_THIS_PARAMETER_NAME
    else -> null
  }

private class UFakeThisExpression(override val uastParent: UElement?) : UThisExpression, UFakeExpression {
  override val label: String?
    get() = null

  override val labelIdentifier: UIdentifier?
    get() = null
}

private class UFakeQualifiedReferenceExpression(
  override val receiver: UExpression,
  override val selector: UExpression,
  override val uastParent: UElement?
) : UQualifiedReferenceExpression, UFakeExpression {
  override val accessType: UastQualifiedExpressionAccessType
    get() = UastQualifiedExpressionAccessType.SIMPLE

  override val resolvedName: String?
    get() = null
}

private interface UFakeExpression : UExpression, UResolvable {
  @Suppress("OverridingDeprecatedMember")
  override val psi: PsiElement?
    get() = null

  override val uAnnotations: List<UAnnotation>
    get() = emptyList()

  override fun resolve(): PsiElement? = null
}

private class UFakeSimpleNameReferenceExpression(
  override val uastParent: UElement?,
  override val resolvedName: String?,
  override val identifier: String
) : USimpleNameReferenceExpression, UFakeExpression