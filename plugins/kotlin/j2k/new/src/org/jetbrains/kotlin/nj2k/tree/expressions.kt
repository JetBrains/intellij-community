// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree


import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiSwitchExpression
import org.jetbrains.kotlin.nj2k.symbols.*
import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitor
import org.jetbrains.kotlin.nj2k.types.JKContextType
import org.jetbrains.kotlin.nj2k.types.JKNoType
import org.jetbrains.kotlin.nj2k.types.JKType
import org.jetbrains.kotlin.nj2k.types.JKTypeFactory

abstract class JKExpression : JKAnnotationMemberValue(), PsiOwner by PsiOwnerImpl() {
    protected abstract val expressionType: JKType?

    open fun calculateType(typeFactory: JKTypeFactory): JKType? {
        val psiType = (psi as? PsiExpression)?.type ?: return null
        return typeFactory.fromPsiType(psiType)
    }
}

abstract class JKOperatorExpression : JKExpression() {
    abstract var operator: JKOperator

    override fun calculateType(typeFactory: JKTypeFactory) = expressionType ?: operator.returnType
}

class JKBinaryExpression(
    left: JKExpression,
    right: JKExpression,
    override var operator: JKOperator,
    override val expressionType: JKType? = null,
) : JKOperatorExpression() {
    var left by child(left)
    var right by child(right)

    override fun accept(visitor: JKVisitor) = visitor.visitBinaryExpression(this)
}

abstract class JKUnaryExpression : JKOperatorExpression() {
    abstract var expression: JKExpression
}

class JKPrefixExpression(
    expression: JKExpression,
    override var operator: JKOperator,
    override val expressionType: JKType? = null,
) : JKUnaryExpression() {
    override var expression by child(expression)
    override fun accept(visitor: JKVisitor) = visitor.visitPrefixExpression(this)
}

class JKPostfixExpression(
    expression: JKExpression,
    override var operator: JKOperator,
    override val expressionType: JKType? = null
) : JKUnaryExpression() {
    override var expression by child(expression)
    override fun accept(visitor: JKVisitor) = visitor.visitPostfixExpression(this)
}

class JKQualifiedExpression(
    receiver: JKExpression,
    selector: JKExpression,
    override val expressionType: JKType? = null,
) : JKExpression() {
    var receiver: JKExpression by child(receiver)
    var selector: JKExpression by child(selector)
    override fun accept(visitor: JKVisitor) = visitor.visitQualifiedExpression(this)
}

/**
 * @param shouldBePreserved - parentheses came from original Java code and should be preserved
 * (don't run "Remove unnecessary parentheses" inspection on them)
 */
class JKParenthesizedExpression(
    expression: JKExpression,
    override val expressionType: JKType? = null,
    val shouldBePreserved: Boolean = false
) : JKExpression() {
    var expression: JKExpression by child(expression)
    override fun calculateType(typeFactory: JKTypeFactory) = expressionType ?: expression.calculateType(typeFactory)
    override fun accept(visitor: JKVisitor) = visitor.visitParenthesizedExpression(this)
}

class JKTypeCastExpression(
    expression: JKExpression,
    type: JKTypeElement,
    override val expressionType: JKType? = null,
) : JKExpression() {
    var expression by child(expression)
    var type by child(type)
    override fun calculateType(typeFactory: JKTypeFactory) = expressionType ?: type.type
    override fun accept(visitor: JKVisitor) = visitor.visitTypeCastExpression(this)
}

class JKLiteralExpression(
    var literal: String,
    val type: LiteralType,
    override val expressionType: JKType? = null,
) : JKExpression() {
    override fun accept(visitor: JKVisitor) = visitor.visitLiteralExpression(this)

    override fun calculateType(typeFactory: JKTypeFactory): JKType {
        expressionType?.let { return it }
        return when (type) {
            LiteralType.CHAR -> typeFactory.types.char
            LiteralType.BOOLEAN -> typeFactory.types.boolean
            LiteralType.INT -> typeFactory.types.int
            LiteralType.LONG -> typeFactory.types.long
            LiteralType.FLOAT -> typeFactory.types.float
            LiteralType.DOUBLE -> typeFactory.types.double
            LiteralType.NULL -> typeFactory.types.nullableAny
            LiteralType.STRING, LiteralType.TEXT_BLOCK -> typeFactory.types.string
        }
    }

    enum class LiteralType {
        STRING, TEXT_BLOCK, CHAR, BOOLEAN, NULL, INT, LONG, FLOAT, DOUBLE
    }
}

class JKStubExpression(override val expressionType: JKType? = null) : JKExpression() {
    override fun calculateType(typeFactory: JKTypeFactory): JKType? = expressionType
    override fun accept(visitor: JKVisitor) = visitor.visitStubExpression(this)
}

/**
 * @param shouldBePreserved - this is an original `this` expression from Java code
 * that should be preserved in Kotlin (don't run "Redundant explicit 'this'" inspection on it automatically).
 */
class JKThisExpression(
    qualifierLabel: JKLabel,
    override val expressionType: JKType? = null,
    val shouldBePreserved: Boolean = false
) : JKExpression() {
    var qualifierLabel: JKLabel by child(qualifierLabel)
    override fun accept(visitor: JKVisitor) = visitor.visitThisExpression(this)
}

class JKSuperExpression(
    override val expressionType: JKType = JKNoType,
    val superTypeQualifier: JKClassSymbol? = null,
    outerTypeQualifier: JKLabel = JKLabelEmpty(),
) : JKExpression() {
    var outerTypeQualifier: JKLabel by child(outerTypeQualifier)
    override fun accept(visitor: JKVisitor) = visitor.visitSuperExpression(this)
}

class JKIfElseExpression(
    condition: JKExpression,
    thenBranch: JKExpression,
    elseBranch: JKExpression,
    override val expressionType: JKType? = null,
) : JKExpression() {
    var condition by child(condition)
    var thenBranch by child(thenBranch)
    var elseBranch by child(elseBranch)

    override fun accept(visitor: JKVisitor) = visitor.visitIfElseExpression(this)
}

class JKLambdaExpression(
    statement: JKStatement,
    parameters: List<JKParameter> = emptyList(),
    functionalType: JKTypeElement = JKTypeElement(JKNoType),
    returnType: JKTypeElement = JKTypeElement(JKContextType),
    override val expressionType: JKType? = null,
) : JKExpression() {
    var statement by child(statement)
    var parameters by children(parameters)
    var functionalType by child(functionalType)
    val returnType by child(returnType)

    override fun accept(visitor: JKVisitor) = visitor.visitLambdaExpression(this)
}

abstract class JKCallExpression : JKExpression(), JKTypeArgumentListOwner {
    abstract var identifier: JKMethodSymbol
    abstract var arguments: JKArgumentList
}

class JKDelegationConstructorCall(
    override var identifier: JKMethodSymbol,
    expression: JKExpression,
    arguments: JKArgumentList,
    override val expressionType: JKType? = null,
) : JKCallExpression() {
    override var typeArgumentList: JKTypeArgumentList by child(JKTypeArgumentList())
    val expression: JKExpression by child(expression)
    override var arguments: JKArgumentList by child(arguments)
    override fun accept(visitor: JKVisitor) = visitor.visitDelegationConstructorCall(this)
}

class JKCallExpressionImpl(
    override var identifier: JKMethodSymbol,
    arguments: JKArgumentList = JKArgumentList(),
    typeArgumentList: JKTypeArgumentList = JKTypeArgumentList(),
    override val expressionType: JKType? = null,
) : JKCallExpression() {
    override var typeArgumentList by child(typeArgumentList)
    override var arguments by child(arguments)
    override fun accept(visitor: JKVisitor) = visitor.visitCallExpressionImpl(this)
}

class JKNewExpression(
    val classSymbol: JKClassSymbol,
    arguments: JKArgumentList,
    typeArgumentList: JKTypeArgumentList = JKTypeArgumentList(),
    classBody: JKClassBody = JKClassBody(),
    val isAnonymousClass: Boolean = false,
    override val expressionType: JKType? = null,
) : JKExpression() {
    var typeArgumentList by child(typeArgumentList)
    var arguments by child(arguments)
    var classBody by child(classBody)
    override fun accept(visitor: JKVisitor) = visitor.visitNewExpression(this)
}

class JKFieldAccessExpression(
    var identifier: JKFieldSymbol,
    override val expressionType: JKType? = null,
) : JKExpression() {
    override fun accept(visitor: JKVisitor) = visitor.visitFieldAccessExpression(this)
}

class JKPackageAccessExpression(var identifier: JKPackageSymbol) : JKExpression() {
    override val expressionType: JKType? get() = null
    override fun calculateType(typeFactory: JKTypeFactory): JKType? = null
    override fun accept(visitor: JKVisitor) = visitor.visitPackageAccessExpression(this)
}

class JKClassAccessExpression(
    var identifier: JKClassSymbol, override val expressionType: JKType? = null,
) : JKExpression() {
    override fun accept(visitor: JKVisitor) = visitor.visitClassAccessExpression(this)
}

class JKMethodAccessExpression(val identifier: JKMethodSymbol, override val expressionType: JKType? = null) : JKExpression() {
    override fun accept(visitor: JKVisitor) = visitor.visitMethodAccessExpression(this)
}

class JKTypeQualifierExpression(val type: JKType, override val expressionType: JKType? = null) : JKExpression() {
    override fun accept(visitor: JKVisitor) = visitor.visitTypeQualifierExpression(this)
}

class JKMethodReferenceExpression(
    qualifier: JKExpression,
    val identifier: JKSymbol,
    functionalType: JKTypeElement,
    val isConstructorCall: Boolean,
    override val expressionType: JKType? = null,
) : JKExpression() {
    val qualifier by child(qualifier)
    val functionalType by child(functionalType)
    override fun accept(visitor: JKVisitor) = visitor.visitMethodReferenceExpression(this)
}

class JKLabeledExpression(
    statement: JKStatement,
    labels: List<JKNameIdentifier>,
    override val expressionType: JKType? = null,
) : JKExpression() {
    var statement: JKStatement by child(statement)
    val labels: List<JKNameIdentifier> by children(labels)
    override fun accept(visitor: JKVisitor) = visitor.visitLabeledExpression(this)
}

class JKClassLiteralExpression(
    classType: JKTypeElement,
    var literalType: ClassLiteralType,
    override val expressionType: JKType? = null,
) : JKExpression() {
    val classType: JKTypeElement by child(classType)

    override fun accept(visitor: JKVisitor) = visitor.visitClassLiteralExpression(this)

    enum class ClassLiteralType {
        KOTLIN_CLASS,
        JAVA_CLASS,
        JAVA_PRIMITIVE_CLASS,
        JAVA_VOID_TYPE
    }
}

abstract class JKKtAssignmentChainLink : JKExpression() {
    abstract val receiver: JKExpression
    abstract val assignmentStatement: JKKtAssignmentStatement
    abstract val field: JKExpression

    override val expressionType: JKType? get() = null
    override fun calculateType(typeFactory: JKTypeFactory) = field.calculateType(typeFactory)
}

class JKAssignmentChainAlsoLink(
    receiver: JKExpression,
    assignmentStatement: JKKtAssignmentStatement,
    field: JKExpression
) : JKKtAssignmentChainLink() {
    override val receiver by child(receiver)
    override val assignmentStatement by child(assignmentStatement)
    override val field by child(field)
    override fun accept(visitor: JKVisitor) = visitor.visitAssignmentChainAlsoLink(this)
}

class JKAssignmentChainLetLink(
    receiver: JKExpression,
    assignmentStatement: JKKtAssignmentStatement,
    field: JKExpression
) : JKKtAssignmentChainLink() {
    override val receiver by child(receiver)
    override val assignmentStatement by child(assignmentStatement)
    override val field by child(field)
    override fun accept(visitor: JKVisitor) = visitor.visitAssignmentChainLetLink(this)
}

class JKIsExpression(expression: JKExpression, type: JKTypeElement) : JKExpression() {
    var type by child(type)
    var expression by child(expression)
    override val expressionType: JKType? get() = null
    override fun calculateType(typeFactory: JKTypeFactory) = typeFactory.types.boolean
    override fun accept(visitor: JKVisitor) = visitor.visitIsExpression(this)
}

class JKKtItExpression(override val expressionType: JKType) : JKExpression() {
    override fun accept(visitor: JKVisitor) = visitor.visitKtItExpression(this)
}

class JKKtAnnotationArrayInitializerExpression(
    initializers: List<JKAnnotationMemberValue>,
    override val expressionType: JKType? = null
) : JKExpression() {
    constructor(vararg initializers: JKAnnotationMemberValue) : this(initializers.toList())

    val initializers: List<JKAnnotationMemberValue> by children(initializers)
    override fun calculateType(typeFactory: JKTypeFactory): JKType? = expressionType
    override fun accept(visitor: JKVisitor) = visitor.visitKtAnnotationArrayInitializerExpression(this)
}

class JKKtTryExpression(
    tryBlock: JKBlock,
    finallyBlock: JKBlock,
    catchSections: List<JKKtTryCatchSection>,
    override val expressionType: JKType? = null,
) : JKExpression() {
    var tryBlock: JKBlock by child(tryBlock)
    var finallyBlock: JKBlock by child(finallyBlock)
    var catchSections: List<JKKtTryCatchSection> by children(catchSections)

    override fun accept(visitor: JKVisitor) = visitor.visitKtTryExpression(this)
}

class JKThrowExpression(exception: JKExpression) : JKExpression() {
    var exception: JKExpression by child(exception)
    override val expressionType: JKType? get() = null
    override fun calculateType(typeFactory: JKTypeFactory) = typeFactory.types.nothing
    override fun accept(visitor: JKVisitor) = visitor.visitKtThrowExpression(this)
}

class JKJavaNewEmptyArray(
    initializer: List<JKExpression>,
    type: JKTypeElement,
    override val expressionType: JKType? = null
) : JKExpression() {
    val type by child(type)
    var initializer by children(initializer)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaNewEmptyArray(this)
}

/**
 * @param hasTrailingComma - Java array initializer has a trailing comma
 */
class JKJavaNewArray(
    initializer: List<JKExpression>,
    type: JKTypeElement,
    val hasTrailingComma: Boolean,
    override val expressionType: JKType? = null
) : JKExpression() {
    val type by child(type)
    var initializer by children(initializer)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaNewArray(this)
}

class JKJavaAssignmentExpression(
    field: JKExpression,
    expression: JKExpression,
    var operator: JKOperator,
    override val expressionType: JKType? = null
) : JKExpression() {
    var field by child(field)
    var expression by child(expression)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaAssignmentExpression(this)
}

class JKJavaSwitchExpression(
    expression: JKExpression,
    cases: List<JKJavaSwitchCase>,
    override val expressionType: JKType? = null,
) : JKExpression(), JKJavaSwitchBlock {
    override var expression: JKExpression by child(expression)
    override var cases: List<JKJavaSwitchCase> by children(cases)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaSwitchExpression(this)

    override fun calculateType(typeFactory: JKTypeFactory): JKType? {
        val psiType = (psi as? PsiSwitchExpression)?.type ?: return null
        return typeFactory.fromPsiType(psiType)
    }
}

class JKKtWhenExpression(
    expression: JKExpression,
    cases: List<JKKtWhenCase>,
    override val expressionType: JKType?,
) : JKExpression(), JKKtWhenBlock {
    override var expression: JKExpression by child(expression)
    override var cases: List<JKKtWhenCase> by children(cases)
    override fun accept(visitor: JKVisitor) = visitor.visitKtWhenExpression(this)

    override fun calculateType(typeFactory: JKTypeFactory): JKType? = expressionType
}

class JKErrorExpression(
    override var psi: PsiElement?,
    override val reason: String?,
    override val expressionType: JKType? = null
) : JKExpression(), JKErrorElement {
    override fun calculateType(typeFactory: JKTypeFactory): JKType = expressionType ?: typeFactory.types.nothing

    override fun accept(visitor: JKVisitor) = visitor.visitErrorExpression(this)
}