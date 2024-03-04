// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.nj2k.printing

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.printing.JKPrinterBase.ParenthesisKind
import org.jetbrains.kotlin.nj2k.symbols.getDisplayFqName
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.Visibility.PUBLIC
import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitor
import org.jetbrains.kotlin.nj2k.types.JKContextType
import org.jetbrains.kotlin.nj2k.types.isAnnotationMethod
import org.jetbrains.kotlin.nj2k.types.isInterface
import org.jetbrains.kotlin.nj2k.types.isUnit
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JKCodeBuilder(context: NewJ2kConverterContext) {
    private val elementInfoStorage = context.elementsInfoStorage
    private val printer = JKPrinter(context.project, context.importStorage, elementInfoStorage)
    private val commentPrinter = JKCommentPrinter(printer)

    fun printCodeOut(root: JKTreeElement): String {
        Visitor().also { root.accept(it) }
        return printer.toString().replace("\r\n", "\n")
    }

    private inner class Visitor : JKVisitor() {
        fun printLeftNonCodeElements(element: JKFormattingOwner) {
            commentPrinter.printCommentsAndLineBreaksBefore(element)
        }

        fun printRightNonCodeElements(element: JKFormattingOwner) {
            commentPrinter.printCommentsAndLineBreaksAfter(element)
        }

        private fun renderTokenElement(tokenElement: JKTokenElement) {
            printLeftNonCodeElements(tokenElement)
            printer.print(tokenElement.text)
            printRightNonCodeElements(tokenElement)
        }

        private fun renderExtraTypeParametersUpperBounds(typeParameterList: JKTypeParameterList) {
            val extraTypeBounds = typeParameterList.typeParameters
                .filter { it.upperBounds.size > 1 }
            if (extraTypeBounds.isNotEmpty()) {
                printer.printWithSurroundingSpaces("where")
                val typeParametersWithBounds =
                    extraTypeBounds.flatMap { typeParameter ->
                        typeParameter.upperBounds.map { bound ->
                            typeParameter.name to bound
                        }
                    }
                printer.renderList(typeParametersWithBounds) { (name, bound) ->
                    name.accept(this)
                    printer.printWithSurroundingSpaces(":")
                    bound.accept(this)
                }
            }
        }

        private fun renderModifiersList(modifiersListOwner: JKModifiersListOwner) {
            modifiersListOwner.forEachModifier { modifierElement ->
                if (modifierElement.isRedundant()) {
                    printLeftNonCodeElements(modifierElement)
                    printRightNonCodeElements(modifierElement)
                } else {
                    modifierElement.accept(this)
                    printer.print(" ")
                }
            }
        }

        override fun visitTreeElement(treeElement: JKElement) {
            printer.print("/* !!! Hit visitElement for element type: ${treeElement::class} !!! */")
        }

        override fun visitModifierElement(modifierElement: JKModifierElement) {
            printLeftNonCodeElements(modifierElement)
            visitModifierElementRaw(modifierElement)
            printRightNonCodeElements(modifierElement)
        }

        override fun visitMutabilityModifierElement(mutabilityModifierElement: JKMutabilityModifierElement) {
            printLeftNonCodeElements(mutabilityModifierElement)
            visitModifierElementRaw(mutabilityModifierElement)
            printRightNonCodeElements(mutabilityModifierElement)
        }

        override fun visitModalityModifierElement(modalityModifierElement: JKModalityModifierElement) {
            printLeftNonCodeElements(modalityModifierElement)
            visitModifierElementRaw(modalityModifierElement)
            printRightNonCodeElements(modalityModifierElement)
        }

        override fun visitVisibilityModifierElement(visibilityModifierElement: JKVisibilityModifierElement) {
            printLeftNonCodeElements(visibilityModifierElement)
            visitModifierElementRaw(visibilityModifierElement)
            printRightNonCodeElements(visibilityModifierElement)
        }

        override fun visitOtherModifierElement(otherModifierElement: JKOtherModifierElement) {
            printLeftNonCodeElements(otherModifierElement)
            visitModifierElementRaw(otherModifierElement)
            printRightNonCodeElements(otherModifierElement)
        }

        private fun visitModifierElementRaw(modifierElement: JKModifierElement) {
            if (modifierElement.modifier != FINAL) {
                printer.print(modifierElement.modifier.text)
            }
        }

        override fun visitTreeRoot(treeRoot: JKTreeRoot) {
            printLeftNonCodeElements(treeRoot)
            treeRoot.element.accept(this)
            printRightNonCodeElements(treeRoot)
        }

        override fun visitKtTryExpression(ktTryExpression: JKKtTryExpression) {
            printLeftNonCodeElements(ktTryExpression)
            printer.print("try ")
            ktTryExpression.tryBlock.accept(this)
            ktTryExpression.catchSections.forEach { it.accept(this) }
            if (ktTryExpression.finallyBlock != JKBodyStub) {
                printer.print("finally ")
                ktTryExpression.finallyBlock.accept(this)
            }
            printRightNonCodeElements(ktTryExpression)
        }

        override fun visitKtTryCatchSection(ktTryCatchSection: JKKtTryCatchSection) {
            printLeftNonCodeElements(ktTryCatchSection)
            printer.print("catch ")
            printer.par {
                ktTryCatchSection.parameter.accept(this)
            }
            ktTryCatchSection.block.accept(this)
            printRightNonCodeElements(ktTryCatchSection)
        }

        override fun visitForInStatement(forInStatement: JKForInStatement) {
            printLeftNonCodeElements(forInStatement)
            printer.print("for (")
            forInStatement.declaration.accept(this)
            printer.printWithSurroundingSpaces("in")
            forInStatement.iterationExpression.accept(this)
            printer.print(") ")
            if (forInStatement.body.isEmpty()) {
                printer.print(";")
            } else {
                forInStatement.body.accept(this)
            }
            printRightNonCodeElements(forInStatement)
        }

        override fun visitKtThrowExpression(ktThrowExpression: JKThrowExpression) {
            printLeftNonCodeElements(ktThrowExpression)
            printer.print("throw ")
            ktThrowExpression.exception.accept(this)
            printRightNonCodeElements(ktThrowExpression)
        }

        override fun visitDoWhileStatement(doWhileStatement: JKDoWhileStatement) {
            printLeftNonCodeElements(doWhileStatement)
            printer.print("do ")
            doWhileStatement.body.accept(this)
            printer.print(" ")
            printer.print("while (")
            doWhileStatement.condition.accept(this)
            printer.print(")")
            printRightNonCodeElements(doWhileStatement)
        }

        override fun visitClassAccessExpression(classAccessExpression: JKClassAccessExpression) {
            printLeftNonCodeElements(classAccessExpression)
            printer.renderSymbol(classAccessExpression.identifier, classAccessExpression)
            printRightNonCodeElements(classAccessExpression)
        }

        override fun visitMethodAccessExpression(methodAccessExpression: JKMethodAccessExpression) {
            printer.renderSymbol(methodAccessExpression.identifier, methodAccessExpression)
        }

        override fun visitTypeQualifierExpression(typeQualifierExpression: JKTypeQualifierExpression) {
            printer.renderType(typeQualifierExpression.type, typeQualifierExpression)
        }

        override fun visitFile(file: JKFile) {
            printLeftNonCodeElements(file)
            if (file.packageDeclaration.name.value.isNotEmpty()) {
                file.packageDeclaration.accept(this)
            }
            file.importList.accept(this)
            file.declarationList.forEach { it.accept(this) }
            printRightNonCodeElements(file)
        }

        override fun visitPackageDeclaration(packageDeclaration: JKPackageDeclaration) {
            printLeftNonCodeElements(packageDeclaration)
            printer.print("package ")
            val packageNameEscaped = packageDeclaration.name.value.escapedAsQualifiedName()
            printer.print(packageNameEscaped)
            if (!packageDeclaration.hasLineBreakAfter) printer.println()
            printRightNonCodeElements(packageDeclaration)
        }

        override fun visitImportList(importList: JKImportList) {
            printLeftNonCodeElements(importList)
            importList.imports.forEach { it.accept(this) }
            printRightNonCodeElements(importList)
        }

        override fun visitImportStatement(importStatement: JKImportStatement) {
            printLeftNonCodeElements(importStatement)
            printer.print("import ")
            val importNameEscaped =
                importStatement.name.value.escapedAsQualifiedName()
            printer.print(importNameEscaped)
            if (!importStatement.hasLineBreakAfter) printer.println()
            printRightNonCodeElements(importStatement)
        }

        override fun visitBreakStatement(breakStatement: JKBreakStatement) {
            printLeftNonCodeElements(breakStatement)
            printer.print("break")
            breakStatement.label.accept(this)
            printRightNonCodeElements(breakStatement)
        }

        override fun visitClass(klass: JKClass) {
            printLeftNonCodeElements(klass)
            visitClassRaw(klass)
            printRightNonCodeElements(klass)
        }

        private fun visitClassRaw(klass: JKClass) {
            klass.annotationList.accept(this)
            if (klass.hasAnnotations) {
                ensureLineBreak()
            }
            renderModifiersList(klass)
            printer.print(klass.classKind.text)
            printer.print(" ")
            klass.name.accept(this)
            klass.typeParameterList.accept(this)
            printer.print(" ")
            val primaryConstructor = klass.primaryConstructor()
            primaryConstructor?.accept(this)
            if (klass.inheritance.present()) {
                printer.printWithSurroundingSpaces(":")
                klass.inheritance.accept(this)
            }
            renderExtraTypeParametersUpperBounds(klass.typeParameterList)
            klass.classBody.accept(this)
        }

        override fun visitInheritanceInfo(inheritanceInfo: JKInheritanceInfo) {
            printLeftNonCodeElements(inheritanceInfo)
            visitInheritanceInfoRaw(inheritanceInfo)
            printRightNonCodeElements(inheritanceInfo)
        }

        private fun visitInheritanceInfoRaw(inheritanceInfo: JKInheritanceInfo) {
            val thisClass = inheritanceInfo.parentOfType<JKClass>()!!
            if (thisClass.classKind == INTERFACE) {
                renderTypes(inheritanceInfo.extends)
                return
            }
            inheritanceInfo.extends.singleOrNull()?.let { superTypeElement ->
                superTypeElement.accept(this)
                val primaryConstructor = thisClass.primaryConstructor()
                val delegationCall = primaryConstructor?.delegationCall.safeAs<JKDelegationConstructorCall>()
                if (delegationCall != null) {
                    printer.par { delegationCall.arguments.accept(this) }
                } else if (!superTypeElement.type.isInterface() && (primaryConstructor != null || thisClass.isObjectOrCompanionObject)) {
                    printer.print("()")
                }
                if (inheritanceInfo.implements.isNotEmpty()) printer.print(", ")
            }
            renderTypes(inheritanceInfo.implements)
        }

        private fun renderTypes(types: List<JKTypeElement>) {
            printer.renderList(types) {
                it.annotationList.accept(this)
                printer.renderType(it.type)
            }
        }

        override fun visitField(field: JKField) {
            printLeftNonCodeElements(field)
            visitFieldRaw(field)
            printRightNonCodeElements(field)
        }

        private fun visitFieldRaw(field: JKField) {
            field.annotationList.accept(this)
            if (field.hasAnnotations) {
                ensureLineBreak()
            }
            renderModifiersList(field)
            field.name.accept(this)
            if (field.type.present()) {
                printer.print(": ")
                field.type.accept(this)
            }
            if (field.initializer !is JKStubExpression) {
                printer.printWithSurroundingSpaces("=")
                field.initializer.accept(this)
            }
        }

        override fun visitEnumConstant(enumConstant: JKEnumConstant) {
            printLeftNonCodeElements(enumConstant)
            visitEnumConstantRaw(enumConstant)
            printRightNonCodeElements(enumConstant)
        }

        private fun visitEnumConstantRaw(enumConstant: JKEnumConstant) {
            enumConstant.annotationList.accept(this)
            enumConstant.name.accept(this)
            if (enumConstant.arguments.arguments.isNotEmpty()) {
                printer.par {
                    renderArgumentList(enumConstant.arguments)
                }
            }
            if (enumConstant.body.declarations.isNotEmpty()) {
                enumConstant.body.accept(this)
            }
        }

        override fun visitKtInitDeclaration(ktInitDeclaration: JKKtInitDeclaration) {
            printLeftNonCodeElements(ktInitDeclaration)
            if (ktInitDeclaration.block.statements.isNotEmpty()) {
                printer.print("init ")
                ktInitDeclaration.block.accept(this)
            }
            printRightNonCodeElements(ktInitDeclaration)
        }

        override fun visitIsExpression(isExpression: JKIsExpression) {
            printLeftNonCodeElements(isExpression)
            isExpression.expression.accept(this)
            printer.printWithSurroundingSpaces("is")
            isExpression.type.accept(this)
            printRightNonCodeElements(isExpression)
        }

        override fun visitParameter(parameter: JKParameter) {
            printLeftNonCodeElements(parameter)
            visitParameterRaw(parameter)
            printRightNonCodeElements(parameter)
        }

        private fun visitParameterRaw(parameter: JKParameter) {
            renderModifiersList(parameter)
            parameter.annotationList.accept(this)
            if (parameter.isVarArgs) {
                printer.print("vararg")
                printer.print(" ")
            }
            if (parameter.parent is JKKtPrimaryConstructor
                && (parameter.parent?.parent?.parent as? JKClass)?.classKind == ANNOTATION
            ) {
                printer.printWithSurroundingSpaces("val")
            }
            parameter.name.accept(this)
            if (parameter.type.present() && parameter.type.type !is JKContextType) {
                printer.print(": ")
                parameter.type.accept(this)
            }
            if (parameter.initializer !is JKStubExpression) {
                printer.printWithSurroundingSpaces("=")
                parameter.initializer.accept(this)
            }
        }

        override fun visitKtAnnotationArrayInitializerExpression(ktAnnotationArrayInitializerExpression: JKKtAnnotationArrayInitializerExpression) {
            printLeftNonCodeElements(ktAnnotationArrayInitializerExpression)
            printer.print("[")
            printer.renderList(ktAnnotationArrayInitializerExpression.initializers) {
                it.accept(this)
            }
            printer.print("]")
            printRightNonCodeElements(ktAnnotationArrayInitializerExpression)
        }

        override fun visitForLoopVariable(forLoopVariable: JKForLoopVariable) {
            printLeftNonCodeElements(forLoopVariable)
            forLoopVariable.annotationList.accept(this)
            forLoopVariable.name.accept(this)
            if (forLoopVariable.type.present() && forLoopVariable.type.type !is JKContextType) {
                printer.print(": ")
                forLoopVariable.type.accept(this)
            }
            printRightNonCodeElements(forLoopVariable)
        }

        override fun visitMethod(method: JKMethod) {
            printLeftNonCodeElements(method)
            visitMethodRaw(method)
            printRightNonCodeElements(method)
        }

        override fun visitMethodImpl(methodImpl: JKMethodImpl) {
            printLeftNonCodeElements(methodImpl)
            visitMethodRaw(methodImpl)
            printRightNonCodeElements(methodImpl)
        }

        override fun visitJavaAnnotationMethod(javaAnnotationMethod: JKJavaAnnotationMethod) {
            printLeftNonCodeElements(javaAnnotationMethod)
            visitMethodRaw(javaAnnotationMethod)
            printRightNonCodeElements(javaAnnotationMethod)
        }

        private fun visitMethodRaw(method: JKMethod) {
            method.annotationList.accept(this)
            renderModifiersList(method)
            printer.printWithSurroundingSpaces("fun")
            if (method.typeParameterList.typeParameters.isNotEmpty()) {
                method.typeParameterList.accept(this)
            }
            printInferenceLabel(method)
            method.name.accept(this)
            renderParameterList(method)
            if (!method.returnType.type.isUnit()) {
                printer.print(": ")
                method.returnType.accept(this)
            }
            renderExtraTypeParametersUpperBounds(method.typeParameterList)
            method.block.accept(this)
        }

        override fun visitIfElseExpression(ifElseExpression: JKIfElseExpression) {
            printLeftNonCodeElements(ifElseExpression)
            printer.print("if (")
            ifElseExpression.condition.accept(this)
            printer.print(") ")
            ifElseExpression.thenBranch.accept(this)
            if (ifElseExpression.elseBranch !is JKStubExpression) {
                printer.printWithSurroundingSpaces("else")
                ifElseExpression.elseBranch.accept(this)
            }
            printRightNonCodeElements(ifElseExpression)
        }

        override fun visitIfElseStatement(ifElseStatement: JKIfElseStatement) {
            printLeftNonCodeElements(ifElseStatement)
            visitIfElseStatementRaw(ifElseStatement)
            printRightNonCodeElements(ifElseStatement)
        }

        private fun visitIfElseStatementRaw(ifElseStatement: JKIfElseStatement) {
            printer.print("if (")
            ifElseStatement.condition.accept(this)
            printer.print(") ")
            if (ifElseStatement.thenBranch.isEmpty()) {
                printer.print(";")
            } else {
                ifElseStatement.thenBranch.accept(this)
            }
            if (!ifElseStatement.elseBranch.isEmpty()) {
                printer.printWithSurroundingSpaces("else")
                ifElseStatement.elseBranch.accept(this)
            }
        }

        override fun visitBinaryExpression(binaryExpression: JKBinaryExpression) {
            printLeftNonCodeElements(binaryExpression)
            binaryExpression.left.accept(this)
            printer.print(" ")
            printer.print(binaryExpression.operator.token.text)
            printer.print(" ")
            binaryExpression.right.accept(this)
            printRightNonCodeElements(binaryExpression)
        }

        override fun visitTypeParameterList(typeParameterList: JKTypeParameterList) {
            printLeftNonCodeElements(typeParameterList)
            if (typeParameterList.typeParameters.isNotEmpty()) {
                printer.par(ParenthesisKind.ANGLE) {
                    printer.renderList(typeParameterList.typeParameters) {
                        it.accept(this)
                    }
                }
            }
            printRightNonCodeElements(typeParameterList)
        }

        override fun visitTypeParameter(typeParameter: JKTypeParameter) {
            printLeftNonCodeElements(typeParameter)
            typeParameter.annotationList.accept(this)
            typeParameter.name.accept(this)
            if (typeParameter.upperBounds.size == 1) {
                printer.printWithSurroundingSpaces(":")
                typeParameter.upperBounds.single().accept(this)
            }
            printRightNonCodeElements(typeParameter)
        }

        override fun visitLiteralExpression(literalExpression: JKLiteralExpression) {
            printLeftNonCodeElements(literalExpression)
            printer.print(literalExpression.literal)
            printRightNonCodeElements(literalExpression)
        }

        override fun visitPrefixExpression(prefixExpression: JKPrefixExpression) {
            printLeftNonCodeElements(prefixExpression)
            printer.print(prefixExpression.operator.token.text)
            prefixExpression.expression.accept(this)
            printRightNonCodeElements(prefixExpression)
        }

        override fun visitThisExpression(thisExpression: JKThisExpression) {
            printLeftNonCodeElements(thisExpression)
            if (thisExpression.shouldBePreserved) {
                printExplicitLabel(thisExpression)
            }
            printer.print("this")
            thisExpression.qualifierLabel.accept(this)
            printRightNonCodeElements(thisExpression)
        }

        override fun visitSuperExpression(superExpression: JKSuperExpression) {
            printLeftNonCodeElements(superExpression)
            printer.print("super")
            val numberOfDirectSupertypes = superExpression.parentOfType<JKClass>()?.inheritance?.supertypeCount() ?: 0
            if (superExpression.superTypeQualifier != null && numberOfDirectSupertypes > 1) {
                printer.par(ParenthesisKind.ANGLE) {
                    printer.renderSymbol(superExpression.superTypeQualifier, superExpression)
                }
            } else {
                superExpression.outerTypeQualifier.accept(this)
            }
            printRightNonCodeElements(superExpression)
        }

        override fun visitContinueStatement(continueStatement: JKContinueStatement) {
            printLeftNonCodeElements(continueStatement)
            printer.print("continue")
            continueStatement.label.accept(this)
            printer.print(" ")
            printRightNonCodeElements(continueStatement)
        }

        override fun visitLabelEmpty(labelEmpty: JKLabelEmpty) {
            printLeftNonCodeElements(labelEmpty)
            printRightNonCodeElements(labelEmpty)
        }

        override fun visitLabelText(labelText: JKLabelText) {
            printLeftNonCodeElements(labelText)
            printer.print("@")
            labelText.label.accept(this)
            printer.print(" ")
            printRightNonCodeElements(labelText)
        }

        override fun visitLabeledExpression(labeledExpression: JKLabeledExpression) {
            printLeftNonCodeElements(labeledExpression)
            for (label in labeledExpression.labels) {
                label.accept(this)
                printer.print("@")
            }
            labeledExpression.statement.accept(this)
            printRightNonCodeElements(labeledExpression)
        }

        override fun visitNameIdentifier(nameIdentifier: JKNameIdentifier) {
            printLeftNonCodeElements(nameIdentifier)
            printer.print(nameIdentifier.value.escaped())
            printRightNonCodeElements(nameIdentifier)
        }

        override fun visitPostfixExpression(postfixExpression: JKPostfixExpression) {
            printLeftNonCodeElements(postfixExpression)
            postfixExpression.expression.accept(this)
            printer.print(postfixExpression.operator.token.text)
            printRightNonCodeElements(postfixExpression)
        }

        override fun visitQualifiedExpression(qualifiedExpression: JKQualifiedExpression) {
            printLeftNonCodeElements(qualifiedExpression)
            qualifiedExpression.receiver.accept(this)
            printer.print(".")
            qualifiedExpression.selector.accept(this)
            printRightNonCodeElements(qualifiedExpression)
        }

        override fun visitArgumentList(argumentList: JKArgumentList) {
            printLeftNonCodeElements(argumentList)
            renderArgumentList(argumentList)
            printRightNonCodeElements(argumentList)
        }

        private fun renderArgumentList(argumentList: JKArgumentList) {
            for ((i, argument) in argumentList.arguments.withIndex()) {
                printLeftNonCodeElements(argument)
                if (argument is JKNamedArgument) {
                    visitNamedArgumentRaw(argument)
                } else {
                    argument.value.accept(this)
                }
                if (i < argumentList.arguments.lastIndex || argumentList.hasTrailingComma) {
                    printer.print(", ")
                }
                printRightNonCodeElements(argument)
            }
        }

        override fun visitArgument(argument: JKArgument) {
            printLeftNonCodeElements(argument)
            argument.value.accept(this)
            printRightNonCodeElements(argument)
        }

        override fun visitArgumentImpl(argumentImpl: JKArgumentImpl) {
            printLeftNonCodeElements(argumentImpl)
            argumentImpl.value.accept(this)
            printRightNonCodeElements(argumentImpl)
        }

        override fun visitNamedArgument(namedArgument: JKNamedArgument) {
            printLeftNonCodeElements(namedArgument)
            visitNamedArgumentRaw(namedArgument)
            printRightNonCodeElements(namedArgument)
        }

        private fun visitNamedArgumentRaw(namedArgument: JKNamedArgument) {
            namedArgument.name.accept(this)
            printer.printWithSurroundingSpaces("=")
            namedArgument.value.accept(this)
        }

        override fun visitCallExpression(callExpression: JKCallExpression) {
            printLeftNonCodeElements(callExpression)
            visitCallExpressionRaw(callExpression)
            printRightNonCodeElements(callExpression)
        }

        override fun visitCallExpressionImpl(callExpressionImpl: JKCallExpressionImpl) {
            printLeftNonCodeElements(callExpressionImpl)
            visitCallExpressionRaw(callExpressionImpl)
            printRightNonCodeElements(callExpressionImpl)
        }

        private fun visitCallExpressionRaw(callExpression: JKCallExpression) {
            printer.renderSymbol(callExpression.identifier, callExpression)
            if (callExpression.identifier.isAnnotationMethod()) return
            callExpression.typeArgumentList.accept(this)
            printer.par {
                callExpression.arguments.accept(this)
            }
        }

        override fun visitTypeArgumentList(typeArgumentList: JKTypeArgumentList) {
            printLeftNonCodeElements(typeArgumentList)
            if (typeArgumentList.typeArguments.isNotEmpty()) {
                printer.par(ParenthesisKind.ANGLE) {
                    printer.renderList(typeArgumentList.typeArguments) {
                        it.accept(this)
                    }
                }
            }
            printRightNonCodeElements(typeArgumentList)
        }

        override fun visitParenthesizedExpression(parenthesizedExpression: JKParenthesizedExpression) {
            printLeftNonCodeElements(parenthesizedExpression)
            if (parenthesizedExpression.shouldBePreserved) {
                printExplicitLabel(parenthesizedExpression)
            }
            printer.par {
                parenthesizedExpression.expression.accept(this)
            }
            printRightNonCodeElements(parenthesizedExpression)
        }

        override fun visitDeclarationStatement(declarationStatement: JKDeclarationStatement) {
            printLeftNonCodeElements(declarationStatement)
            printer.renderList(declarationStatement.declaredStatements, ::ensureLineBreak) {
                it.accept(this)
            }
            printRightNonCodeElements(declarationStatement)
        }

        override fun visitTypeCastExpression(typeCastExpression: JKTypeCastExpression) {
            printLeftNonCodeElements(typeCastExpression)
            typeCastExpression.expression.accept(this)
            printer.printWithSurroundingSpaces("as")
            typeCastExpression.type.accept(this)
            printRightNonCodeElements(typeCastExpression)
        }

        override fun visitWhileStatement(whileStatement: JKWhileStatement) {
            printLeftNonCodeElements(whileStatement)
            printer.print("while (")
            whileStatement.condition.accept(this)
            printer.print(") ")
            if (whileStatement.body.isEmpty()) {
                printer.print(";")
            } else {
                whileStatement.body.accept(this)
            }
            printRightNonCodeElements(whileStatement)
        }

        override fun visitLocalVariable(localVariable: JKLocalVariable) {
            printLeftNonCodeElements(localVariable)
            printer.print(" ")
            localVariable.annotationList.accept(this)
            renderModifiersList(localVariable)
            localVariable.name.accept(this)
            if (localVariable.type.present() && localVariable.type.type != JKContextType) {
                printer.print(": ")
                localVariable.type.accept(this)
            }
            if (localVariable.initializer !is JKStubExpression) {
                printer.printWithSurroundingSpaces("=")
                localVariable.initializer.accept(this)
            }
            printRightNonCodeElements(localVariable)
        }

        override fun visitEmptyStatement(emptyStatement: JKEmptyStatement) {
            printLeftNonCodeElements(emptyStatement)
            printRightNonCodeElements(emptyStatement)
        }

        override fun visitStubExpression(stubExpression: JKStubExpression) {
            printLeftNonCodeElements(stubExpression)
            printRightNonCodeElements(stubExpression)
        }

        override fun visitKtConvertedFromForLoopSyntheticWhileStatement(ktConvertedFromForLoopSyntheticWhileStatement: JKKtConvertedFromForLoopSyntheticWhileStatement) {
            printLeftNonCodeElements(ktConvertedFromForLoopSyntheticWhileStatement)
            printer.renderList(ktConvertedFromForLoopSyntheticWhileStatement.variableDeclarations, ::ensureLineBreak) {
                it.accept(this)
            }
            ensureLineBreak()
            ktConvertedFromForLoopSyntheticWhileStatement.whileStatement.accept(this)
            printRightNonCodeElements(ktConvertedFromForLoopSyntheticWhileStatement)
        }

        override fun visitNewExpression(newExpression: JKNewExpression) {
            printLeftNonCodeElements(newExpression)
            if (newExpression.isAnonymousClass) {
                printer.print("object : ")
            }
            printer.renderSymbol(newExpression.classSymbol, newExpression)
            newExpression.typeArgumentList.accept(this)
            if (!newExpression.classSymbol.isInterface() || newExpression.arguments.arguments.isNotEmpty()) {
                printer.par(ParenthesisKind.ROUND) {
                    newExpression.arguments.accept(this)
                }
            }
            if (newExpression.isAnonymousClass) {
                newExpression.classBody.accept(this)
            }
            printRightNonCodeElements(newExpression)
        }

        override fun visitKtItExpression(ktItExpression: JKKtItExpression) {
            printLeftNonCodeElements(ktItExpression)
            printer.print(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)
            printRightNonCodeElements(ktItExpression)
        }

        override fun visitClassBody(classBody: JKClassBody) {
            printLeftNonCodeElements(classBody)
            val declarations = classBody.declarations.filterNot { it is JKKtPrimaryConstructor }
            val isAnonymousClass = (classBody.parent as? JKNewExpression)?.isAnonymousClass == true
            if (declarations.isEmpty() && !isAnonymousClass) return
            printer.print(" ")
            renderTokenElement(classBody.leftBrace)
            if (declarations.isNotEmpty()) {
                ensureLineBreak()
                val containingClass = classBody.parent as? JKClass
                if (containingClass?.classKind == ENUM) {
                    renderEnumDeclarations(declarations, containingClass.hasTrailingComma)
                } else {
                    renderDeclarations(declarations)
                }
            }
            renderTokenElement(classBody.rightBrace)
            printRightNonCodeElements(classBody)
        }

        private fun renderDeclarations(declarations: List<JKDeclaration>) {
            printer.indented {
                printer.renderList(declarations, ::ensureLineBreak) {
                    it.accept(this)
                }
            }
        }

        private fun renderEnumDeclarations(declarations: List<JKDeclaration>, hasTrailingComma: Boolean) {
            printer.indented {
                val enumConstants = declarations.filterIsInstance<JKEnumConstant>()
                val otherDeclarations = declarations.filterNot { it is JKEnumConstant }
                for ((i, enumConstant) in enumConstants.withIndex()) {
                    printLeftNonCodeElements(enumConstant)
                    visitEnumConstantRaw(enumConstant)
                    if (i < enumConstants.lastIndex || hasTrailingComma) {
                        printer.print(", ")
                    }
                    if (i == enumConstants.lastIndex && otherDeclarations.isNotEmpty()) {
                        if (hasTrailingComma) ensureLineBreak()
                        printer.print(";")
                    }
                    printRightNonCodeElements(enumConstant)
                }
                ensureLineBreak()
                if (enumConstants.isEmpty() && otherDeclarations.isNotEmpty()) {
                    // Special case: Kotlin parser requires the semicolon in a non-empty Enum
                    printer.print(";")
                    printer.println()
                }
                printer.renderList(otherDeclarations, ::ensureLineBreak) {
                    it.accept(this)
                }
            }
        }

        override fun visitTypeElement(typeElement: JKTypeElement) {
            printLeftNonCodeElements(typeElement)
            typeElement.annotationList.accept(this)
            printer.renderType(typeElement.type, typeElement)
            printRightNonCodeElements(typeElement)
        }

        override fun visitBlock(block: JKBlock) {
            printLeftNonCodeElements(block)
            visitBlockRaw(block)
            printRightNonCodeElements(block)
        }

        override fun visitBlockImpl(blockImpl: JKBlockImpl) {
            printLeftNonCodeElements(blockImpl)
            visitBlockRaw(blockImpl)
            printRightNonCodeElements(blockImpl)
        }

        private fun visitBlockRaw(block: JKBlock) {
            printer.print(" ")
            renderTokenElement(block.leftBrace)
            if (block.statements.isNotEmpty()) {
                ensureLineBreak()
                printer.indented {
                    printer.renderList(block.statements, ::ensureLineBreak) {
                        it.accept(this)
                    }
                }
            }
            renderTokenElement(block.rightBrace)
        }

        private fun ensureLineBreak() {
            // Make sure that statements are separated by at least one line break,
            // but if the previous statement already has a line break, don't add another one.
            if (!printer.lastSymbolIsLineBreak) printer.println()
        }

        override fun visitBlockStatementWithoutBrackets(blockStatementWithoutBrackets: JKBlockStatementWithoutBrackets) {
            printLeftNonCodeElements(blockStatementWithoutBrackets)
            printer.renderList(blockStatementWithoutBrackets.statements, ::ensureLineBreak) {
                it.accept(this)
            }
            printRightNonCodeElements(blockStatementWithoutBrackets)
        }

        override fun visitExpressionStatement(expressionStatement: JKExpressionStatement) {
            printLeftNonCodeElements(expressionStatement)
            expressionStatement.expression.accept(this)
            printRightNonCodeElements(expressionStatement)
        }

        override fun visitReturnStatement(returnStatement: JKReturnStatement) {
            printLeftNonCodeElements(returnStatement)
            printer.print("return")
            returnStatement.label.accept(this)
            printer.print(" ")
            returnStatement.expression.accept(this)
            printRightNonCodeElements(returnStatement)
        }

        override fun visitFieldAccessExpression(fieldAccessExpression: JKFieldAccessExpression) {
            printLeftNonCodeElements(fieldAccessExpression)
            printer.renderSymbol(fieldAccessExpression.identifier, fieldAccessExpression)
            printRightNonCodeElements(fieldAccessExpression)
        }

        override fun visitPackageAccessExpression(packageAccessExpression: JKPackageAccessExpression) {
            printLeftNonCodeElements(packageAccessExpression)
            printer.renderSymbol(packageAccessExpression.identifier, packageAccessExpression)
            printRightNonCodeElements(packageAccessExpression)
        }

        override fun visitMethodReferenceExpression(methodReferenceExpression: JKMethodReferenceExpression) {
            printLeftNonCodeElements(methodReferenceExpression)
            methodReferenceExpression.qualifier.accept(this)
            printer.print("::")
            val needFqName = methodReferenceExpression.qualifier is JKStubExpression
            val displayName =
                if (needFqName) methodReferenceExpression.identifier.getDisplayFqName()
                else methodReferenceExpression.identifier.name
            printer.print(displayName.escapedAsQualifiedName())
            printRightNonCodeElements(methodReferenceExpression)
        }

        override fun visitDelegationConstructorCall(delegationConstructorCall: JKDelegationConstructorCall) {
            printLeftNonCodeElements(delegationConstructorCall)
            delegationConstructorCall.expression.accept(this)
            printer.par {
                delegationConstructorCall.arguments.accept(this)
            }
            printRightNonCodeElements(delegationConstructorCall)
        }

        private fun renderParameterList(method: JKMethod) {
            renderTokenElement(method.leftParen)
            for ((i, parameter) in method.parameters.withIndex()) {
                printLeftNonCodeElements(parameter)
                visitParameterRaw(parameter)
                if (i < method.parameters.lastIndex) printer.print(", ")
                printRightNonCodeElements(parameter)
            }
            renderTokenElement(method.rightParen)
        }

        override fun visitConstructor(constructor: JKConstructor) {
            printLeftNonCodeElements(constructor)
            visitConstructorRaw(constructor)
            printRightNonCodeElements(constructor)
        }

        override fun visitConstructorImpl(constructorImpl: JKConstructorImpl) {
            printLeftNonCodeElements(constructorImpl)
            visitConstructorRaw(constructorImpl)
            printRightNonCodeElements(constructorImpl)
        }

        private fun visitConstructorRaw(constructor: JKConstructor) {
            constructor.annotationList.accept(this)
            if (constructor.hasAnnotations) ensureLineBreak()
            renderModifiersList(constructor)
            printer.print("constructor")
            renderParameterList(constructor)
            if (constructor.delegationCall !is JKStubExpression) {
                printer.printWithSurroundingSpaces(":")
                constructor.delegationCall.accept(this)
            }
            if (constructor.block.statements.isNotEmpty()) {
                constructor.block.accept(this)
            }
        }

        override fun visitKtPrimaryConstructor(ktPrimaryConstructor: JKKtPrimaryConstructor) {
            printLeftNonCodeElements(ktPrimaryConstructor)
            ktPrimaryConstructor.annotationList.accept(this)
            renderModifiersList(ktPrimaryConstructor)
            val needConstructorKeyword = ktPrimaryConstructor.hasAnnotations || ktPrimaryConstructor.visibility != PUBLIC
            if (needConstructorKeyword) {
                printer.print("constructor")
            }
            val hasSecondaryConstructors =
                ktPrimaryConstructor.parentOfType<JKClassBody>()?.declarations.orEmpty().filterIsInstance<JKConstructorImpl>().isNotEmpty()
            if (ktPrimaryConstructor.parameters.isNotEmpty() || needConstructorKeyword || hasSecondaryConstructors) {
                // Print explicit primary constructor `()` with parameters, if any
                renderParameterList(ktPrimaryConstructor)
            }
            printRightNonCodeElements(ktPrimaryConstructor)
        }

        override fun visitLambdaExpression(lambdaExpression: JKLambdaExpression) {
            printLeftNonCodeElements(lambdaExpression)
            visitLambdaExpressionRaw(lambdaExpression)
            printRightNonCodeElements(lambdaExpression)
        }

        private fun visitLambdaExpressionRaw(lambdaExpression: JKLambdaExpression) {
            fun printLambda() {
                printer.par(ParenthesisKind.CURVED) {
                    val isMultiStatement = lambdaExpression.statement.statements.size > 1
                    if (isMultiStatement) printer.println()
                    printer.renderList(lambdaExpression.parameters) { it.accept(this) }
                    if (lambdaExpression.parameters.isNotEmpty()) {
                        printer.printWithSurroundingSpaces("->")
                    }
                    val statement = lambdaExpression.statement
                    if (statement is JKBlockStatement) {
                        printer.renderList(statement.block.statements, ::ensureLineBreak) { it.accept(this) }
                    } else {
                        statement.accept(this)
                    }
                    if (isMultiStatement) printer.println()
                }
            }
            if (lambdaExpression.functionalType.present()) {
                printer.renderType(lambdaExpression.functionalType.type, lambdaExpression)
                printer.print(" ")
                printer.par(ParenthesisKind.ROUND, ::printLambda)
            } else {
                printLambda()
            }
        }

        override fun visitBlockStatement(blockStatement: JKBlockStatement) {
            printLeftNonCodeElements(blockStatement)
            blockStatement.block.accept(this)
            printRightNonCodeElements(blockStatement)
        }

        override fun visitKtAssignmentStatement(ktAssignmentStatement: JKKtAssignmentStatement) {
            printLeftNonCodeElements(ktAssignmentStatement)
            ktAssignmentStatement.field.accept(this)
            printer.print(" ")
            printer.print(ktAssignmentStatement.token.text)
            printer.print(" ")
            ktAssignmentStatement.expression.accept(this)
            printRightNonCodeElements(ktAssignmentStatement)
        }

        override fun visitAssignmentChainAlsoLink(assignmentChainAlsoLink: JKAssignmentChainAlsoLink) {
            printLeftNonCodeElements(assignmentChainAlsoLink)
            assignmentChainAlsoLink.receiver.accept(this)
            printer.print(".also({ ")
            assignmentChainAlsoLink.assignmentStatement.accept(this)
            printer.print(" ")
            printer.print("})")
            printRightNonCodeElements(assignmentChainAlsoLink)
        }

        override fun visitAssignmentChainLetLink(assignmentChainLetLink: JKAssignmentChainLetLink) {
            printLeftNonCodeElements(assignmentChainLetLink)
            assignmentChainLetLink.receiver.accept(this)
            printer.print(".let({ ")
            assignmentChainLetLink.assignmentStatement.accept(this)
            printer.print("; ")
            assignmentChainLetLink.field.accept(this)
            printer.print(" ")
            printer.print("})")
            printRightNonCodeElements(assignmentChainLetLink)
        }

        override fun visitKtWhenBlock(ktWhenBlock: JKKtWhenBlock) {
            printLeftNonCodeElements(ktWhenBlock)
            visitKtWhenBlockRaw(ktWhenBlock)
            printRightNonCodeElements(ktWhenBlock)
        }

        private fun visitKtWhenBlockRaw(ktWhenBlock: JKKtWhenBlock) {
            printer.print("when (")
            ktWhenBlock.expression.accept(this)
            printer.print(")")
            printer.block {
                printer.renderList(ktWhenBlock.cases, ::ensureLineBreak) {
                    it.accept(this)
                }
            }
        }

        override fun visitKtWhenExpression(ktWhenExpression: JKKtWhenExpression) {
            visitKtWhenBlockRaw(ktWhenExpression)
        }

        override fun visitKtWhenStatement(ktWhenStatement: JKKtWhenStatement) {
            visitKtWhenBlockRaw(ktWhenStatement)
        }

        override fun visitAnnotationList(annotationList: JKAnnotationList) {
            printLeftNonCodeElements(annotationList)
            printer.renderList(annotationList.annotations, " ") {
                it.accept(this)
            }
            if (annotationList.annotations.isNotEmpty() && !printer.lastSymbolIsLineBreak) {
                printer.print(" ")
            }
            printRightNonCodeElements(annotationList)
        }

        override fun visitAnnotation(annotation: JKAnnotation) {
            printLeftNonCodeElements(annotation)
            printer.print("@")
            annotation.useSiteTarget?.let { printer.print("${it.renderName}:") }
            printer.renderSymbol(annotation.classSymbol, annotation)
            if (annotation.arguments.isNotEmpty()) {
                printer.par {
                    printer.renderList(annotation.arguments) { it.accept(this) }
                }
            }
            printRightNonCodeElements(annotation)
        }

        override fun visitAnnotationNameParameter(annotationNameParameter: JKAnnotationNameParameter) {
            printLeftNonCodeElements(annotationNameParameter)
            annotationNameParameter.name.accept(this)
            printer.printWithSurroundingSpaces("=")
            annotationNameParameter.value.accept(this)
            printRightNonCodeElements(annotationNameParameter)
        }

        override fun visitAnnotationParameter(annotationParameter: JKAnnotationParameter) {
            printLeftNonCodeElements(annotationParameter)
            annotationParameter.value.accept(this)
            printRightNonCodeElements(annotationParameter)
        }

        override fun visitAnnotationParameterImpl(annotationParameterImpl: JKAnnotationParameterImpl) {
            printLeftNonCodeElements(annotationParameterImpl)
            annotationParameterImpl.value.accept(this)
            printRightNonCodeElements(annotationParameterImpl)
        }

        override fun visitClassLiteralExpression(classLiteralExpression: JKClassLiteralExpression) {
            printLeftNonCodeElements(classLiteralExpression)
            visitClassLiteralExpressionRaw(classLiteralExpression)
            printRightNonCodeElements(classLiteralExpression)
        }

        private fun visitClassLiteralExpressionRaw(classLiteralExpression: JKClassLiteralExpression) {
            if (classLiteralExpression.literalType == JKClassLiteralExpression.ClassLiteralType.JAVA_VOID_TYPE) {
                printer.print("Void.TYPE")
            } else {
                printer.renderType(classLiteralExpression.classType.type, classLiteralExpression)
                printer.print("::")
                when (classLiteralExpression.literalType) {
                    JKClassLiteralExpression.ClassLiteralType.KOTLIN_CLASS -> printer.print("class")
                    JKClassLiteralExpression.ClassLiteralType.JAVA_CLASS -> printer.print("class.java")
                    JKClassLiteralExpression.ClassLiteralType.JAVA_PRIMITIVE_CLASS -> printer.print("class.javaPrimitiveType")
                    JKClassLiteralExpression.ClassLiteralType.JAVA_VOID_TYPE -> Unit
                }
            }
        }

        override fun visitKtWhenCase(ktWhenCase: JKKtWhenCase) {
            printLeftNonCodeElements(ktWhenCase)
            printer.renderList(ktWhenCase.labels) {
                it.accept(this)
            }
            printer.printWithSurroundingSpaces("->")
            ktWhenCase.statement.accept(this)
            printRightNonCodeElements(ktWhenCase)
        }

        override fun visitKtElseWhenLabel(ktElseWhenLabel: JKKtElseWhenLabel) {
            printLeftNonCodeElements(ktElseWhenLabel)
            printer.print("else")
            printRightNonCodeElements(ktElseWhenLabel)
        }

        override fun visitKtValueWhenLabel(ktValueWhenLabel: JKKtValueWhenLabel) {
            printLeftNonCodeElements(ktValueWhenLabel)
            ktValueWhenLabel.expression.accept(this)
            printRightNonCodeElements(ktValueWhenLabel)
        }

        override fun visitErrorExpression(errorExpression: JKErrorExpression) {
            visitErrorElement(errorExpression)
        }

        override fun visitErrorStatement(errorStatement: JKErrorStatement) {
            visitErrorElement(errorStatement)
        }

        private fun visitErrorElement(errorElement: JKErrorElement) {
            val message = buildString {
                append("Cannot convert element")
                errorElement.reason?.let { append(": $it") }
                val elementText = errorElement.psi?.text
                if (!elementText.isNullOrBlank()) {
                    append("\nWith text:\n$elementText")
                }
            }.replace("$", "\\$")
            if (message.contains("\n")) {
                printer.print("TODO(")
                printer.indented {
                    printer.print("\"\"\"")
                    printer.println()
                    message.split('\n').forEach { line ->
                        printer.print("|")
                        printer.print(line)
                        printer.println()
                    }
                    printer.print("\"\"\".trimMargin()")
                }
                printer.print(")")
            } else {
                printer.print("TODO(\"$message\")")
            }
        }

        private fun printExplicitLabel(element: JKElement) {
            // TODO: Currently disabled for K2 J2K, but may have to be enabled
            // if we don't figure out how to accurately preserve original `this` expressions
            // and parentheses without a post-processing
            if (KotlinPluginModeProvider.isK2Mode()) return
            val label = elementInfoStorage.getOrCreateExplicitLabelForElement(element)
            printer.print(label.render())
        }

        private fun printInferenceLabel(element: JKElement) {
            if (KotlinPluginModeProvider.isK2Mode()) return
            val label = elementInfoStorage.getOrCreateInferenceLabelForElement(element)
            printer.print(label.render())
        }
    }
}
