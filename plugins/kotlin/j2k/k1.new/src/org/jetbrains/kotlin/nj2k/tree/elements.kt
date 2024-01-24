// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.tree

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.nj2k.symbols.JKClassSymbol
import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitor
import org.jetbrains.kotlin.nj2k.types.JKType

internal class JKTreeRoot(element: JKTreeElement) : JKTreeElement() {
    var element by child(element)
    override fun accept(visitor: JKVisitor) = visitor.visitTreeRoot(this)
}

internal class JKFile(
    packageDeclaration: JKPackageDeclaration,
    importList: JKImportList,
    declarationList: List<JKDeclaration>
) : JKTreeElement(), PsiOwner by PsiOwnerImpl() {
    override fun accept(visitor: JKVisitor) = visitor.visitFile(this)

    var packageDeclaration: JKPackageDeclaration by child(packageDeclaration)
    var importList: JKImportList by child(importList)
    var declarationList by children(declarationList)
}

internal class JKTypeElement(var type: JKType, annotationList: JKAnnotationList = JKAnnotationList()) :
    JKTreeElement(), JKAnnotationListOwner {

    override fun accept(visitor: JKVisitor) = visitor.visitTypeElement(this)
    override var annotationList: JKAnnotationList by child(annotationList)
}

internal abstract class JKBlock : JKTreeElement() {
    abstract var statements: List<JKStatement>

    val leftBrace = JKTokenElementImpl("{")
    val rightBrace = JKTokenElementImpl("}")
}

internal object JKBodyStub : JKBlock() {
    override val commentsBefore: MutableList<JKComment> = mutableListOf()
    override val commentsAfter: MutableList<JKComment> = mutableListOf()
    override var lineBreaksBefore: Int = 0
    override var lineBreaksAfter: Int = 0

    override fun copy(): JKTreeElement = this

    override var statements: List<JKStatement>
        get() = emptyList()
        set(_) {}

    override fun acceptChildren(visitor: JKVisitor) {}

    override var parent: JKElement?
        get() = null
        set(_) {}

    override fun detach(from: JKElement) {}
    override fun attach(to: JKElement) {}
    override fun accept(visitor: JKVisitor) = Unit
}

internal class JKInheritanceInfo(
    extends: List<JKTypeElement>,
    implements: List<JKTypeElement>
) : JKTreeElement() {
    var extends: List<JKTypeElement> by children(extends)
    var implements: List<JKTypeElement> by children(implements)

    override fun accept(visitor: JKVisitor) = visitor.visitInheritanceInfo(this)
}

internal class JKPackageDeclaration(name: JKNameIdentifier) : JKDeclaration() {
    override var name: JKNameIdentifier by child(name)
    override fun accept(visitor: JKVisitor) = visitor.visitPackageDeclaration(this)
}

internal abstract class JKLabel : JKTreeElement()

internal class JKLabelEmpty : JKLabel() {
    override fun accept(visitor: JKVisitor) = visitor.visitLabelEmpty(this)
}

internal class JKLabelText(label: JKNameIdentifier) : JKLabel() {
    val label: JKNameIdentifier by child(label)
    override fun accept(visitor: JKVisitor) = visitor.visitLabelText(this)
}

internal class JKImportStatement(name: JKNameIdentifier) : JKTreeElement() {
    val name: JKNameIdentifier by child(name)
    override fun accept(visitor: JKVisitor) = visitor.visitImportStatement(this)
}

internal class JKImportList(imports: List<JKImportStatement>) : JKTreeElement() {
    var imports by children(imports)
    override fun accept(visitor: JKVisitor) = visitor.visitImportList(this)
}

internal abstract class JKAnnotationParameter : JKTreeElement() {
    abstract var value: JKAnnotationMemberValue
}

internal class JKAnnotationParameterImpl(value: JKAnnotationMemberValue) : JKAnnotationParameter() {
    override var value: JKAnnotationMemberValue by child(value)

    override fun accept(visitor: JKVisitor) = visitor.visitAnnotationParameter(this)
}

internal class JKAnnotationNameParameter(
    value: JKAnnotationMemberValue,
    name: JKNameIdentifier
) : JKAnnotationParameter() {
    override var value: JKAnnotationMemberValue by child(value)
    val name: JKNameIdentifier by child(name)
    override fun accept(visitor: JKVisitor) = visitor.visitAnnotationNameParameter(this)
}

internal abstract class JKArgument : JKTreeElement() {
    abstract var value: JKExpression
}

internal class JKNamedArgument(
    value: JKExpression,
    name: JKNameIdentifier
) : JKArgument() {
    override var value by child(value)
    val name by child(name)
    override fun accept(visitor: JKVisitor) = visitor.visitNamedArgument(this)

    init {
        this.takeFormattingFrom(value)
    }
}

internal class JKArgumentImpl(value: JKExpression) : JKArgument() {
    override var value by child(value)
    override fun accept(visitor: JKVisitor) = visitor.visitArgument(this)

    init {
        this.takeFormattingFrom(value)
    }
}

/**
 * @param hasTrailingComma - a trailing comma in Java can come from an array initializer,
 * which is converted to a regular method call in Kotlin, so it belongs to JKArgumentList
 */
internal class JKArgumentList(arguments: List<JKArgument> = emptyList(), var hasTrailingComma: Boolean = false) : JKTreeElement() {
    constructor(vararg arguments: JKArgument) : this(arguments.toList())
    constructor(vararg values: JKExpression) : this(values.map { JKArgumentImpl(it) })

    var arguments by children(arguments)
    override fun accept(visitor: JKVisitor) = visitor.visitArgumentList(this)
}

internal class JKTypeParameterList(typeParameters: List<JKTypeParameter> = emptyList()) : JKTreeElement() {
    var typeParameters by children(typeParameters)
    override fun accept(visitor: JKVisitor) = visitor.visitTypeParameterList(this)
}

internal class JKAnnotationList(annotations: List<JKAnnotation> = emptyList()) : JKTreeElement() {
    var annotations: List<JKAnnotation> by children(annotations)
    override fun accept(visitor: JKVisitor) = visitor.visitAnnotationList(this)
}

internal class JKAnnotation(
    var classSymbol: JKClassSymbol,
    arguments: List<JKAnnotationParameter> = emptyList(),
    var useSiteTarget: UseSiteTarget? = null
) : JKAnnotationMemberValue() {
    var arguments: List<JKAnnotationParameter> by children(arguments)
    override fun accept(visitor: JKVisitor) = visitor.visitAnnotation(this)

    @Suppress("unused")
    enum class UseSiteTarget(val renderName: String) {
        FIELD("field"),
        FILE("file"),
        PROPERTY("property"),
        PROPERTY_GETTER("get"),
        PROPERTY_SETTER("set"),
        RECEIVER("receiver"),
        CONSTRUCTOR_PARAMETER("param"),
        SETTER_PARAMETER("setparam"),
        PROPERTY_DELEGATE_FIELD("delegate")
    }
}

internal class JKTypeArgumentList(typeArguments: List<JKTypeElement> = emptyList()) : JKTreeElement(), PsiOwner by PsiOwnerImpl() {
    constructor(vararg typeArguments: JKTypeElement) : this(typeArguments.toList())
    constructor(vararg types: JKType) : this(types.map { JKTypeElement(it) })

    var typeArguments: List<JKTypeElement> by children(typeArguments)
    override fun accept(visitor: JKVisitor) = visitor.visitTypeArgumentList(this)
}

internal class JKNameIdentifier(val value: String) : JKTreeElement() {
    override fun accept(visitor: JKVisitor) = visitor.visitNameIdentifier(this)
}

internal interface JKAnnotationListOwner : JKFormattingOwner {
    var annotationList: JKAnnotationList
}

internal class JKBlockImpl(statements: List<JKStatement> = emptyList()) : JKBlock() {
    constructor(vararg statements: JKStatement) : this(statements.toList())

    override var statements by children(statements)
    override fun accept(visitor: JKVisitor) = visitor.visitBlock(this)
}

internal class JKKtWhenCase(labels: List<JKKtWhenLabel>, statement: JKStatement) : JKTreeElement() {
    var labels: List<JKKtWhenLabel> by children(labels)
    var statement: JKStatement by child(statement)
    override fun accept(visitor: JKVisitor) = visitor.visitKtWhenCase(this)
}

internal abstract class JKKtWhenLabel : JKTreeElement()

internal class JKKtElseWhenLabel : JKKtWhenLabel() {
    override fun accept(visitor: JKVisitor) = visitor.visitKtElseWhenLabel(this)
}

internal class JKKtValueWhenLabel(expression: JKExpression) : JKKtWhenLabel() {
    var expression: JKExpression by child(expression)
    override fun accept(visitor: JKVisitor) = visitor.visitKtValueWhenLabel(this)
}

internal class JKClassBody(declarations: List<JKDeclaration> = emptyList()) : JKTreeElement() {
    var declarations: List<JKDeclaration> by children(declarations)
    override fun accept(visitor: JKVisitor) = visitor.visitClassBody(this)

    val leftBrace = JKTokenElementImpl("{")
    val rightBrace = JKTokenElementImpl("}")
}

internal class JKJavaTryCatchSection(
    parameter: JKParameter,
    block: JKBlock
) : JKStatement() {
    var parameter: JKParameter by child(parameter)
    var block: JKBlock by child(block)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaTryCatchSection(this)
}

internal sealed class JKJavaSwitchCase : JKTreeElement() {
    abstract fun isDefault(): Boolean
    abstract var statements: List<JKStatement>
}

internal class JKJavaDefaultSwitchCase(statements: List<JKStatement>) : JKJavaSwitchCase(), PsiOwner by PsiOwnerImpl() {
    override var statements: List<JKStatement> by children(statements)
    override fun isDefault(): Boolean = true
    override fun accept(visitor: JKVisitor) = visitor.visitJavaDefaultSwitchCase(this)
}

internal sealed class JKJavaLabelSwitchCase : JKJavaSwitchCase() {
    abstract val labels: List<JKExpression>
    final override fun isDefault(): Boolean = false
    override fun accept(visitor: JKVisitor) = visitor.visitJavaLabelSwitchCase(this)
}

internal class JKJavaClassicLabelSwitchCase(
    labels: List<JKExpression>,
    statements: List<JKStatement>
) : JKJavaLabelSwitchCase(), PsiOwner by PsiOwnerImpl() {
    override var statements: List<JKStatement> by children(statements)
    override var labels: List<JKExpression> by children(labels)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaClassicLabelSwitchCase(this)
}

internal class JKJavaArrowSwitchLabelCase(
    labels: List<JKExpression>,
    statements: List<JKStatement>
) : JKJavaLabelSwitchCase(), PsiOwner by PsiOwnerImpl() {
    override var statements: List<JKStatement> by children(statements)
    override var labels: List<JKExpression> by children(labels)
    override fun accept(visitor: JKVisitor) = visitor.visitJavaArrowLabelSwitchCase(this)
}

internal class JKKtTryCatchSection(
    parameter: JKParameter,
    block: JKBlock
) : JKTreeElement() {
    var parameter: JKParameter by child(parameter)
    var block: JKBlock by child(block)
    override fun accept(visitor: JKVisitor) = visitor.visitKtTryCatchSection(this)
}

internal interface JKJavaSwitchBlock : JKElement {
    val expression: JKExpression
    val cases: List<JKJavaSwitchCase>
}

internal interface JKKtWhenBlock : JKElement, JKFormattingOwner {
    val expression: JKExpression
    val cases: List<JKKtWhenCase>
}

internal sealed class JKJavaResourceElement : JKTreeElement(), PsiOwner by PsiOwnerImpl()

internal class JKJavaResourceExpression(expression: JKExpression) : JKJavaResourceElement() {
    var expression by child(expression)
}

internal class JKJavaResourceDeclaration(declaration: JKLocalVariable) : JKJavaResourceElement() {
    var declaration by child(declaration)
}

internal interface JKErrorElement : JKElement {
    val psi: PsiElement?
    val reason: String?
}