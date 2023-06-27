// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing.processings

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantGetter
import org.jetbrains.kotlin.idea.codeinsight.utils.isRedundantSetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantGetter
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantSetter
import org.jetbrains.kotlin.idea.core.*
import org.jetbrains.kotlin.idea.inspections.*
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.idea.intentions.RemoveExplicitTypeArgumentsIntention
import org.jetbrains.kotlin.idea.intentions.RemoveExplicitTypeIntention
import org.jetbrains.kotlin.idea.intentions.addUseSiteTarget
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.BranchedFoldingUtils
import org.jetbrains.kotlin.idea.j2k.post.processing.InspectionLikeProcessingForElement
import org.jetbrains.kotlin.idea.quickfix.AddConstModifierFix
import org.jetbrains.kotlin.idea.references.KtSimpleNameReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.readWriteAccess
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.j2k.isInSingleLine
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.types.checker.KotlinTypeChecker
import org.jetbrains.kotlin.types.isFlexible
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


internal class RemoveExplicitPropertyTypeProcessing : InspectionLikeProcessingForElement<KtProperty>(KtProperty::class.java) {
    override fun isApplicableTo(element: KtProperty, settings: ConverterSettings?): Boolean {
        if (element.isMember && !element.isPrivate()) return false

        val typeReference = element.typeReference
        if (typeReference == null || typeReference.annotationEntries.isNotEmpty()) return false

        val needLocalVariablesTypes = settings?.specifyLocalVariableTypeByDefault == true
        if (needLocalVariablesTypes && element.isLocal) return false

        val initializer = element.initializer ?: return false
        val initializerType =
            initializer.analyzeInContext(initializer.getResolutionScope()).getType(initializer) ?: return false

        // https://kotlinlang.org/docs/coding-conventions.html#platform-types
        // Any property initialized with an expression of a platform type must declare its Kotlin type explicitly
        if (element.isMember && initializerType.isFlexible()) {
            return false
        }

        val propertyType = element.resolveToDescriptorIfAny().safeAs<CallableDescriptor>()?.returnType ?: return false
        return KotlinTypeChecker.DEFAULT.equalTypes(initializerType, propertyType)
    }

    override fun apply(element: KtProperty) {
        val typeReference = element.typeReference ?: return
        element.colon?.let { colon ->
            val followingWhiteSpace = colon.nextSibling?.takeIf { following ->
                following is PsiWhiteSpace && following.isInSingleLine()
            }
            followingWhiteSpace?.delete()
            colon.delete()
        }
        typeReference.delete()
    }
}

internal class RemoveRedundantNullabilityProcessing : InspectionLikeProcessingForElement<KtProperty>(KtProperty::class.java) {
    override fun isApplicableTo(element: KtProperty, settings: ConverterSettings?): Boolean {
        if (!element.isLocal) return false
        val typeReference = element.typeReference
        if (typeReference == null || typeReference.typeElement !is KtNullableType) return false
        val initializerType = element.initializer?.let {
            it.analyzeInContext(element.getResolutionScope()).getType(it)
        }
        if (initializerType?.isNullable() == true) return false

        return ReferencesSearch.search(element, element.useScope).findAll().mapNotNull { ref ->
            val parent = (ref.element.parent as? KtExpression)?.asAssignment()
            parent?.takeIf { it.left == ref.element }
        }.all {
            val right = it.right
            val withoutExpectedType = right?.analyzeInContext(element.getResolutionScope())
            withoutExpectedType?.getType(right)?.isNullable() == false
        }
    }

    override fun apply(element: KtProperty) {
        val typeElement = element.typeReference?.typeElement
        typeElement?.replace(typeElement.safeAs<KtNullableType>()?.innerType ?: return)
    }
}

internal class RemoveExplicitTypeArgumentsProcessing :
    InspectionLikeProcessingForElement<KtTypeArgumentList>(KtTypeArgumentList::class.java) {
    override fun isApplicableTo(element: KtTypeArgumentList, settings: ConverterSettings?): Boolean =
        RemoveExplicitTypeArgumentsIntention.isApplicableTo(element, approximateFlexible = true)

    override fun apply(element: KtTypeArgumentList) {
        element.delete()
    }
}

// the types arguments for Stream.collect calls cannot be explicitly specified in Kotlin,
// but we need them in nullability inference, so we remove it here
internal class RemoveJavaStreamsCollectCallTypeArgumentsProcessing :
    InspectionLikeProcessingForElement<KtCallExpression>(KtCallExpression::class.java) {
    override fun isApplicableTo(element: KtCallExpression, settings: ConverterSettings?): Boolean {
        if (element.typeArgumentList == null) return false
        if (element.calleeExpression?.text != COLLECT_FQ_NAME.shortName().identifier) return false
        return element.isCalling(COLLECT_FQ_NAME)
    }

    override fun apply(element: KtCallExpression) {
        element.typeArgumentList?.delete()
    }

    companion object {
        private val COLLECT_FQ_NAME = FqName("java.util.stream.Stream.collect")
    }
}


internal class RemoveRedundantOverrideVisibilityProcessing :
    InspectionLikeProcessingForElement<KtCallableDeclaration>(KtCallableDeclaration::class.java) {

    override fun isApplicableTo(element: KtCallableDeclaration, settings: ConverterSettings?): Boolean {
        if (!element.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return false
        return element.visibilityModifier() != null
    }

    override fun apply(element: KtCallableDeclaration) {
        val modifier = element.visibilityModifierType() ?: return
        element.setVisibility(modifier)
    }
}

internal class ReplaceGetterBodyWithSingleReturnStatementWithExpressionBody :
    InspectionLikeProcessingForElement<KtPropertyAccessor>(KtPropertyAccessor::class.java) {

    private fun KtPropertyAccessor.singleBodyStatementExpression() =
        bodyBlockExpression?.statements
            ?.singleOrNull()
            ?.safeAs<KtReturnExpression>()
            ?.takeIf { it.labeledExpression == null }
            ?.returnedExpression

    override fun isApplicableTo(element: KtPropertyAccessor, settings: ConverterSettings?): Boolean {
        if (!element.isGetter) return false
        return element.singleBodyStatementExpression() != null
    }

    override fun apply(element: KtPropertyAccessor) {
        val body = element.bodyExpression ?: return
        val returnedExpression = element.singleBodyStatementExpression() ?: return

        val commentSaver = CommentSaver(body)
        element.addBefore(KtPsiFactory(element.project).createEQ(), body)
        val newBody = body.replaced(returnedExpression)
        commentSaver.restore(newBody)
    }
}

internal class RemoveRedundantCastToNullableProcessing :
    InspectionLikeProcessingForElement<KtBinaryExpressionWithTypeRHS>(KtBinaryExpressionWithTypeRHS::class.java) {

    override fun isApplicableTo(element: KtBinaryExpressionWithTypeRHS, settings: ConverterSettings?): Boolean {
        if (element.right?.typeElement !is KtNullableType) return false
        val context = element.analyze()
        val leftType = context.getType(element.left) ?: return false
        val rightType = context.get(BindingContext.TYPE, element.right) ?: return false
        return !leftType.isMarkedNullable && rightType.isMarkedNullable
    }

    override fun apply(element: KtBinaryExpressionWithTypeRHS) {
        val type = element.right?.typeElement as? KtNullableType ?: return
        type.replace(type.innerType ?: return)
    }
}

internal class RemoveRedundantSamAdaptersProcessing :
    InspectionLikeProcessingForElement<KtCallExpression>(KtCallExpression::class.java) {
    override val writeActionNeeded = false

    override fun isApplicableTo(element: KtCallExpression, settings: ConverterSettings?): Boolean =
        RedundantSamConstructorInspection.samConstructorCallsToBeConverted(element).isNotEmpty()

    override fun apply(element: KtCallExpression) {
        val callsToBeConverted = RedundantSamConstructorInspection.samConstructorCallsToBeConverted(element)
        runWriteAction {
            for (call in callsToBeConverted) {
                RedundantSamConstructorInspection.replaceSamConstructorCall(call)
            }
        }
    }
}

internal class UninitializedVariableReferenceFromInitializerToThisReferenceProcessing :
    InspectionLikeProcessingForElement<KtSimpleNameExpression>(KtSimpleNameExpression::class.java) {

    override fun isApplicableTo(element: KtSimpleNameExpression, settings: ConverterSettings?): Boolean {
        val anonymousObject = element.getStrictParentOfType<KtClassOrObject>()?.takeIf { it.name == null } ?: return false
        val resolved = element.mainReference.resolve() ?: return false
        if (resolved.isAncestor(element, strict = true)) {
            if (resolved is KtVariableDeclaration && resolved.hasInitializer()) {
                if (resolved.initializer?.getChildOfType<KtClassOrObject>() == anonymousObject) {
                    return true
                }
            }
        }
        return false
    }

    override fun apply(element: KtSimpleNameExpression) {
        element.replaced(KtPsiFactory(element.project).createThisExpression())
    }
}

internal class UnresolvedVariableReferenceFromInitializerToThisReferenceProcessing :
    InspectionLikeProcessingForElement<KtSimpleNameExpression>(KtSimpleNameExpression::class.java) {

    override fun isApplicableTo(element: KtSimpleNameExpression, settings: ConverterSettings?): Boolean {
        val anonymousObject = element.getStrictParentOfType<KtClassOrObject>() ?: return false
        val variable = anonymousObject.getStrictParentOfType<KtVariableDeclaration>() ?: return false
        if (variable.nameAsName != element.getReferencedNameAsName()) return false
        if (variable.initializer?.getChildOfType<KtClassOrObject>() != anonymousObject) return false
        return element.mainReference.resolve() == null
    }

    override fun apply(element: KtSimpleNameExpression) {
        element.replaced(KtPsiFactory(element.project).createThisExpression())
    }
}

internal class VarToValProcessing : InspectionLikeProcessingForElement<KtProperty>(KtProperty::class.java) {
    companion object {
        private val JPA_COLUMN_ANNOTATIONS: Set<FqName> = setOf(
            FqName("javax.persistence.Column"),
            FqName("jakarta.persistence.Column"),
        )
    }

    private fun KtProperty.hasWriteUsages(): Boolean =
        ReferencesSearch.search(this, useScope).any { usage ->
            (usage as? KtSimpleNameReference)?.element?.let { nameReference ->
                val receiver = nameReference.parent?.safeAs<KtDotQualifiedExpression>()?.receiverExpression
                if (nameReference.getStrictParentOfType<KtAnonymousInitializer>() != null
                    && (receiver == null || receiver is KtThisExpression)
                ) return@let false
                nameReference.readWriteAccess(useResolveForReadWrite = true).isWrite
            } == true
        }

    override fun isApplicableTo(element: KtProperty, settings: ConverterSettings?): Boolean {
        if (!element.isVar) return false
        if (!element.isPrivate()) return false
        val descriptor = element.resolveToDescriptorIfAny() as? PropertyDescriptor ?: return false
        if (descriptor.overriddenDescriptors.any { it.safeAs<VariableDescriptor>()?.isVar == true }) return false

        descriptor.backingField?.annotations?.let { annotations ->
            JPA_COLUMN_ANNOTATIONS.forEach {
                if (annotations.hasAnnotation(it)) return false
            }
        }
        return !element.hasWriteUsages()
    }

    override fun apply(element: KtProperty) {
        val psiFactory = KtPsiFactory(element.project)
        element.valOrVarKeyword.replace(psiFactory.createValKeyword())
    }
}

internal class JavaObjectEqualsToEqOperatorProcessing : InspectionLikeProcessingForElement<KtCallExpression>(KtCallExpression::class.java) {
    companion object {
        val CALL_FQ_NAME = FqName("java.util.Objects.equals")
    }

    override fun isApplicableTo(element: KtCallExpression, settings: ConverterSettings?): Boolean {
        if (element.calleeExpression?.text != CALL_FQ_NAME.shortName().identifier) return false
        if (element.valueArguments.size != 2) return false
        if (element.valueArguments.any { it.getArgumentExpression() == null }) return false
        return element.isCalling(CALL_FQ_NAME)
    }

    override fun apply(element: KtCallExpression) {
        val psiFactory = KtPsiFactory(element.project)
        element.getQualifiedExpressionForSelectorOrThis().replace(
            psiFactory.createExpressionByPattern(
                "($0 == $1)",
                element.valueArguments[0].getArgumentExpression() ?: return,
                element.valueArguments[1].getArgumentExpression() ?: return
            )
        )
    }
}


internal class RemoveForExpressionLoopParameterTypeProcessing :
    InspectionLikeProcessingForElement<KtForExpression>(KtForExpression::class.java) {
    override fun isApplicableTo(element: KtForExpression, settings: ConverterSettings?): Boolean {
        val typeReference = element.loopParameter?.typeReference ?: return false
        return (typeReference.annotationEntries.isEmpty()
                && typeReference.typeElement != null
                && settings?.specifyLocalVariableTypeByDefault != true)
    }

    override fun apply(element: KtForExpression) {
        element.loopParameter?.typeReference = null
    }
}

internal class RemoveRedundantModalityModifierProcessing : InspectionLikeProcessingForElement<KtDeclaration>(KtDeclaration::class.java) {
    override fun isApplicableTo(element: KtDeclaration, settings: ConverterSettings?): Boolean {
        if (element.hasModifier(KtTokens.FINAL_KEYWORD)) {
            return !element.hasModifier(KtTokens.OVERRIDE_KEYWORD)
        }
        val modalityModifierType = element.modalityModifierType() ?: return false
        return modalityModifierType == element.implicitModality()
    }

    override fun apply(element: KtDeclaration) {
        element.removeModifier(element.modalityModifierType() ?: return)
    }
}


internal class RemoveRedundantVisibilityModifierProcessing : InspectionLikeProcessingForElement<KtDeclaration>(KtDeclaration::class.java) {
    override fun isApplicableTo(element: KtDeclaration, settings: ConverterSettings?) = when {
        element.hasModifier(KtTokens.PUBLIC_KEYWORD) && element.hasModifier(KtTokens.OVERRIDE_KEYWORD) ->
            false

        element.hasModifier(KtTokens.INTERNAL_KEYWORD) && element.containingClassOrObject?.isLocal == true ->
            true

        element.visibilityModifierType() == element.implicitVisibility() ->
            true

        else -> false
    }

    override fun apply(element: KtDeclaration) {
        element.removeModifier(element.visibilityModifierType() ?: return)
    }
}

internal class CanBeValInspectionBasedProcessing : InspectionLikeProcessingForElement<KtDeclaration>(KtDeclaration::class.java) {
    override fun isApplicableTo(element: KtDeclaration, settings: ConverterSettings?): Boolean =
        CanBeValInspection.canBeVal(element, ignoreNotUsedVals = false)

    override fun apply(element: KtDeclaration) {
        val project = element.project
        if (element !is KtValVarKeywordOwner) return
        element.valOrVarKeyword?.replace(KtPsiFactory(project).createValKeyword())
    }
}


internal class MayBeConstantInspectionBasedProcessing : InspectionLikeProcessingForElement<KtProperty>(KtProperty::class.java) {
    override fun isApplicableTo(element: KtProperty, settings: ConverterSettings?): Boolean =
        with(MayBeConstantInspection) {
            val status = element.getStatus()
            status == MayBeConstantInspection.Status.MIGHT_BE_CONST
                    || status == MayBeConstantInspection.Status.JVM_FIELD_MIGHT_BE_CONST
        }

    override fun apply(element: KtProperty) {
        AddConstModifierFix.addConstModifier(element)
    }
}

internal class RemoveExplicitGetterInspectionBasedProcessing :
    InspectionLikeProcessingForElement<KtPropertyAccessor>(KtPropertyAccessor::class.java) {
    override fun isApplicableTo(element: KtPropertyAccessor, settings: ConverterSettings?): Boolean =
        element.isRedundantGetter()

    override fun apply(element: KtPropertyAccessor) {
        removeRedundantGetter(element)
    }
}

internal class RemoveExplicitSetterInspectionBasedProcessing :
    InspectionLikeProcessingForElement<KtPropertyAccessor>(KtPropertyAccessor::class.java) {
    override fun isApplicableTo(element: KtPropertyAccessor, settings: ConverterSettings?): Boolean =
        element.isRedundantSetter()

    override fun apply(element: KtPropertyAccessor) {
        removeRedundantSetter(element)
    }
}

internal class ExplicitThisInspectionBasedProcessing :
    InspectionLikeProcessingForElement<KtExpression>(KtExpression::class.java) {
    override fun isApplicableTo(element: KtExpression, settings: ConverterSettings?): Boolean =
        ExplicitThisInspection.hasExplicitThis(element)

    override fun apply(element: KtExpression) {
        ExplicitThisExpressionFix.removeExplicitThisExpression(
            with(ExplicitThisInspection) {
                element.thisAsReceiverOrNull() ?: return
            }
        )
    }
}

internal class LiftReturnInspectionBasedProcessing :
    InspectionLikeProcessingForElement<KtExpression>(KtExpression::class.java) {
    override fun isApplicableTo(element: KtExpression, settings: ConverterSettings?): Boolean =
        LiftReturnOrAssignmentInspection.getState(element, false)?.any {
            it.liftType == LiftReturnOrAssignmentInspection.Companion.LiftType.LIFT_RETURN_OUT
        } ?: false

    override fun apply(element: KtExpression) {
        BranchedFoldingUtils.foldToReturn(element)
    }
}

internal class LiftAssignmentInspectionBasedProcessing :
    InspectionLikeProcessingForElement<KtExpression>(KtExpression::class.java) {
    override fun isApplicableTo(element: KtExpression, settings: ConverterSettings?): Boolean =
        LiftReturnOrAssignmentInspection.getState(element, false)?.any {
            it.liftType == LiftReturnOrAssignmentInspection.Companion.LiftType.LIFT_ASSIGNMENT_OUT
        } ?: false

    override fun apply(element: KtExpression) {
        BranchedFoldingUtils.tryFoldToAssignment(element)
    }
}

internal class MoveLambdaOutsideParenthesesProcessing :
    InspectionLikeProcessingForElement<KtCallExpression>(KtCallExpression::class.java) {
    override fun isApplicableTo(element: KtCallExpression, settings: ConverterSettings?): Boolean =
        element.canMoveLambdaOutsideParentheses()

    override fun apply(element: KtCallExpression) {
        element.moveFunctionLiteralOutsideParentheses()
    }
}

internal class RemoveOpenModifierOnTopLevelDeclarationsProcessing :
    InspectionLikeProcessingForElement<KtDeclaration>(KtDeclaration::class.java) {
    override fun isApplicableTo(element: KtDeclaration, settings: ConverterSettings?): Boolean =
        element.hasModifier(KtTokens.OPEN_KEYWORD)
                && (element is KtFunction || element is KtProperty)
                && element.parent is KtFile

    override fun apply(element: KtDeclaration) {
        element.removeModifier(KtTokens.OPEN_KEYWORD)
    }
}
