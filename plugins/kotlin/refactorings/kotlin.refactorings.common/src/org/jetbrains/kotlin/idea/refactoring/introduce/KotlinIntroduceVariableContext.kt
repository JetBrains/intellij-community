// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.idea.base.psi.moveInsideParenthesesAndReplaceWith
import org.jetbrains.kotlin.idea.base.psi.shouldLambdaParameterBeNamed
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getOutermostParenthesizerOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isLambdaOutsideParentheses
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment
import org.jetbrains.kotlin.utils.exceptions.withPsiEntry
import org.jetbrains.kotlin.utils.sure
import org.jetbrains.kotlin.utils.zipIfSizesAreEqual

abstract class KotlinIntroduceVariableContext(
    project: Project,
    private val isVar: Boolean,
    private val nameSuggestions: List<Collection<String>>,
    /**
     * If true then the first occurrence among all found occurrences should be replaced and a variable can't be introduced inplace.
     */
    private val replaceFirstOccurrence: Boolean,
    private val isDestructuringDeclaration: Boolean,
) {
    protected val psiFactory = KtPsiFactory(project)

    protected abstract fun convertToBlockBodyAndSpecifyReturnTypeByAnalyze(declaration: KtDeclarationWithBody): KtDeclarationWithBody

    protected abstract fun KtLambdaArgument.getLambdaArgumentNameByAnalyze(): Name?

    protected abstract fun analyzeDeclarationAndSpecifyTypeIfNeeded(declaration: KtDeclaration)

    var reference: SmartPsiElementPointer<KtExpression>? = null; private set
    val references = ArrayList<SmartPsiElementPointer<KtExpression>>()

    var introducedVariablePointer: SmartPsiElementPointer<KtDeclaration>? = null; private set

    fun convertToBlockIfNeededAndRunRefactoring(
        expression: KtExpression,
        commonContainer: PsiElement,
        commonParent: PsiElement,
        allReplaces: List<KtExpression>
    ) {
        if (commonContainer !is KtDeclarationWithBody) return runRefactoring(expression, commonContainer, commonParent, allReplaces)

        commonContainer.bodyExpression.sure { "Original body is not found: $commonContainer" }

        expression.substringContextOrThis.putCopyableUserData(EXPRESSION_KEY, true)
        for (replace in allReplaces) {
            replace.substringContextOrThis.putCopyableUserData(REPLACE_KEY, true)
        }
        commonParent.putCopyableUserData(COMMON_PARENT_KEY, true)

        val newDeclaration = convertToBlockBodyAndSpecifyReturnTypeByAnalyze(commonContainer)
        val newCommonContainer = newDeclaration.bodyBlockExpression

        val newExpression = newCommonContainer?.findExpressionByCopyableDataAndClearIt(EXPRESSION_KEY)
            ?.getSubstringExpressionOrThis(expression)
        val newCommonParent = newCommonContainer?.findElementByCopyableDataAndClearIt(COMMON_PARENT_KEY)
        val newAllReplaces = newCommonContainer?.findExpressionsByCopyableDataAndClearIt(REPLACE_KEY)
            ?.zipIfSizesAreEqual(allReplaces)
            ?.map { (newReplace, originalReplace) -> newReplace.getSubstringExpressionOrThis(originalReplace) }

        if (newExpression != null && newCommonParent != null && newAllReplaces != null) {
            runRefactoring(newExpression, newCommonContainer, newCommonParent, newAllReplaces)
        } else {
            errorWithAttachment("Failed to find selected expression after converting expression body to block body") {
                withPsiEntry("declaration", commonContainer)
                withPsiEntry("expression", expression)
            }
        }
    }

    private fun replaceExpression(
        actualExpression: KtExpression,
        expressionToReplace: KtExpression,
        addToReferences: Boolean,
        lambdaArgumentName: Name?,
    ): KtExpression {
        val isActualExpression = actualExpression.getOutermostParenthesizerOrThis() == expressionToReplace

        val replacement = psiFactory.createExpression(nameSuggestions.single().first())
        val substringInfo = expressionToReplace.extractableSubstringInfo
        var result = when {
            expressionToReplace.isLambdaOutsideParentheses() -> {
                val functionLiteralArgument = expressionToReplace.getStrictParentOfType<KtLambdaArgument>()!!
                val newCallExpression = functionLiteralArgument.moveInsideParenthesesAndReplaceWith(replacement, lambdaArgumentName)
                newCallExpression.valueArguments.last().getArgumentExpression()!!
            }

            substringInfo != null -> substringInfo.replaceWith(replacement)
            else -> expressionToReplace.replace(replacement) as KtExpression
        }

        result = result.removeTemplateEntryBracesIfPossible()

        if (addToReferences) {
            references.addIfNotNull(SmartPointerManager.createPointer(result))
        }

        if (isActualExpression) {
            reference = SmartPointerManager.createPointer(result)
        }

        return result
    }

    private fun createBasicPropertyOrDestructuringDeclaration(expression: KtExpression): KtDeclaration {
        val initializer = (expression as? KtParenthesizedExpression)?.expression ?: expression
        val initializerText = if (initializer.mustBeParenthesizedInInitializerPosition()) "(${initializer.text})" else initializer.text

        val varOvVal = if (isVar) "var" else "val"

        return if (isDestructuringDeclaration) {
            val destructuringDeclarationText = buildString {
                nameSuggestions.joinTo(this, prefix = "$varOvVal (", postfix = ")") { it.first() }
                append(" = ")
                append(initializerText)
            }
            psiFactory.createDestructuringDeclaration(destructuringDeclarationText)
        } else {
            val propertyText = buildString {
                append("$varOvVal ")
                val single = nameSuggestions.single()
                checkWithAttachment(single.isNotEmpty(), {
                    "nameSuggestions: $nameSuggestions"
                }) {
                    withPsiEntry("expression.kt", expression)
                }
                append(single.first())
                append(" = ")
                append(initializerText)
            }
            psiFactory.createProperty(propertyText)
        }
    }

    private fun runRefactoring(
        expression: KtExpression,
        commonContainer: PsiElement,
        commonParent: PsiElement,
        allReplaces: List<KtExpression>,
    ) {
        var property = createBasicPropertyOrDestructuringDeclaration(expression)
        val lambdaArgumentName = expression.getContainingLambdaOutsideParentheses()
            ?.takeIf { shouldLambdaParameterBeNamed(it) }
            ?.getLambdaArgumentNameByAnalyze()
        var anchor = calculateAnchorForExpressions(commonParent, commonContainer, allReplaces) ?: return
        val needBraces = commonContainer !is KtBlockExpression && commonContainer !is KtClassBody && commonContainer !is KtFile

        if (!needBraces) {
            property = commonContainer.addBefore(property, anchor) as KtDeclaration
            commonContainer.addBefore(psiFactory.createNewLine(), anchor)
        } else {
            var emptyBody: KtExpression = psiFactory.createEmptyBody()
            val firstChild = emptyBody.firstChild
            emptyBody.addAfter(psiFactory.createNewLine(), firstChild)

            val haveOccurrencesToReplace = replaceFirstOccurrence || allReplaces.size > 1
            if (haveOccurrencesToReplace) {
                for ((index, replace) in allReplaces.withIndex()) {
                    if (index == 0 && !replaceFirstOccurrence) continue

                    val exprAfterReplace = replaceExpression(expression, replace, addToReferences = false, lambdaArgumentName)
                    exprAfterReplace.isOccurrence = true
                    if (anchor == replace) {
                        anchor = exprAfterReplace
                    }
                }

                var oldElement: PsiElement = commonContainer

                val body = when (commonContainer) {
                    is KtWhenEntry -> commonContainer.expression
                    is KtContainerNodeForControlStructureBody -> commonContainer.expression
                    else -> null
                }
                if (body != null) {
                    oldElement = body
                }

                modifyPsiAndUpdateReference(anchorForOffsetCalculation = oldElement) {
                    emptyBody.addAfter(oldElement, firstChild)
                } ?: return

                emptyBody.addAfter(psiFactory.createNewLine(), firstChild)
                property = emptyBody.addAfter(property, firstChild) as KtDeclaration
                emptyBody.addAfter(psiFactory.createNewLine(), firstChild)

                emptyBody = modifyPsiAndUpdateReference(anchorForOffsetCalculation = emptyBody) {
                    anchor.replace(emptyBody) as KtBlockExpression
                } ?: return

                emptyBody.accept(object : KtTreeVisitorVoid() {
                    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                        if (!expression.isOccurrence) return

                        expression.isOccurrence = false
                        references.add(SmartPointerManager.createPointer(expression))
                    }
                })
            } else {
                val parent = anchor.parent
                val copyTo = parent.lastChild
                val copyFrom = anchor.nextSibling

                property = emptyBody.addAfter(property, firstChild) as KtDeclaration
                emptyBody.addAfter(psiFactory.createNewLine(), firstChild)
                if (copyFrom != null && copyTo != null) {
                    emptyBody.addRangeAfter(copyFrom, copyTo, property)
                    parent.deleteChildRange(copyFrom, copyTo)
                }
                emptyBody = anchor.replace(emptyBody) as KtBlockExpression
            }
            property = emptyBody.children.firstIsInstance<KtDeclaration>()

            if (commonContainer is KtContainerNode) {
                if (commonContainer.parent is KtIfExpression) {
                    val next = commonContainer.nextSibling
                    if (next != null) {
                        val nextnext = next.nextSibling
                        if (nextnext != null && nextnext.node.elementType == KtTokens.ELSE_KEYWORD) {
                            if (next is PsiWhiteSpace) {
                                next.replace(psiFactory.createWhiteSpace()) // replace "\n " with " "
                            }
                        }
                    }
                }
            }
        }
        if (!needBraces) {
            for ((index, replace) in allReplaces.withIndex()) {
                if (index == 0 && !replaceFirstOccurrence) {
                    val sibling = PsiTreeUtil.skipSiblingsBackward(replace, PsiWhiteSpace::class.java)
                    if (sibling == property) {
                        replace.parent.deleteChildRange(property.nextSibling, replace)
                    } else {
                        replace.delete()
                    }
                } else {
                    replaceExpression(expression, replace, addToReferences = true, lambdaArgumentName)
                }
            }
        }
        introducedVariablePointer = property.createSmartPointer()

        analyzeDeclarationAndSpecifyTypeIfNeeded(property)
    }

    /**
     * @param modifyAnchor should return modified anchor
     * @return anchor after modification
     */
    private fun <T: PsiElement> modifyPsiAndUpdateReference(anchorForOffsetCalculation: T, modifyAnchor: () -> T): T? {
        val selectedExpression = reference?.element ?: return null
        val offsetRelativeToAnchor = selectedExpression.textRange.startOffset - anchorForOffsetCalculation.textRange.startOffset
        val selectedExpressionText = selectedExpression.text

        val anchorAfterModification = modifyAnchor()

        findElementByOffsetAndText(offsetRelativeToAnchor, selectedExpressionText, anchorAfterModification)?.let {
            reference = SmartPointerManager.createPointer(it as KtExpression)
        }

        return anchorAfterModification
    }

    private fun KtExpression.getSubstringExpressionOrThis(oldExpression: KtExpression): KtExpression {
        val oldSubstringInfo = oldExpression.extractableSubstringInfo ?: return this
        val newSubstringInfo = oldSubstringInfo.copy(this as KtStringTemplateExpression)
        return newSubstringInfo.createExpression()
    }

    private fun findElementByOffsetAndText(offset: Int, text: String, newContainer: PsiElement): PsiElement? =
        newContainer.findElementAt(offset)?.parents(withSelf = true)?.firstOrNull { (it as? KtExpression)?.text == text }

    companion object {
        private val EXPRESSION_KEY = Key.create<Boolean>("EXPRESSION_KEY")
        private val REPLACE_KEY = Key.create<Boolean>("REPLACE_KEY")
        private val COMMON_PARENT_KEY = Key.create<Boolean>("COMMON_PARENT_KEY")

        private var KtExpression.isOccurrence: Boolean by NotNullablePsiCopyableUserDataProperty(Key.create("OCCURRENCE"), false)
    }
}