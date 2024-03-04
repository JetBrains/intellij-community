// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.tree.visitors

import org.jetbrains.kotlin.nj2k.tree.*

abstract class JKVisitorWithCommentsPrinting : JKVisitor() {
    abstract fun printLeftNonCodeElements(element: JKFormattingOwner)
    abstract fun printRightNonCodeElements(element: JKFormattingOwner)
    override fun visitTreeElement(treeElement: JKElement) {
        if (treeElement is JKFormattingOwner) {
            printLeftNonCodeElements(treeElement)
        }
        visitTreeElementRaw(treeElement)
        if (treeElement is JKFormattingOwner) {
            printRightNonCodeElements(treeElement)
        }
    }

    abstract fun visitTreeElementRaw(treeElement: JKElement)
    override fun visitDeclaration(declaration: JKDeclaration) {
        printLeftNonCodeElements(declaration)
        visitTreeElementRaw(declaration)
        printRightNonCodeElements(declaration)
    }

    override fun visitVariable(variable: JKVariable) {
        printLeftNonCodeElements(variable)
        visitTreeElementRaw(variable)
        printRightNonCodeElements(variable)
    }

    override fun visitJavaStaticInitDeclaration(javaStaticInitDeclaration: JKJavaStaticInitDeclaration) {
        printLeftNonCodeElements(javaStaticInitDeclaration)
        visitTreeElementRaw(javaStaticInitDeclaration)
        printRightNonCodeElements(javaStaticInitDeclaration)
    }

    override fun visitLabel(label: JKLabel) {
        printLeftNonCodeElements(label)
        visitTreeElementRaw(label)
        printRightNonCodeElements(label)
    }

    override fun visitKtWhenLabel(ktWhenLabel: JKKtWhenLabel) {
        printLeftNonCodeElements(ktWhenLabel)
        visitTreeElementRaw(ktWhenLabel)
        printRightNonCodeElements(ktWhenLabel)
    }

    override fun visitJavaTryCatchSection(javaTryCatchSection: JKJavaTryCatchSection) {
        printLeftNonCodeElements(javaTryCatchSection)
        visitTreeElementRaw(javaTryCatchSection)
        printRightNonCodeElements(javaTryCatchSection)
    }

    override fun visitJavaSwitchCase(javaSwitchCase: JKJavaSwitchCase) {
        printLeftNonCodeElements(javaSwitchCase)
        visitTreeElementRaw(javaSwitchCase)
        printRightNonCodeElements(javaSwitchCase)
    }

    override fun visitJavaDefaultSwitchCase(javaDefaultSwitchCase: JKJavaDefaultSwitchCase) {
        printLeftNonCodeElements(javaDefaultSwitchCase)
        visitTreeElementRaw(javaDefaultSwitchCase)
        printRightNonCodeElements(javaDefaultSwitchCase)
    }

    override fun visitJavaLabelSwitchCase(javaLabelSwitchCase: JKJavaLabelSwitchCase) {
        printLeftNonCodeElements(javaLabelSwitchCase)
        visitTreeElementRaw(javaLabelSwitchCase)
        printRightNonCodeElements(javaLabelSwitchCase)
    }

    override fun visitExpression(expression: JKExpression) {
        printLeftNonCodeElements(expression)
        visitTreeElementRaw(expression)
        printRightNonCodeElements(expression)
    }

    override fun visitOperatorExpression(operatorExpression: JKOperatorExpression) {
        printLeftNonCodeElements(operatorExpression)
        visitTreeElementRaw(operatorExpression)
        printRightNonCodeElements(operatorExpression)
    }

    override fun visitUnaryExpression(unaryExpression: JKUnaryExpression) {
        printLeftNonCodeElements(unaryExpression)
        visitTreeElementRaw(unaryExpression)
        printRightNonCodeElements(unaryExpression)
    }

    override fun visitKtAssignmentChainLink(ktAssignmentChainLink: JKKtAssignmentChainLink) {
        printLeftNonCodeElements(ktAssignmentChainLink)
        visitTreeElementRaw(ktAssignmentChainLink)
        printRightNonCodeElements(ktAssignmentChainLink)
    }

    override fun visitJavaNewEmptyArray(javaNewEmptyArray: JKJavaNewEmptyArray) {
        printLeftNonCodeElements(javaNewEmptyArray)
        visitTreeElementRaw(javaNewEmptyArray)
        printRightNonCodeElements(javaNewEmptyArray)
    }

    override fun visitJavaNewArray(javaNewArray: JKJavaNewArray) {
        printLeftNonCodeElements(javaNewArray)
        visitTreeElementRaw(javaNewArray)
        printRightNonCodeElements(javaNewArray)
    }

    override fun visitJavaAssignmentExpression(javaAssignmentExpression: JKJavaAssignmentExpression) {
        printLeftNonCodeElements(javaAssignmentExpression)
        visitTreeElementRaw(javaAssignmentExpression)
        printRightNonCodeElements(javaAssignmentExpression)
    }

    override fun visitStatement(statement: JKStatement) {
        printLeftNonCodeElements(statement)
        visitTreeElementRaw(statement)
        printRightNonCodeElements(statement)
    }

    override fun visitLoopStatement(loopStatement: JKLoopStatement) {
        printLeftNonCodeElements(loopStatement)
        visitTreeElementRaw(loopStatement)
        printRightNonCodeElements(loopStatement)
    }

    override fun visitJavaSwitchStatement(javaSwitchStatement: JKJavaSwitchStatement) {
        printLeftNonCodeElements(javaSwitchStatement)
        visitTreeElementRaw(javaSwitchStatement)
        printRightNonCodeElements(javaSwitchStatement)
    }

    override fun visitJavaTryStatement(javaTryStatement: JKJavaTryStatement) {
        printLeftNonCodeElements(javaTryStatement)
        visitTreeElementRaw(javaTryStatement)
        printRightNonCodeElements(javaTryStatement)
    }

    override fun visitJavaSynchronizedStatement(javaSynchronizedStatement: JKJavaSynchronizedStatement) {
        printLeftNonCodeElements(javaSynchronizedStatement)
        visitTreeElementRaw(javaSynchronizedStatement)
        printRightNonCodeElements(javaSynchronizedStatement)
    }

    override fun visitJavaAssertStatement(javaAssertStatement: JKJavaAssertStatement) {
        printLeftNonCodeElements(javaAssertStatement)
        visitTreeElementRaw(javaAssertStatement)
        printRightNonCodeElements(javaAssertStatement)
    }

    override fun visitJavaForLoopStatement(javaForLoopStatement: JKJavaForLoopStatement) {
        printLeftNonCodeElements(javaForLoopStatement)
        visitTreeElementRaw(javaForLoopStatement)
        printRightNonCodeElements(javaForLoopStatement)
    }
}
