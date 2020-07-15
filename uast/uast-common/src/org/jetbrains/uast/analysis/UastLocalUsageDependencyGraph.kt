// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.analysis

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiType
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.castSafelyTo
import gnu.trove.THashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

private val LOG = Logger.getInstance(UastLocalUsageDependencyGraph::class.java)

/**
 * Dependency graph of UElements in some scope.
 * Dependencies of element are elements needed to compute value of this element.
 * Handles variable assignments and branching
 */
@ApiStatus.Experimental
class UastLocalUsageDependencyGraph private constructor(element: UElement) {
  val dependents: Map<UElement, Set<Dependent>>
  val dependencies: Map<UElement, Set<Dependency>>

  init {
    val visitor = VisitorWithVariablesTracking(currentDepth = 0)
    try {
      element.accept(visitor)
    }
    finally {
      LOG.debug {
        "graph size: dependants = ${visitor.dependents.asSequence().map { (_, arr) -> arr.size }.sum()}," +
        " dependencies = ${visitor.dependencies.asSequence().map { (_, arr) -> arr.size }.sum()}"
      }
    }
    dependents = visitor.dependents
    dependencies = visitor.dependencies
  }

  companion object {
    private val DEPENDENCY_GRAPH_KEY = Key.create<CachedValue<UastLocalUsageDependencyGraph>>(
      "reactor.local.dependency.graph"
    )

    /**
     * Creates or takes from cache of [element] dependency graph
     */
    @JvmStatic
    fun getGraphByUElement(element: UElement): UastLocalUsageDependencyGraph? {
      val sourcePsi = element.sourcePsi ?: return null
      return CachedValuesManager.getCachedValue(sourcePsi, DEPENDENCY_GRAPH_KEY) {
        val graph = try {
          UastLocalUsageDependencyGraph(sourcePsi.toUElement()!!)
        }
        catch (e: VisitorWithVariablesTracking.Companion.BuildOverflowException) {
          null
        }
        CachedValueProvider.Result.create(graph, PsiModificationTracker.MODIFICATION_COUNT)
      }
    }
  }
}

private class VisitorWithVariablesTracking(
  val currentScope: LocalScopeContext = LocalScopeContext(null),
  var currentDepth: Int,
  val dependents: MutableMap<UElement, MutableSet<Dependent>> = mutableMapOf(),
  val dependencies: MutableMap<UElement, MutableSet<Dependency>> = mutableMapOf()
) : AbstractUastVisitor() {

  private val elementsProcessedAsReceiver: MutableSet<UExpression> = THashSet()

  private fun createVisitor(scope: LocalScopeContext) =
    VisitorWithVariablesTracking(scope, currentDepth, dependents, dependencies)

  inline fun checkedDepthCall(node: UElement, body: () -> Boolean): Boolean {
    currentDepth++
    try {
      if (currentDepth > maxBuildDepth) {
        LOG.info("build overflow in $node because depth is greater than $maxBuildDepth")
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
    if (node.operationKind != UastBinaryExpressionWithTypeKind.TYPE_CAST) return@checkedDepthCall super.visitBinaryExpressionWithType(node)
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

    currentScope[node.identifier]?.let { registerDependency(Dependent.CommonDependent(node), Dependency.BranchingDependency(it)) }
    return@checkedDepthCall super.visitSimpleNameReferenceExpression(node)
  }

  override fun visitLocalVariable(node: ULocalVariable): Boolean = checkedDepthCall(node) {
    ProgressManager.checkCanceled()
    val name = node.name
    currentScope.declare(node)
    val initializer = node.uastInitializer ?: return@checkedDepthCall super.visitLocalVariable(node)
    currentScope[name] = initializer.extractBranchesResultAsDependency().elements
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
  override fun visitClass(node: UClass): Boolean {
    return true
  }

  // Ignore field nodes
  override fun visitField(node: UField): Boolean {
    return true
  }

  private fun registerDependency(dependent: Dependent,
                                 dependency: Dependency) {
    for (el in dependency.elements) {
      dependents.getOrPut(el) { THashSet() }.add(dependent)
    }
    dependencies.getOrPut(dependent.element) { THashSet() }.add(dependency)
  }

  companion object {
    val maxBuildDepth = Registry.intValue("reactor.default.recursion.depth.limit", 30)

    object BuildOverflowException : RuntimeException("graph building is overflowed", null, false, false)
  }
}

private fun UExpression.extractBranchesResultAsDependency(): Dependency {
  val branchResults = THashSet<UExpression>().apply { accumulateBranchesResult(this) }

  if (branchResults.size > 1)
    return Dependency.BranchingDependency(branchResults)
  return Dependency.CommonDependency(branchResults.firstOrNull() ?: this)
}

private fun UExpression.accumulateBranchesResult(results: MutableSet<UExpression>) {
  when (this) {
    is UIfExpression -> {
      thenExpression?.lastExpression?.accumulateBranchesResult(results)
      elseExpression?.lastExpression?.accumulateBranchesResult(results)
    }
    is USwitchExpression -> body.expressions.filterIsInstance<USwitchClauseExpression>()
      .mapNotNull { it.lastExpression }
      .forEach { it.accumulateBranchesResult(results) }
    is UTryExpression -> {
      tryClause.lastExpression?.accumulateBranchesResult(results)
      catchClauses.mapNotNull { it.body.lastExpression }.forEach { it.accumulateBranchesResult(results) }
    }
    else -> results += this
  }
}

private val UExpression.lastExpression: UExpression?
  get() = when (this) {
    is USwitchClauseExpressionWithBody -> {
      //FIXME: workaround for KT-35574
      if (lang.id == "kotlin" && getParentOfType<USwitchExpression>()?.getExpressionType() != null) {
        // skip last break statement, which doesn't make sense
        body.expressions.getOrNull(body.expressions.lastIndex - 1)
      } else {
        body.expressions.lastOrNull()
      }
    }
    is UBlockExpression -> this.expressions.lastOrNull()
    is UExpressionList -> this.expressions.lastOrNull()
    else -> this
  }?.let { expression ->
    when(expression) {
      is UYieldExpression -> expression.expression
      else -> expression
    }
  }

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

  data class BranchingDependency(override val elements: Set<UElement>) : Dependency()

  fun and(other: Dependency): Dependency {
    return BranchingDependency(elements + other.elements)
  }
}

@Suppress("MemberVisibilityCanBePrivate")
object KotlinExtensionConstants {
  const val STANDARD_CLASS = "kotlin.StandardKt__StandardKt"
  const val LET_METHOD = "let"
  const val ALSO_METHOD = "also"
  const val DEFAULT_LAMBDA_ARGUMENT_NAME = "it"

  fun isExtensionFunctionToIgnore(call: UCallExpression): Boolean =
    call
      .takeIf { it.methodName == LET_METHOD || it.methodName == ALSO_METHOD }
      ?.resolve()
      ?.containingClass?.qualifiedName == STANDARD_CLASS
}

private class LocalScopeContext(private val parent: LocalScopeContext?) {
  private val definedInScopeVariables = THashSet<UElement>()
  private val definedInScopeVariablesNames = THashSet<String>()

  private val lastAssignmentOf = mutableMapOf<UElement, Set<UElement>>()
  private val lastDeclarationOf = mutableMapOf<String?, UElement>()

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

  val variables: Iterable<UElement>
    get() = generateSequence(this) { it.parent }
      .flatMap { it.definedInScopeVariables.asSequence() }
      .asIterable()

  fun mergeWith(others: Iterable<LocalScopeContext>) {
    for (variable in variables) {
      this[variable] = THashSet<UElement>().apply {
        for (other in others) {
          other[variable]?.let { addAll(it) }
          other[variable]?.let { addAll(it) }
        }
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
}

private const val UAST_KT_ELVIS_NAME = "elvis"