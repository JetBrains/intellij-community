// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.refactoring.createTempCopy
import org.jetbrains.kotlin.idea.refactoring.introduce.extractionEngine.AnalysisResult.ErrorMessage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtTypeConstraint
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.createDeclarationByPattern
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isInsideOf
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.DFS.CollectingNodeHandler
import org.jetbrains.kotlin.utils.DFS.Neighbors
import org.jetbrains.kotlin.utils.DFS.VisitedWithSet
import org.jetbrains.kotlin.utils.DFS.dfsFromNode
import java.util.Collections


internal fun IExtractionData.createTemporaryDeclaration(pattern: String): KtNamedDeclaration {
    val targetSiblingMarker = Any()
    PsiTreeUtil.mark(targetSibling, targetSiblingMarker)
    val tmpFile = originalFile.createTempCopy("")
    tmpFile.deleteChildRange(tmpFile.firstChild, tmpFile.lastChild)
    tmpFile.addRange(originalFile.firstChild, originalFile.lastChild)
    val newTargetSibling = PsiTreeUtil.releaseMark(tmpFile, targetSiblingMarker)!!
    val newTargetParent = newTargetSibling.parent

    val declaration = KtPsiFactory(project).createDeclarationByPattern<KtNamedDeclaration>(
        pattern,
        PsiChildRange(originalElements.firstOrNull(), originalElements.lastOrNull())
    )
    return if (insertBefore) {
        newTargetParent.addBefore(declaration, newTargetSibling) as KtNamedDeclaration
    } else {
        newTargetParent.addAfter(declaration, newTargetSibling) as KtNamedDeclaration
    }
}

fun IExtractionData.createTemporaryCodeBlock(): KtBlockExpression {
    if (options.extractAsProperty) {
        return ((createTemporaryDeclaration("val = {\n$0\n}\n") as KtProperty).initializer as KtLambdaExpression).bodyExpression!!
    }
    return (createTemporaryDeclaration("fun() {\n$0\n}\n") as KtNamedFunction).bodyBlockExpression!!
}

fun IExtractionData.isLocal(): Boolean {
    val parent = targetSibling.parent
    return parent !is KtClassBody && (parent !is KtFile || parent.isScript())
}

fun IExtractionData.isVisibilityApplicable(): Boolean {
    if (isLocal()) return false
    if (commonParent.parentsWithSelf.any { it is KtNamedFunction && it.hasModifier(KtTokens.INLINE_KEYWORD) && it.isPublic }) return false
    return true
}

fun IExtractionData.getDefaultVisibility(): KtModifierKeywordToken? {
    if (!isVisibilityApplicable()) return null

    val parent = targetSibling.getStrictParentOfType<KtDeclaration>()
    if (parent is KtClass) {
        if (parent.isInterface()) return null
        if (parent.isEnum() && commonParent.getNonStrictParentOfType<KtEnumEntry>()?.getStrictParentOfType<KtClass>() == parent) return null
    }

    return KtTokens.PRIVATE_KEYWORD
}

fun KtTypeParameter.collectRelevantConstraints(): List<KtTypeConstraint> {
    val typeConstraints = getNonStrictParentOfType<KtTypeParameterListOwner>()?.typeConstraints ?: return Collections.emptyList()
    return typeConstraints.filter { it.subjectTypeParameterName?.mainReference?.resolve() == this }
}

fun TypeParameter.collectReferencedTypes(): ArrayList<KtTypeReference> {
    val typeRefs = ArrayList<KtTypeReference>()
    originalDeclaration.extendsBound?.let { typeRefs.add(it) }
    originalConstraints.mapNotNullTo(typeRefs) { it.boundTypeReference }

    return typeRefs
}

fun <KotlinType> KotlinType.collectReferencedTypes(
    processTypeArguments: Boolean,
    args: (KotlinType) -> List<KotlinType>
): List<KotlinType> {
    if (!processTypeArguments) return Collections.singletonList(this)
    return dfsFromNode(
        this!!,
        Neighbors { current -> args(current) },
        VisitedWithSet(),
        object : CollectingNodeHandler<KotlinType, KotlinType, ArrayList<KotlinType>>(ArrayList()) {
            override fun afterChildren(current: KotlinType) {
                result.add(current)
            }
        }
    )!!
}

fun <KotlinType> KotlinType.processTypeIfExtractable(
    typeParameters: MutableSet<TypeParameter>,
    nonDenotableTypes: MutableSet<KotlinType>,
    processTypeArguments: Boolean = true,
    args: (KotlinType) -> List<KotlinType>,
    isResolvableInScope: (typeToCheck: KotlinType, typeParameters: MutableSet<TypeParameter>) -> Boolean
): Boolean {
    return collectReferencedTypes(processTypeArguments, args).fold(true) { extractable, typeToCheck ->
        when {
            isResolvableInScope(typeToCheck, typeParameters) ->
                extractable

            else -> {
                nonDenotableTypes.add(typeToCheck)
                false
            }
        }
    }
}

fun IExtractableCodeDescriptor<*>.getOccurrenceContainer(): PsiElement? {
    return extractionData.duplicateContainer ?: extractionData.targetSibling.parent
}

fun <KotlinType> IExtractionData.checkDeclarationsMovingOutOfScope(
    enclosingDeclaration: KtDeclaration, controlFlow: ControlFlow<KotlinType>, textPresentation: (KtNamedDeclaration) -> String
): ErrorMessage? {
    val declarationsOutOfScope = HashSet<KtNamedDeclaration>()
    controlFlow.jumpOutputValue?.elementToInsertAfterCall?.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
            val target = expression.mainReference.resolve()
            if (target is KtNamedDeclaration && target.isInsideOf(physicalElements) && target.getStrictParentOfType<KtDeclaration>() == enclosingDeclaration) {
                declarationsOutOfScope.add(target)
            }
        }
    })

    if (declarationsOutOfScope.isNotEmpty()) {
        val declStr = declarationsOutOfScope.map(textPresentation).sorted()
        return ErrorMessage.DECLARATIONS_OUT_OF_SCOPE.addAdditionalInfo(declStr)
    }

    return null
}

fun convertInfixCallToOrdinary(element: KtBinaryExpression): KtExpression {
    val argument = KtPsiUtil.safeDeparenthesize(element.right!!)
    val pattern = "$0.$1" + when (argument) {
        is KtLambdaExpression -> " $2:'{}'"
        else -> "($2)"
    }

    val replacement = KtPsiFactory(element.project).createExpressionByPattern(
        pattern,
        element.left!!,
        element.operationReference,
        argument
    )

    return element.replace(replacement) as KtExpression
}