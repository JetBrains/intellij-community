// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitor

internal abstract class JKStatement : JKTreeElement(), PsiOwner by PsiOwnerImpl()

internal class JKEmptyStatement : JKStatement() {
    override fun accept(visitor: JKVisitor) = visitor.visitEmptyStatement(this)
}

internal abstract class JKLoopStatement : JKStatement() {
    abstract var body: JKStatement
}

internal class JKWhileStatement(condition: JKExpression, body: JKStatement) : JKLoopStatement() {
    var condition by child(condition)
    override var body by child(body)
    override fun accept(visitor: JKVisitor) = visitor.visitWhileStatement(this)
}

internal class JKDoWhileStatement(body: JKStatement, condition: JKExpression) : JKLoopStatement() {
    var condition by child(condition)
    override var body by child(body)
    override fun accept(visitor: JKVisitor) = visitor.visitDoWhileStatement(this)
}

internal class JKForInStatement(declaration: JKDeclaration, iterationExpression: JKExpression, body: JKStatement) : JKStatement() {
    var declaration: JKDeclaration by child(declaration)
    var iterationExpression: JKExpression by child(iterationExpression)
    var body: JKStatement by child(body)
    override fun accept(visitor: JKVisitor) = visitor.visitForInStatement(this)
}

internal class JKIfElseStatement(condition: JKExpression, thenBranch: JKStatement, elseBranch: JKStatement) : JKStatement() {
    var condition by child(condition)
    var thenBranch by child(thenBranch)
    var elseBranch by child(elseBranch)
    override fun accept(visitor: JKVisitor) = visitor.visitIfElseStatement(this)
}

internal class JKBreakStatement(label: JKLabel) : JKStatement() {
    val label: JKLabel by child(label)
    override fun accept(visitor: JKVisitor) = visitor.visitBreakStatement(this)
}

internal class JKJavaYieldStatement(expression: JKExpression) : JKStatement() {
    val expression: JKExpression by child(expression)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaYieldStatement(this)
}

internal class JKContinueStatement(label: JKLabel) : JKStatement() {
    var label: JKLabel by child(label)
    override fun accept(visitor: JKVisitor) = visitor.visitContinueStatement(this)
}

internal class JKBlockStatement(block: JKBlock) : JKStatement() {
    var block by child(block)
    override fun accept(visitor: JKVisitor) = visitor.visitBlockStatement(this)
}

internal class JKBlockStatementWithoutBrackets(statements: List<JKStatement>) : JKStatement() {
    var statements by children(statements)
    override fun accept(visitor: JKVisitor) = visitor.visitBlockStatementWithoutBrackets(this)
}

internal class JKExpressionStatement(expression: JKExpression) : JKStatement() {
    var expression: JKExpression by child(expression)
    override fun accept(visitor: JKVisitor) = visitor.visitExpressionStatement(this)
}

internal class JKDeclarationStatement(declaredStatements: List<JKDeclaration>) : JKStatement() {
    val declaredStatements by children(declaredStatements)
    override fun accept(visitor: JKVisitor) = visitor.visitDeclarationStatement(this)
}

internal class JKKtWhenStatement(
    expression: JKExpression,
    cases: List<JKKtWhenCase>
) : JKStatement(), JKKtWhenBlock {
    override var expression: JKExpression by child(expression)
    override var cases: List<JKKtWhenCase> by children(cases)

    override fun accept(visitor: JKVisitor) = visitor.visitKtWhenStatement(this)
}

internal class JKKtConvertedFromForLoopSyntheticWhileStatement(
    variableDeclarations: List<JKStatement>,
    whileStatement: JKWhileStatement
) : JKStatement() {
    var variableDeclarations: List<JKStatement> by children(variableDeclarations)
    var whileStatement: JKWhileStatement by child(whileStatement)
    override fun accept(visitor: JKVisitor) = visitor.visitKtConvertedFromForLoopSyntheticWhileStatement(this)
}

internal class JKKtAssignmentStatement(
    field: JKExpression,
    expression: JKExpression,
    var token: JKOperatorToken
) : JKStatement() {
    var field: JKExpression by child(field)
    var expression by child(expression)
    override fun accept(visitor: JKVisitor) = visitor.visitKtAssignmentStatement(this)
}

internal class JKReturnStatement(
    expression: JKExpression,
    label: JKLabel = JKLabelEmpty()
) : JKStatement() {
    val expression by child(expression)
    var label by child(label)
    override fun accept(visitor: JKVisitor) = visitor.visitReturnStatement(this)
}

internal class JKJavaSwitchStatement(
    expression: JKExpression,
    cases: List<JKJavaSwitchCase>
) : JKStatement(), JKJavaSwitchBlock {
    override var expression: JKExpression by child(expression)
    override var cases: List<JKJavaSwitchCase> by children(cases)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaSwitchStatement(this)
}

internal class JKJavaTryStatement(
    resourceDeclarations: List<JKJavaResourceElement>,
    tryBlock: JKBlock,
    finallyBlock: JKBlock,
    catchSections: List<JKJavaTryCatchSection>
) : JKStatement() {
    var resourceDeclarations: List<JKJavaResourceElement> by children(resourceDeclarations)
    var tryBlock: JKBlock by child(tryBlock)
    var finallyBlock: JKBlock by child(finallyBlock)
    var catchSections: List<JKJavaTryCatchSection> by children(catchSections)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaTryStatement(this)

    val isTryWithResources get() = resourceDeclarations.isNotEmpty()
}

internal class JKJavaSynchronizedStatement(
    lockExpression: JKExpression,
    body: JKBlock
) : JKStatement() {
    val lockExpression: JKExpression by child(lockExpression)
    val body: JKBlock by child(body)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaSynchronizedStatement(this)
}

internal class JKJavaAssertStatement(condition: JKExpression, description: JKExpression) : JKStatement() {
    val description by child(description)
    val condition by child(condition)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaAssertStatement(this)
}

internal class JKJavaForLoopStatement(
    initializers: List<JKStatement>,
    condition: JKExpression,
    updaters: List<JKStatement>,
    body: JKStatement
) : JKLoopStatement() {
    override var body by child(body)
    var updaters by children(updaters)
    var condition by child(condition)
    var initializers by children(initializers)

    override fun accept(visitor: JKVisitor) = visitor.visitJavaForLoopStatement(this)
}

internal class JKJavaAnnotationMethod(
    returnType: JKTypeElement,
    name: JKNameIdentifier,
    defaultValue: JKAnnotationMemberValue,
    annotationList: JKAnnotationList,
    otherModifierElements: List<JKOtherModifierElement>,
    visibilityElement: JKVisibilityModifierElement,
    modalityElement: JKModalityModifierElement
) : JKMethod(), JKAnnotationListOwner, JKTypeParameterListOwner {
    override var returnType: JKTypeElement by child(returnType)
    override var name: JKNameIdentifier by child(name)
    override var parameters: List<JKParameter> by children()
    var defaultValue: JKAnnotationMemberValue by child(defaultValue)
    override var block: JKBlock by child(JKBodyStub)
    override var typeParameterList: JKTypeParameterList by child(JKTypeParameterList())
    override var annotationList: JKAnnotationList by child(annotationList)
    override var otherModifierElements by children(otherModifierElements)
    override var visibilityElement by child(visibilityElement)
    override var modalityElement by child(modalityElement)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaAnnotationMethod(this)
}

internal class JKErrorStatement(override var psi: PsiElement?, override val reason: String? = null) : JKStatement(), JKErrorElement {
    override fun accept(visitor: JKVisitor) = visitor.visitErrorStatement(this)
}