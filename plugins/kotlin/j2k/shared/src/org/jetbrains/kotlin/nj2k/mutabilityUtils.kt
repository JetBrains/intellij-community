// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k

import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal
import com.intellij.psi.*
import com.intellij.psi.controlFlow.ControlFlowUtil
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.JavaPsiConstructorUtil
import com.intellij.util.MathUtil
import com.siyeh.ig.psiutils.FinalUtils
import com.siyeh.ig.psiutils.VariableAccessUtils
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.MINUSMINUS
import org.jetbrains.kotlin.nj2k.tree.JKOperatorToken.Companion.PLUSPLUS
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal fun getImmutableLocalVariablesInBlock(body: PsiCodeBlock?): Set<PsiVariable>? {
    if (body == null) return null
    val flow = LocalCanBeFinal.getControlFlow(body) ?: return null
    val start = flow.getStartOffset(body)
    val end = flow.getEndOffset(body)
    val writtenVariables = ControlFlowUtil.getWrittenVariables(flow, start, end, false)
    val result = HashSet<PsiVariable>()
    body.accept(object : JavaRecursiveElementWalkingVisitor() {
        override fun visitCodeBlock(block: PsiCodeBlock) {
            if (block.getParent() is PsiLambdaExpression && block !== body) {
                val descriptors = getImmutableLocalVariablesInBlock(block)
                if (descriptors != null) {
                    result.addAll(descriptors)
                }
                return
            }
            super.visitCodeBlock(block)
            val declared = getDeclaredVariables(block)
            if (declared.isEmpty()) return
            var anchor: PsiElement = block
            if (block.getParent() is PsiSwitchBlock) {
                anchor = block.getParent()

                //special case: switch legs
                val writeRefs = SyntaxTraverser.psiTraverser().withRoot(block)
                    .filter(PsiReferenceExpression::class.java)
                    .filter { ref -> PsiUtil.isOnAssignmentLeftHand(ref) }
                    .toSet()
                for (ref in writeRefs) {
                    val resolve = ref.resolve()
                    if (resolve is PsiVariable && declared.contains(resolve) && resolve.hasInitializer()) {
                        declared.remove(resolve)
                    }
                }
            }
            val from = flow.getStartOffset(anchor)
            val codeBlockEnd = flow.getEndOffset(anchor)
            val ssa = ControlFlowUtil.getSSAVariables(flow, from, codeBlockEnd, true)
            for (psiVariable in ssa) {
                if (declared.contains(psiVariable) && (!psiVariable.hasInitializer() || !VariableAccessUtils.variableIsAssigned(
                        psiVariable,
                        block
                    ))
                ) {
                    result.add(psiVariable)
                }
            }
        }

        override fun visitResourceVariable(variable: PsiResourceVariable) {
            if (PsiTreeUtil.getParentOfType(variable, PsiClass::class.java) !== PsiTreeUtil.getParentOfType(
                    body,
                    PsiClass::class.java
                )
            ) {
                return
            }
            result.add(variable)
        }

        override fun visitPatternVariable(variable: PsiPatternVariable) {
            super.visitPatternVariable(variable)
            if (PsiTreeUtil.getParentOfType(variable, PsiClass::class.java) !== PsiTreeUtil.getParentOfType(
                    body,
                    PsiClass::class.java
                )
            ) {
                return
            }
            val context = PsiTreeUtil.getParentOfType(
                variable,
                PsiInstanceOfExpression::class.java,
                PsiSwitchLabelStatementBase::class.java,
                PsiForeachPatternStatement::class.java,
                PsiForeachStatement::class.java
            )
            var from: Int
            var patternVarEnd: Int
            when (context) {
                is PsiInstanceOfExpression -> {
                    from = flow.getEndOffset(context)
                    patternVarEnd = flow.getEndOffset(body)
                }

                is PsiSwitchLabelStatementBase -> {
                    val guardExpression: PsiExpression? = context.guardExpression
                    from = if (guardExpression != null) {
                        flow.getStartOffset(guardExpression)
                    } else {
                        flow.getEndOffset(context)
                    }
                    patternVarEnd = flow.getEndOffset(body)
                }

                is PsiForeachPatternStatement -> {
                    val contextBody: PsiStatement = context.getBody() ?: return
                    from = flow.getStartOffset(contextBody)
                    patternVarEnd = flow.getEndOffset(contextBody)
                }

                else -> return
            }

            from = MathUtil.clamp(from, 0, flow.getInstructions().size)
            patternVarEnd = MathUtil.clamp(patternVarEnd, from, flow.getInstructions().size)
            if (!ControlFlowUtil.getWrittenVariables(flow, from, patternVarEnd, false).contains(variable)) {
                writtenVariables.remove(variable)
                result.add(variable)
            }
        }

        private fun getDeclaredVariables(block: PsiCodeBlock): MutableSet<PsiVariable> {
            val declaredResult = HashSet<PsiVariable>()
            val children = block.getChildren()
            for (child in children) {
                child.accept(object : JavaElementVisitor() {
                    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                        visitReferenceElement(expression)
                    }

                    override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
                        val declaredElements = statement.getDeclaredElements()
                        for (declaredElement in declaredElements) {
                            if (declaredElement is PsiVariable) declaredResult.add(declaredElement)
                        }
                    }

                    override fun visitForStatement(statement: PsiForStatement) {
                        super.visitForStatement(statement)
                        val initialization = statement.getInitialization() as? PsiDeclarationStatement ?: return
                        val declaredElements = initialization.getDeclaredElements()
                        for (declaredElement in declaredElements) {
                            if (declaredElement is PsiVariable) {
                                declaredResult.add(declaredElement)
                            }
                        }
                    }
                })
            }
            return declaredResult
        }
    })
    return HashSet(result)
}

internal fun canBeImmutable(variable: PsiVariable): Boolean {
    if (variable.getInitializer() != null || variable is PsiParameter) {
        // parameters have an implicit initializer
        return !VariableAccessUtils.variableIsAssigned(variable)
    }
    return if (variable is PsiField && fieldConstructionImpliesMutable(variable)) {
        false
    } else checkIfElementViolatesImmutability(variable)
}

private fun fieldConstructionImpliesMutable(field: PsiField): Boolean {
    val aClass = field.getContainingClass() ?: return false
    // instance field should be initialized at the end of each constructor
    val constructors = aClass.getConstructors()
    val usefulConstructors: MutableList<PsiMethod> = ArrayList()
    for (constructor in constructors) {
        val ctrBody = constructor.getBody() ?: return true
        val redirectedConstructors = JavaPsiConstructorUtil.getChainedConstructors(constructor)
        val usefulRedirectedConstructors: MutableList<PsiMethod> = ArrayList()
        for (redirectedConstructor in redirectedConstructors) {
            val body = redirectedConstructor.getBody()
            if (body != null && (ControlFlowUtil.variableDefinitelyAssignedIn(field, body) || isValidThisMethodInConstructor(
                    redirectedConstructor
                ))
            ) {
                usefulRedirectedConstructors.add(redirectedConstructor)
            }
        }
        if (usefulRedirectedConstructors.isNotEmpty() && usefulRedirectedConstructors.size != redirectedConstructors.size) return true
        if (ctrBody.isValid() && (ControlFlowUtil.variableDefinitelyAssignedIn(field, ctrBody) || isValidThisMethodInConstructor(
                constructor
            ))
        ) {
            usefulConstructors.add(constructor)
        }
    }
    return usefulConstructors.isNotEmpty() && usefulConstructors.size != constructors.size
}

private fun isValidThisMethodInConstructor(constructor: PsiMethod): Boolean {
    val children = constructor.getChildren().filterIsInstance<PsiCodeBlock>().flatMap { it.children.toList() }.toMutableList()
    while (children.isNotEmpty()) {
        val child = children.removeFirst()
        if (child !is PsiMethodCallExpression) {
            children.addAll(child.children)
            continue
        }
        if (child.getMethodExpression().getQualifiedName() == "this") {
            return true
        }
    }
    return false
}


private fun checkIfElementViolatesImmutability(variable: PsiVariable): Boolean {
    val scope =
        (if (variable is PsiField) PsiUtil.getTopLevelClass(variable) else PsiUtil.getVariableCodeBlock(variable, null))
            ?: return false
    val finalVarProblems: Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>> = HashMap()
    val uninitializedVarProblems: Map<PsiElement, Collection<PsiReferenceExpression>> = HashMap()
    val elementDoesNotViolateFinality =
        PsiElementProcessor { e: PsiElement ->
            checkElementDoesNotViolateImmutability(
                e,
                variable,
                uninitializedVarProblems,
                finalVarProblems
            )
        }
    return PsiTreeUtil.processElements(scope, elementDoesNotViolateFinality)
}

private fun checkElementDoesNotViolateImmutability(
    e: PsiElement,
    variable: PsiVariable,
    uninitializedVarProblems: Map<PsiElement, Collection<PsiReferenceExpression>>,
    finalVarProblems: Map<PsiElement, Collection<ControlFlowUtil.VariableInfo>>
): Boolean {
    if (!FinalUtils.checkElementDoesNotViolateFinality(e, variable, uninitializedVarProblems, finalVarProblems)) return false
    if (e !is PsiReferenceExpression) return true
    if (!e.isReferenceTo(variable)) return true
    if (variable !is PsiField) return true
    val enclosingInitializer = PsiUtil.findEnclosingConstructorOrInitializer(e) ?: return true
    var parent = e
    val conditionals: MutableCollection<PsiIfStatement> = ArrayList()
    while (parent !== enclosingInitializer) {
        parent = parent.getParent()
        if (parent is PsiIfStatement) {
            conditionals.add(parent)
            break
        }
    }
    return conditionals.isEmpty()
}

private fun JKFieldAccessExpression.asAssignmentFromTarget(): JKKtAssignmentStatement? =
    parent.safeAs<JKKtAssignmentStatement>()?.takeIf { it.field == this }

private fun JKFieldAccessExpression.asParenthesizedAssignmentFromTarget(): JKParenthesizedExpression? =
    parent.safeAs<JKParenthesizedExpression>()?.takeIf { it.parent is JKKtAssignmentStatement && it.expression == this }

private fun JKFieldAccessExpression.asQualifiedAssignmentFromTarget(): JKQualifiedExpression? =
    parent.safeAs<JKQualifiedExpression>()?.takeIf {
        val operatorToken = it.parent.safeAs<JKUnaryExpression>()?.operator?.token
        it.selector == this &&
                (it.parent is JKKtAssignmentStatement || operatorToken == PLUSPLUS || operatorToken == MINUSMINUS)
    }

context(_: KaSession)
private fun JKVariable.findWritableUsages(scope: JKTreeElement, context: ConverterContext): List<JKFieldAccessExpression> =
    findUsages(scope, context).filter {
        it.asAssignmentFromTarget() != null
                || it.isInDecrementOrIncrement() || it.asQualifiedAssignmentFromTarget() != null
                || it.asParenthesizedAssignmentFromTarget() != null
    }.distinct()

context(_: KaSession)
fun JKVariable.hasWritableUsages(scope: JKTreeElement, context: ConverterContext): Boolean =
    findWritableUsages(scope, context).isNotEmpty()


// JPA and @Volatile fields should always be mutable
val MUTABLE_ANNOTATIONS = setOf("kotlin.concurrent.Volatile", "javax.persistence.Column", "jakarta.persistence.Column")