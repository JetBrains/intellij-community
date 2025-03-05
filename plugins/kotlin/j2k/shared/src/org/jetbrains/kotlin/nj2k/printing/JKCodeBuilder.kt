// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.nj2k.printing

import com.intellij.psi.PsiReferenceExpression
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider.Companion.isK1Mode
import org.jetbrains.kotlin.j2k.ConverterContext
import org.jetbrains.kotlin.nj2k.*
import org.jetbrains.kotlin.nj2k.printing.JKPrinterBase.ParenthesisKind
import org.jetbrains.kotlin.nj2k.symbols.getDisplayFqName
import org.jetbrains.kotlin.nj2k.tree.*
import org.jetbrains.kotlin.nj2k.tree.JKClass.ClassKind.*
import org.jetbrains.kotlin.nj2k.tree.Modality.FINAL
import org.jetbrains.kotlin.nj2k.tree.visitors.JKVisitorWithCommentsPrinting
import org.jetbrains.kotlin.nj2k.types.JKContextType
import org.jetbrains.kotlin.nj2k.types.isAnnotationMethod
import org.jetbrains.kotlin.nj2k.types.isInterface
import org.jetbrains.kotlin.nj2k.types.isUnit
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class JKCodeBuilder(private val context: ConverterContext) {
    private val elementInfoStorage = context.elementsInfoStorage
    private val printer = JKPrinter(context.project, context.importStorage, elementInfoStorage)
    private val commentPrinter = JKCommentPrinter(printer)
    private val settings = context.converter.settings

    fun printCodeOut(root: JKTreeElement): String {
        Visitor().also { root.accept(it) }
        return printer.toString().replace("\r\n", "\n")
    }

    private inner class Visitor : JKVisitorWithCommentsPrinting() {
        override fun printLeftNonCodeElements(element: JKFormattingOwner) {
            commentPrinter.printCommentsAndLineBreaksBefore(element)
        }

        override fun printRightNonCodeElements(element: JKFormattingOwner) {
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

        private fun renderModifiersList(modifiersListOwner: JKModifiersListOwner): Boolean {
            var hasRenderedModifiers = false
            modifiersListOwner.forEachModifier { modifierElement ->
                if (modifierElement.isRedundant(context.languageVersionSettings)) {
                    printLeftNonCodeElements(modifierElement)
                    printRightNonCodeElements(modifierElement)
                } else {
                    hasRenderedModifiers = true
                    modifierElement.accept(this)
                    printer.print(" ")
                }
            }
            return hasRenderedModifiers
        }

        override fun visitTreeElementRaw(treeElement: JKElement) {
            printer.print("/* !!! Hit visitElement for element type: ${treeElement::class} !!! */")
        }

        override fun visitModifierElementRaw(modifierElement: JKModifierElement) {
            if (modifierElement.modifier != FINAL) {
                printer.print(modifierElement.modifier.text)
            }
        }

        override fun visitTreeRootRaw(treeRoot: JKTreeRoot) {
            treeRoot.element.accept(this)
        }

        override fun visitKtTryExpressionRaw(ktTryExpression: JKKtTryExpression) {
            printer.print("try ")
            ktTryExpression.tryBlock.accept(this)
            ktTryExpression.catchSections.forEach { it.accept(this) }
            if (ktTryExpression.finallyBlock != JKBodyStub) {
                printer.print("finally ")
                ktTryExpression.finallyBlock.accept(this)
            }
        }

        override fun visitKtTryCatchSectionRaw(ktTryCatchSection: JKKtTryCatchSection) {
            printer.print("catch ")
            printer.par {
                ktTryCatchSection.parameter.accept(this)
            }
            ktTryCatchSection.block.accept(this)
        }

        override fun visitForInStatementRaw(forInStatement: JKForInStatement) {
            printer.print("for (")
            forInStatement.parameter.accept(this)
            printer.printWithSurroundingSpaces("in")
            forInStatement.iterationExpression.accept(this)
            printer.print(") ")
            if (forInStatement.body.isEmpty()) {
                printer.print(";")
            } else {
                forInStatement.body.accept(this)
            }
        }

        override fun visitKtThrowExpressionRaw(ktThrowExpression: JKThrowExpression) {
            printer.print("throw ")
            ktThrowExpression.exception.accept(this)
        }

        override fun visitDoWhileStatementRaw(doWhileStatement: JKDoWhileStatement) {
            printer.print("do ")
            doWhileStatement.body.accept(this)
            printer.print(" ")
            printer.print("while (")
            doWhileStatement.condition.accept(this)
            printer.print(")")
        }

        override fun visitClassAccessExpressionRaw(classAccessExpression: JKClassAccessExpression) {
            printer.renderSymbol(classAccessExpression.identifier, classAccessExpression)
        }

        override fun visitMethodAccessExpressionRaw(methodAccessExpression: JKMethodAccessExpression) {
            printer.renderSymbol(methodAccessExpression.identifier, methodAccessExpression)
        }

        override fun visitTypeQualifierExpressionRaw(typeQualifierExpression: JKTypeQualifierExpression) {
            printer.renderType(typeQualifierExpression.type, typeQualifierExpression)
        }

        override fun visitFileRaw(file: JKFile) {
            if (file.packageDeclaration.name.value.isNotEmpty()) {
                file.packageDeclaration.accept(this)
            }
            file.importList.accept(this)
            file.declarationList.forEach { it.accept(this) }
        }

        override fun visitPackageDeclarationRaw(packageDeclaration: JKPackageDeclaration) {
            printer.print("package ")
            val packageNameEscaped = packageDeclaration.name.value.escapedAsQualifiedName()
            printer.print(packageNameEscaped)
            if (!packageDeclaration.hasLineBreakAfter) printer.println()
        }

        override fun visitImportListRaw(importList: JKImportList) {
            importList.imports.forEach { it.accept(this) }
        }

        override fun visitImportStatementRaw(importStatement: JKImportStatement) {
            if (JKImportStorage.PLATFORM_CLASSES_MAPPED_TO_KOTLIN.any { it.matches(importStatement.name.value) }) {
                return
            }
            printer.print("import ")
            val importNameEscaped =
                importStatement.name.value.escapedAsQualifiedName()
            printer.print(importNameEscaped)
            if (!importStatement.hasLineBreakAfter) printer.println()
        }

        override fun visitBreakStatementRaw(breakStatement: JKBreakStatement) {
            printer.print("break")
            breakStatement.label.accept(this)
        }

        override fun visitClassRaw(klass: JKClass) {
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

            if (klass.inheritance.isPresent()) {
                printer.printWithSurroundingSpaces(":")
                klass.inheritance.accept(this)
            }

            renderExtraTypeParametersUpperBounds(klass.typeParameterList)

            klass.classBody.accept(this)
        }

        override fun visitInheritanceInfoRaw(inheritanceInfo: JKInheritanceInfo) {
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
                    delegationCall.arguments.accept(this)
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

        override fun visitFieldRaw(field: JKField) {
            field.annotationList.accept(this)
            if (field.hasAnnotations) {
                ensureLineBreak()
            }
            renderModifiersList(field)
            field.name.accept(this)
            if (field.type.isPresent()) {
                printer.print(": ")
                field.type.accept(this)
            }
            if (field.initializer !is JKStubExpression) {
                printer.printWithSurroundingSpaces("=")
                field.initializer.accept(this)
            }
        }

        override fun visitEnumConstantRaw(enumConstant: JKEnumConstant) {
            enumConstant.annotationList.accept(this)
            enumConstant.name.accept(this)
            if (enumConstant.arguments.arguments.isNotEmpty()) {
                enumConstant.arguments.accept(this)
            }
            if (enumConstant.body.declarations.isNotEmpty()) {
                enumConstant.body.accept(this)
            }
        }

        override fun visitKtInitDeclarationRaw(ktInitDeclaration: JKKtInitDeclaration) {
            if (ktInitDeclaration.block.statements.isNotEmpty()) {
                printer.print("init ")
                ktInitDeclaration.block.accept(this)
            }
        }

        override fun visitIsExpressionRaw(isExpression: JKIsExpression) {
            isExpression.expression.accept(this)
            printer.printWithSurroundingSpaces(if (isExpression.isNegated) "!is" else "is")
            isExpression.type.accept(this)
        }

        override fun visitParameterRaw(parameter: JKParameter) {
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

            renderVariableDeclarationNameAndType(parameter)
            if (parameter.initializer !is JKStubExpression) {
                printer.printWithSurroundingSpaces("=")
                parameter.initializer.accept(this)
            }
        }

        override fun visitDestructuringDeclarationRaw(destructuringDeclaration: JKKtDestructuringDeclaration) {
            printer.par {
                printer.renderList(destructuringDeclaration.entries) { it.accept(this) }
            }
        }

        override fun visitDestructuringDeclarationEntryRaw(destructuringDeclarationEntry: JKKtDestructuringDeclarationEntry) {
            renderVariableDeclarationNameAndType(destructuringDeclarationEntry)
        }

        private fun renderVariableDeclarationNameAndType(variable: JKVariable) {
            variable.name.accept(this)
            if (variable.type.isPresent() && variable.type.type !is JKContextType) {
                printer.print(": ")
                variable.type.accept(this)
            }
        }

        override fun visitKtAnnotationArrayInitializerExpressionRaw(
            ktAnnotationArrayInitializerExpression: JKKtAnnotationArrayInitializerExpression
        ) {
            printer.print("[")
            printer.renderList(ktAnnotationArrayInitializerExpression.initializers) {
                it.accept(this)
            }
            printer.print("]")
        }

        override fun visitForLoopParameterRaw(forLoopParameter: JKForLoopParameter) {
            forLoopParameter.annotationList.accept(this)
            forLoopParameter.name.accept(this)
            if (!forLoopParameter.type.isPresent() || forLoopParameter.type.type is JKContextType) return

            val needExplicitType = isK1Mode() || // for K1 nullability inference
                    settings.specifyLocalVariableTypeByDefault ||
                    forLoopParameter.type.annotationList.annotations.isNotEmpty()

            if (needExplicitType) {
                printer.print(": ")
                forLoopParameter.type.accept(this)
            }
        }

        override fun visitMethodRaw(method: JKMethod) {
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

        override fun visitIfElseExpressionRaw(ifElseExpression: JKIfElseExpression) {
            printer.print("if (")
            ifElseExpression.condition.accept(this)
            printer.print(") ")
            ifElseExpression.thenBranch.accept(this)
            if (ifElseExpression.elseBranch !is JKStubExpression) {
                printer.printWithSurroundingSpaces("else")
                ifElseExpression.elseBranch.accept(this)
            }
        }


        override fun visitIfElseStatementRaw(ifElseStatement: JKIfElseStatement) {
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


        override fun visitBinaryExpressionRaw(binaryExpression: JKBinaryExpression) {
            binaryExpression.left.accept(this)
            printer.print(" ")
            printer.print(binaryExpression.operator.token.text)
            printer.print(" ")
            binaryExpression.right.accept(this)
        }

        override fun visitTypeParameterListRaw(typeParameterList: JKTypeParameterList) {
            if (typeParameterList.typeParameters.isNotEmpty()) {
                printer.par(ParenthesisKind.ANGLE) {
                    printer.renderList(typeParameterList.typeParameters) {
                        it.accept(this)
                    }
                }
            }
        }

        override fun visitTypeParameterRaw(typeParameter: JKTypeParameter) {
            typeParameter.annotationList.accept(this)
            typeParameter.name.accept(this)
            if (typeParameter.upperBounds.size == 1) {
                printer.printWithSurroundingSpaces(":")
                typeParameter.upperBounds.single().accept(this)
            }
        }

        override fun visitLiteralExpressionRaw(literalExpression: JKLiteralExpression) {
            printer.print(literalExpression.literal)
        }

        override fun visitPrefixExpressionRaw(prefixExpression: JKPrefixExpression) {
            printer.print(prefixExpression.operator.token.text)
            prefixExpression.expression.accept(this)
        }

        override fun visitThisExpressionRaw(thisExpression: JKThisExpression) {
            if (thisExpression.shouldBePreserved) {
                printExplicitLabel(thisExpression)
            }
            printer.print("this")
            thisExpression.qualifierLabel.accept(this)
        }

        override fun visitSuperExpressionRaw(superExpression: JKSuperExpression) {
            printer.print("super")
            val numberOfDirectSupertypes = superExpression.parentOfType<JKClass>()?.inheritance?.supertypeCount() ?: 0
            if (superExpression.superTypeQualifier != null && numberOfDirectSupertypes > 1) {
                printer.par(ParenthesisKind.ANGLE) {
                    printer.renderSymbol(superExpression.superTypeQualifier, superExpression)
                }
            } else {
                superExpression.outerTypeQualifier.accept(this)
            }
        }

        override fun visitContinueStatementRaw(continueStatement: JKContinueStatement) {
            printer.print("continue")
            continueStatement.label.accept(this)
            printer.print(" ")
        }

        override fun visitLabelEmptyRaw(labelEmpty: JKLabelEmpty) {}

        override fun visitLabelTextRaw(labelText: JKLabelText) {
            printer.print("@")
            labelText.label.accept(this)
            printer.print(" ")
        }

        override fun visitLabeledExpressionRaw(labeledExpression: JKLabeledExpression) {
            for (label in labeledExpression.labels) {
                label.accept(this)
                printer.print("@")
            }
            labeledExpression.statement.accept(this)
        }

        override fun visitNameIdentifierRaw(nameIdentifier: JKNameIdentifier) {
            printer.print(nameIdentifier.value.escaped())
        }

        override fun visitPostfixExpressionRaw(postfixExpression: JKPostfixExpression) {
            postfixExpression.expression.accept(this)
            printer.print(postfixExpression.operator.token.text)
        }

        override fun visitQualifiedExpressionRaw(qualifiedExpression: JKQualifiedExpression) {
            qualifiedExpression.receiver.accept(this)
            printer.print(".")
            qualifiedExpression.selector.accept(this)
        }

        override fun visitArrayAccessExpressionRaw(arrayAccessExpression: JKArrayAccessExpression) {
            arrayAccessExpression.receiver.accept(this)
            printer.print("[")
            arrayAccessExpression.indexExpression.accept(this)
            printer.print("]")
        }

        override fun visitArgumentListRaw(argumentList: JKArgumentList) {
            val arguments = argumentList.arguments
            if (!canMoveLambdaOutsideParentheses(argumentList)) {
                renderArguments(arguments, argumentList.hasTrailingComma)
                return
            }

            if (arguments.size > 1) {
                renderArguments(arguments.subList(0, arguments.lastIndex), hasTrailingComma = false)
            }
            printer.print(" ")

            val lambdaExpression = arguments.last().value as JKLambdaExpression
            printLeftNonCodeElements(lambdaExpression)
            renderLambdaExpressionWithoutFunctionalType(lambdaExpression)
            printRightNonCodeElements(lambdaExpression)
            printer.print(" ")
        }

        private fun renderArguments(arguments: List<JKArgument>, hasTrailingComma: Boolean) {
            printer.par {
                for ((i, argument) in arguments.withIndex()) {
                    printLeftNonCodeElements(argument)

                    if (argument is JKNamedArgument) {
                        visitNamedArgumentRaw(argument)
                    } else {
                        visitArgumentRaw(argument)
                    }

                    if (i < arguments.lastIndex || hasTrailingComma) {
                        printer.print(", ")
                    }
                    printRightNonCodeElements(argument)
                }
            }
        }

        override fun visitArgumentRaw(argument: JKArgument) {
            argument.value.accept(this)
        }

        override fun visitNamedArgumentRaw(namedArgument: JKNamedArgument) {
            namedArgument.name.accept(this)
            printer.printWithSurroundingSpaces("=")
            namedArgument.value.accept(this)
        }

        override fun visitCallExpressionRaw(callExpression: JKCallExpression) {
            printer.renderSymbol(callExpression.identifier, callExpression)
            if (callExpression.identifier.isAnnotationMethod()) return

            val methodName = callExpression.identifier.fqName
            if (isK1Mode() ||
                (methodName != "java.util.stream.Stream.collect" && !methodName.startsWith("java.util.stream.Collectors"))) {
                // Type arguments for Stream.collect calls cannot be explicitly specified in Kotlin.
                // This is a K2 counterpart to the K1 `RemoveJavaStreamsCollectCallTypeArgumentsProcessing`.
                callExpression.typeArgumentList.accept(this)
            }

            callExpression.arguments.accept(this)
        }

        override fun visitTypeArgumentListRaw(typeArgumentList: JKTypeArgumentList) {
            if (typeArgumentList.typeArguments.isNotEmpty()) {
                printer.par(ParenthesisKind.ANGLE) {
                    printer.renderList(typeArgumentList.typeArguments) {
                        it.accept(this)
                    }
                }
            }
        }

        override fun visitParenthesizedExpressionRaw(parenthesizedExpression: JKParenthesizedExpression) {
            printer.par {
                parenthesizedExpression.expression.accept(this)
            }
        }

        override fun visitDeclarationStatementRaw(declarationStatement: JKDeclarationStatement) {
            printer.renderList(declarationStatement.declaredStatements, ::ensureLineBreak) {
                it.accept(this)
            }
        }

        override fun visitTypeCastExpressionRaw(typeCastExpression: JKTypeCastExpression) {
            typeCastExpression.expression.accept(this)
            printer.printWithSurroundingSpaces("as")
            typeCastExpression.type.accept(this)
        }

        override fun visitWhileStatementRaw(whileStatement: JKWhileStatement) {
            printer.print("while (")
            whileStatement.condition.accept(this)
            printer.print(") ")
            if (whileStatement.body.isEmpty()) {
                printer.print(";")
            } else {
                whileStatement.body.accept(this)
            }
        }

        override fun visitLocalVariableRaw(localVariable: JKLocalVariable) {
            printer.print(" ")
            localVariable.annotationList.accept(this)
            renderModifiersList(localVariable)
            renderVariableDeclarationNameAndType(localVariable)
            if (localVariable.initializer !is JKStubExpression) {
                printer.printWithSurroundingSpaces("=")
                localVariable.initializer.accept(this)
            }
        }

        override fun visitEmptyStatementRaw(emptyStatement: JKEmptyStatement) {}

        override fun visitStubExpressionRaw(stubExpression: JKStubExpression) {}

        override fun visitKtConvertedFromForLoopSyntheticWhileStatementRaw(
            ktConvertedFromForLoopSyntheticWhileStatement: JKKtConvertedFromForLoopSyntheticWhileStatement
        ) {
            printer.renderList(ktConvertedFromForLoopSyntheticWhileStatement.variableDeclarations, ::ensureLineBreak) {
                it.accept(this)
            }
            ensureLineBreak()
            ktConvertedFromForLoopSyntheticWhileStatement.whileStatement.accept(this)
        }

        override fun visitNewExpressionRaw(newExpression: JKNewExpression) {
            if (newExpression.isAnonymousClass) {
                printer.print("object : ")
            }
            printer.renderSymbol(newExpression.classSymbol, newExpression)
            newExpression.typeArgumentList.accept(this)
            if (!newExpression.classSymbol.isInterface() || newExpression.arguments.arguments.isNotEmpty()) {
                newExpression.arguments.accept(this)
            }
            if (newExpression.isAnonymousClass) {
                newExpression.classBody.accept(this)
            }
        }

        override fun visitKtItExpressionRaw(ktItExpression: JKKtItExpression) {
            printer.print(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier)
        }

        override fun visitClassBodyRaw(classBody: JKClassBody) {
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

        override fun visitTypeElementRaw(typeElement: JKTypeElement) {
            typeElement.annotationList.accept(this)
            printer.renderType(typeElement.type, typeElement)
        }

        override fun visitBlockRaw(block: JKBlock) {
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

        override fun visitBlockStatementWithoutBracketsRaw(blockStatementWithoutBrackets: JKBlockStatementWithoutBrackets) {
            printer.renderList(blockStatementWithoutBrackets.statements, ::ensureLineBreak) {
                it.accept(this)
            }
        }

        override fun visitExpressionStatementRaw(expressionStatement: JKExpressionStatement) {
            expressionStatement.expression.accept(this)
        }

        override fun visitReturnStatementRaw(returnStatement: JKReturnStatement) {
            printer.print("return")
            returnStatement.label.accept(this)
            printer.print(" ")
            returnStatement.expression.accept(this)
        }

        override fun visitFieldAccessExpressionRaw(fieldAccessExpression: JKFieldAccessExpression) {
            try {
                printer.renderSymbol(fieldAccessExpression.identifier, fieldAccessExpression)
            } catch (ignored: UninitializedPropertyAccessException) {
                // This should only happen on copy-pasting broken (incomplete) code
                val psi = fieldAccessExpression.psi as? PsiReferenceExpression ?: return
                printer.print(psi.text)
            }
        }

        override fun visitPackageAccessExpressionRaw(packageAccessExpression: JKPackageAccessExpression) {
            printer.renderSymbol(packageAccessExpression.identifier, packageAccessExpression)
        }

        override fun visitMethodReferenceExpressionRaw(methodReferenceExpression: JKMethodReferenceExpression) {
            methodReferenceExpression.qualifier.accept(this)
            printer.print("::")
            val needFqName = methodReferenceExpression.qualifier is JKStubExpression
            val displayName =
                if (needFqName) methodReferenceExpression.identifier.getDisplayFqName()
                else methodReferenceExpression.identifier.name

            printer.print(displayName.escapedAsQualifiedName())

        }

        override fun visitDelegationConstructorCallRaw(delegationConstructorCall: JKDelegationConstructorCall) {
            delegationConstructorCall.expression.accept(this)
            delegationConstructorCall.arguments.accept(this)
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

        override fun visitConstructorRaw(constructor: JKConstructor) {
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

        override fun visitKtPrimaryConstructorRaw(ktPrimaryConstructor: JKKtPrimaryConstructor) {
            ktPrimaryConstructor.annotationList.accept(this)
            val hasRenderedModifiers = renderModifiersList(ktPrimaryConstructor)

            val needConstructorKeyword = ktPrimaryConstructor.hasAnnotations || hasRenderedModifiers
            if (needConstructorKeyword) {
                printer.print("constructor")
            }

            val hasSecondaryConstructors =
                ktPrimaryConstructor.parentOfType<JKClassBody>()?.declarations.orEmpty().filterIsInstance<JKConstructorImpl>().isNotEmpty()

            if (ktPrimaryConstructor.parameters.isNotEmpty() || needConstructorKeyword || hasSecondaryConstructors) {
                // Print explicit primary constructor `()` with parameters, if any
                renderParameterList(ktPrimaryConstructor)
            }
        }

        override fun visitLambdaExpressionRaw(lambdaExpression: JKLambdaExpression) {
            if (lambdaExpression.functionalType.isPresent()) {
                // print SAM constructor
                val renderTypeParameters = isK1Mode()
                printer.renderType(lambdaExpression.functionalType.type, lambdaExpression, renderTypeParameters)
                printer.print(" ")
            }

            renderLambdaExpressionWithoutFunctionalType(lambdaExpression)
        }

        private fun renderLambdaExpressionWithoutFunctionalType(lambdaExpression: JKLambdaExpression) {
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

        override fun visitBlockStatementRaw(blockStatement: JKBlockStatement) {
            blockStatement.block.accept(this)
        }

        override fun visitKtAssignmentStatementRaw(ktAssignmentStatement: JKKtAssignmentStatement) {
            ktAssignmentStatement.field.accept(this)
            printer.print(" ")
            printer.print(ktAssignmentStatement.token.text)
            printer.print(" ")
            ktAssignmentStatement.expression.accept(this)
        }

        override fun visitAssignmentChainAlsoLinkRaw(assignmentChainAlsoLink: JKAssignmentChainAlsoLink) {
            assignmentChainAlsoLink.receiver.accept(this)
            printer.print(".also { ")
            assignmentChainAlsoLink.assignmentStatement.accept(this)
            printer.print(" ")
            printer.print("}")
        }

        override fun visitAssignmentChainLetLinkRaw(assignmentChainLetLink: JKAssignmentChainLetLink) {
            assignmentChainLetLink.receiver.accept(this)
            printer.print(".let { ")
            assignmentChainLetLink.assignmentStatement.accept(this)
            printer.print("; ")
            assignmentChainLetLink.field.accept(this)
            printer.print(" ")
            printer.print("}")
        }

        override fun visitKtWhenBlockRaw(ktWhenBlock: JKKtWhenBlock) {
            printer.print("when (")
            ktWhenBlock.expression.accept(this)
            printer.print(")")
            printer.block {
                printer.renderList(ktWhenBlock.cases, ::ensureLineBreak) {
                    it.accept(this)
                }
            }
        }

        override fun visitKtWhenExpressionRaw(ktWhenExpression: JKKtWhenExpression) {
            visitKtWhenBlockRaw(ktWhenExpression)
        }

        override fun visitKtWhenStatementRaw(ktWhenStatement: JKKtWhenStatement) {
            visitKtWhenBlockRaw(ktWhenStatement)
        }

        override fun visitAnnotationListRaw(annotationList: JKAnnotationList) {
            printer.renderList(annotationList.annotations, " ") {
                it.accept(this)
            }
            if (annotationList.annotations.isNotEmpty() && !printer.lastSymbolIsLineBreak) {
                printer.print(" ")
            }
        }

        override fun visitAnnotationRaw(annotation: JKAnnotation) {
            printer.print("@")
            annotation.useSiteTarget?.let { printer.print("${it.renderName}:") }
            printer.renderSymbol(annotation.classSymbol, annotation)
            if (annotation.arguments.isNotEmpty()) {
                printer.par {
                    printer.renderList(annotation.arguments) { it.accept(this) }
                }
            }
        }

        override fun visitAnnotationNameParameterRaw(annotationNameParameter: JKAnnotationNameParameter) {
            annotationNameParameter.name.accept(this)
            printer.printWithSurroundingSpaces("=")
            annotationNameParameter.value.accept(this)
        }

        override fun visitAnnotationParameterRaw(annotationParameter: JKAnnotationParameter) {
            annotationParameter.value.accept(this)
        }

        override fun visitClassLiteralExpressionRaw(classLiteralExpression: JKClassLiteralExpression) {
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

        override fun visitKtWhenCaseRaw(ktWhenCase: JKKtWhenCase) {
            printer.renderList(ktWhenCase.labels) {
                it.accept(this)
            }
            printer.printWithSurroundingSpaces("->")
            ktWhenCase.statement.accept(this)
        }

        override fun visitKtElseWhenLabelRaw(ktElseWhenLabel: JKKtElseWhenLabel) {
            printer.print("else")
        }

        override fun visitKtValueWhenLabelRaw(ktValueWhenLabel: JKKtValueWhenLabel) {
            ktValueWhenLabel.expression.accept(this)
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

        private fun printExplicitLabel(thisExpression: JKThisExpression) {
            // TODO: Currently disabled for K2 J2K, but may have to be enabled
            // if we don't figure out how to accurately preserve original `this` expressions without a post-processing
            if (KotlinPluginModeProvider.isK2Mode()) return
            val label = elementInfoStorage.getOrCreateExplicitLabelForElement(thisExpression)
            printer.print(label.render())
        }

        private fun printInferenceLabel(element: JKElement) {
            if (KotlinPluginModeProvider.isK2Mode()) return
            val label = elementInfoStorage.getOrCreateInferenceLabelForElement(element)
            printer.print(label.render())
        }
    }
}