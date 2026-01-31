// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.uast.kotlin

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.FakeFileForLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightAnnotationForSourceEntry
import org.jetbrains.kotlin.asJava.elements.KtLightDeclaration
import org.jetbrains.kotlin.asJava.elements.KtLightElementBase
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.asJava.elements.KtLightFieldForSourceDeclarationSupport
import org.jetbrains.kotlin.asJava.elements.KtLightParameter
import org.jetbrains.kotlin.asJava.elements.KtLightPsiArrayInitializerMemberValue
import org.jetbrains.kotlin.asJava.elements.KtLightPsiLiteral
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtConstructorDelegationCall
import org.jetbrains.kotlin.psi.KtConstructorDelegationReferenceExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDelegatedSuperTypeEntry
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclarationEntry
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtEnumEntrySuperclassReferenceExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtObjectLiteralExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeCallEntry
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.kotlin.psi.KtWhenConditionIsPattern
import org.jetbrains.kotlin.psi.KtWhenConditionWithExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnnotationEx
import org.jetbrains.uast.UAnnotationMethod
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UBreakExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallExpressionEx
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UClassInitializerEx
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UComment
import org.jetbrains.uast.UContinueExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UDeclarationEx
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UEnumConstant
import org.jetbrains.uast.UEnumConstantEx
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UFieldEx
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UForEachExpression
import org.jetbrains.uast.UForExpression
import org.jetbrains.uast.UIdentifier
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UInstanceExpression
import org.jetbrains.uast.UJumpExpression
import org.jetbrains.uast.ULabeled
import org.jetbrains.uast.ULabeledExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.ULocalVariableEx
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UObjectLiteralExpression
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParameterEx
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UVariableEx
import org.jetbrains.uast.UWhileExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightAccessor
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightDefaultAccessor
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightMethod
import org.jetbrains.uast.kotlin.psi.UastFakeSourceLightPrimaryConstructor
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameter
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiParameterBase
import org.jetbrains.uast.kotlin.psi.UastKotlinPsiVariable
import org.jetbrains.uast.psi.UElementWithLocation
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.classSetOf
import org.jetbrains.uast.util.emptyClassSet

private val checkCanConvert = Registry.`is`("kotlin.uast.use.psi.type.precheck", true)

fun canConvert(element: PsiElement, targets: Array<out Class<out UElement>>): Boolean {
    if (!checkCanConvert) return true
    val psiElementClass = element.javaClass

    for (target in targets) {
        if (getPossibleSourceTypes(target).contains(psiElementClass)) {
            return true
        }
    }

    val ktOriginalCls = (element as? KtLightElementBase)?.kotlinOrigin?.javaClass ?: return false
    return targets.any { getPossibleSourceTypes(it).let { ktOriginalCls in it } }
}

fun getPossibleSourceTypes(uastType: Class<out UElement>): ClassSet<PsiElement> =
    possibleSourceTypes[uastType] ?: emptyClassSet()

/**
 * For every [UElement] subtype states from which [PsiElement] subtypes it can be obtained.
 *
 * This map is machine generated by `KotlinUastMappingsAccountantOverLargeProjectTest`
 */
@Suppress("DEPRECATION", "RemoveExplicitTypeArguments", "DuplicatedCode")
private val possibleSourceTypes = mapOf<Class<*>, ClassSet<PsiElement>>(
    UAnchorOwner::class.java to classSetOf<PsiElement>(
      KtAnnotationEntry::class.java,
      KtCallExpression::class.java,
      KtClass::class.java,
      KtDestructuringDeclarationEntry::class.java,
      KtEnumEntry::class.java,
      KtFile::class.java,
      KtLightAnnotationForSourceEntry::class.java,
      KtLightClass::class.java,
      KtLightDeclaration::class.java,
      KtLightField::class.java,
      KtLightFieldForSourceDeclarationSupport::class.java,
      KtLightParameter::class.java,
      KtNamedFunction::class.java,
      KtObjectDeclaration::class.java,
      KtParameter::class.java,
      KtPrimaryConstructor::class.java,
      KtProperty::class.java,
      KtPropertyAccessor::class.java,
      KtSecondaryConstructor::class.java,
      KtTypeReference::class.java,
      UastFakeSourceLightMethod::class.java,
      UastFakeSourceLightPrimaryConstructor::class.java,
      UastKotlinPsiParameter::class.java,
      UastKotlinPsiParameterBase::class.java,
      UastKotlinPsiVariable::class.java
    ),
    UAnnotated::class.java to classSetOf<PsiElement>(
      FakeFileForLightClass::class.java,
      KtAnnotatedExpression::class.java,
      KtArrayAccessExpression::class.java,
      KtBinaryExpression::class.java,
      KtBinaryExpressionWithTypeRHS::class.java,
      KtBlockExpression::class.java,
      KtBlockStringTemplateEntry::class.java,
      KtBreakExpression::class.java,
      KtCallExpression::class.java,
      KtCallableReferenceExpression::class.java,
      KtClass::class.java,
      KtClassBody::class.java,
      KtClassInitializer::class.java,
      KtClassLiteralExpression::class.java,
      KtCollectionLiteralExpression::class.java,
      KtConstantExpression::class.java,
      KtConstructorCalleeExpression::class.java,
      KtConstructorDelegationCall::class.java,
      KtConstructorDelegationReferenceExpression::class.java,
      KtContinueExpression::class.java,
      KtDelegatedSuperTypeEntry::class.java,
      KtDestructuringDeclaration::class.java,
      KtDestructuringDeclarationEntry::class.java,
      KtDoWhileExpression::class.java,
      KtDotQualifiedExpression::class.java,
      KtEnumEntry::class.java,
      KtEnumEntrySuperclassReferenceExpression::class.java,
      KtEscapeStringTemplateEntry::class.java,
      KtFile::class.java,
      KtForExpression::class.java,
      KtFunctionLiteral::class.java,
      KtIfExpression::class.java,
      KtIsExpression::class.java,
      KtLabelReferenceExpression::class.java,
      KtLabeledExpression::class.java,
      KtLambdaArgument::class.java,
      KtLambdaExpression::class.java,
      KtLightAnnotationForSourceEntry::class.java,
      KtLightClass::class.java,
      KtLightDeclaration::class.java,
      KtLightField::class.java,
      KtLightFieldForSourceDeclarationSupport::class.java,
      KtLightParameter::class.java,
      KtLightPsiArrayInitializerMemberValue::class.java,
      KtLightPsiLiteral::class.java,
      KtLiteralStringTemplateEntry::class.java,
      KtNameReferenceExpression::class.java,
      KtNamedFunction::class.java,
      KtObjectDeclaration::class.java,
      KtObjectLiteralExpression::class.java,
      KtOperationReferenceExpression::class.java,
      KtParameter::class.java,
      KtParameterList::class.java,
      KtParenthesizedExpression::class.java,
      KtPostfixExpression::class.java,
      KtPrefixExpression::class.java,
      KtPrimaryConstructor::class.java,
      KtProperty::class.java,
      KtPropertyAccessor::class.java,
      KtReturnExpression::class.java,
      KtSafeQualifiedExpression::class.java,
      KtScript::class.java,
      KtScriptInitializer::class.java,
      KtSecondaryConstructor::class.java,
      KtSimpleNameStringTemplateEntry::class.java,
      KtStringTemplateExpression::class.java,
      KtSuperExpression::class.java,
      KtSuperTypeCallEntry::class.java,
      KtThisExpression::class.java,
      KtThrowExpression::class.java,
      KtTryExpression::class.java,
      KtTypeAlias::class.java,
      KtTypeParameter::class.java,
      KtTypeReference::class.java,
      KtWhenConditionInRange::class.java,
      KtWhenConditionIsPattern::class.java,
      KtWhenConditionWithExpression::class.java,
      KtWhenEntry::class.java,
      KtWhenExpression::class.java,
      KtWhileExpression::class.java,
      UastFakeSourceLightMethod::class.java,
      UastFakeSourceLightPrimaryConstructor::class.java,
      UastKotlinPsiParameter::class.java,
      UastKotlinPsiParameterBase::class.java,
      UastKotlinPsiVariable::class.java
    ),
    UAnnotation::class.java to classSetOf<PsiElement>(
        KtAnnotationEntry::class.java,
        KtCallExpression::class.java,
        KtLightAnnotationForSourceEntry::class.java
    ),
    UAnnotationEx::class.java to classSetOf<PsiElement>(
        KtAnnotationEntry::class.java,
        KtCallExpression::class.java,
        KtLightAnnotationForSourceEntry::class.java
    ),
    UAnnotationMethod::class.java to classSetOf<PsiElement>(
        KtLightDeclaration::class.java,
        KtParameter::class.java
    ),
    UAnonymousClass::class.java to classSetOf<PsiElement>(
        KtLightClass::class.java,
        KtObjectDeclaration::class.java
    ),
    UArrayAccessExpression::class.java to classSetOf<PsiElement>(
        KtArrayAccessExpression::class.java,
        KtBlockStringTemplateEntry::class.java
    ),
    UBinaryExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBinaryExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtStringTemplateExpression::class.java,
        KtWhenConditionInRange::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UBinaryExpressionWithType::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBinaryExpressionWithTypeRHS::class.java,
        KtIsExpression::class.java,
        KtWhenConditionIsPattern::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UBlockExpression::class.java to classSetOf<PsiElement>(
        KtBlockExpression::class.java
    ),
    UBreakExpression::class.java to classSetOf<PsiElement>(
        KtBreakExpression::class.java
    ),
    UCallExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtCallExpression::class.java,
        KtCollectionLiteralExpression::class.java,
        KtConstructorDelegationCall::class.java,
        KtEnumEntry::class.java,
        KtLightAnnotationForSourceEntry::class.java,
        KtLightField::class.java,
        KtObjectLiteralExpression::class.java,
        KtStringTemplateExpression::class.java,
        KtSuperTypeCallEntry::class.java,
        KtWhenConditionWithExpression::class.java,
        KtLightPsiArrayInitializerMemberValue::class.java
    ),
    UCallExpressionEx::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtCallExpression::class.java,
        KtCollectionLiteralExpression::class.java,
        KtConstructorDelegationCall::class.java,
        KtEnumEntry::class.java,
        KtLightAnnotationForSourceEntry::class.java,
        KtLightField::class.java,
        KtObjectLiteralExpression::class.java,
        KtStringTemplateExpression::class.java,
        KtSuperTypeCallEntry::class.java,
        KtWhenConditionWithExpression::class.java,
        KtLightPsiArrayInitializerMemberValue::class.java
    ),
    UCallableReferenceExpression::class.java to classSetOf<PsiElement>(
        KtCallableReferenceExpression::class.java
    ),
    UCatchClause::class.java to classSetOf<PsiElement>(
        KtCatchClause::class.java
    ),
    UClass::class.java to classSetOf<PsiElement>(
        KtClass::class.java,
        KtFile::class.java,
        KtLightClass::class.java,
        KtObjectDeclaration::class.java
    ),
    UClassInitializer::class.java to classSetOf<PsiElement>(
    ),
    UClassInitializerEx::class.java to classSetOf<PsiElement>(
    ),
    UClassLiteralExpression::class.java to classSetOf<PsiElement>(
        KtClassLiteralExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UComment::class.java to classSetOf<PsiElement>(
        PsiComment::class.java
    ),
    UContinueExpression::class.java to classSetOf<PsiElement>(
        KtContinueExpression::class.java
    ),
    UDeclaration::class.java to classSetOf<PsiElement>(
      KtClass::class.java,
      KtDestructuringDeclarationEntry::class.java,
      KtEnumEntry::class.java,
      KtFile::class.java,
      KtLightClass::class.java,
      KtLightDeclaration::class.java,
      KtLightField::class.java,
      KtLightFieldForSourceDeclarationSupport::class.java,
      KtLightParameter::class.java,
      KtNamedFunction::class.java,
      KtObjectDeclaration::class.java,
      KtParameter::class.java,
      KtPrimaryConstructor::class.java,
      KtProperty::class.java,
      KtPropertyAccessor::class.java,
      KtSecondaryConstructor::class.java,
      KtTypeReference::class.java,
      UastFakeSourceLightMethod::class.java,
      UastFakeSourceLightPrimaryConstructor::class.java,
      UastKotlinPsiParameter::class.java,
      UastKotlinPsiParameterBase::class.java,
      UastKotlinPsiVariable::class.java
    ),
    UDeclarationEx::class.java to classSetOf<PsiElement>(
        KtDestructuringDeclarationEntry::class.java,
        KtEnumEntry::class.java,
        KtLightField::class.java,
        KtLightFieldForSourceDeclarationSupport::class.java,
        KtLightParameter::class.java,
        KtParameter::class.java,
        KtProperty::class.java,
        KtTypeReference::class.java,
        UastKotlinPsiParameter::class.java,
        UastKotlinPsiParameterBase::class.java,
        UastKotlinPsiVariable::class.java
    ),
    UDeclarationsExpression::class.java to classSetOf<PsiElement>(
        KtClass::class.java,
        KtDestructuringDeclaration::class.java,
        KtEnumEntry::class.java,
        KtFunctionLiteral::class.java,
        KtLightDeclaration::class.java,
        KtNamedFunction::class.java,
        KtObjectDeclaration::class.java,
        KtParameterList::class.java,
        KtPrimaryConstructor::class.java,
        KtSecondaryConstructor::class.java
    ),
    UDoWhileExpression::class.java to classSetOf<PsiElement>(
        KtDoWhileExpression::class.java
    ),
    UElement::class.java to classSetOf<PsiElement>(
      FakeFileForLightClass::class.java,
      KDocName::class.java,
      KtAnnotatedExpression::class.java,
      KtAnnotationEntry::class.java,
      KtArrayAccessExpression::class.java,
      KtBinaryExpression::class.java,
      KtBinaryExpressionWithTypeRHS::class.java,
      KtBlockExpression::class.java,
      KtBlockStringTemplateEntry::class.java,
      KtBreakExpression::class.java,
      KtCallExpression::class.java,
      KtCallableReferenceExpression::class.java,
      KtCatchClause::class.java,
      KtClass::class.java,
      KtClassBody::class.java,
      KtClassInitializer::class.java,
      KtClassLiteralExpression::class.java,
      KtCollectionLiteralExpression::class.java,
      KtConstantExpression::class.java,
      KtConstructorCalleeExpression::class.java,
      KtConstructorDelegationCall::class.java,
      KtConstructorDelegationReferenceExpression::class.java,
      KtContinueExpression::class.java,
      KtDelegatedSuperTypeEntry::class.java,
      KtDestructuringDeclaration::class.java,
      KtDestructuringDeclarationEntry::class.java,
      KtDoWhileExpression::class.java,
      KtDotQualifiedExpression::class.java,
      KtEnumEntry::class.java,
      KtEnumEntrySuperclassReferenceExpression::class.java,
      KtEscapeStringTemplateEntry::class.java,
      KtFile::class.java,
      KtForExpression::class.java,
      KtFunctionLiteral::class.java,
      KtIfExpression::class.java,
      KtImportDirective::class.java,
      KtIsExpression::class.java,
      KtLabelReferenceExpression::class.java,
      KtLabeledExpression::class.java,
      KtLambdaArgument::class.java,
      KtLambdaExpression::class.java,
      KtLightAnnotationForSourceEntry::class.java,
      KtLightClass::class.java,
      KtLightDeclaration::class.java,
      KtLightField::class.java,
      KtLightFieldForSourceDeclarationSupport::class.java,
      KtLightParameter::class.java,
      KtLightPsiArrayInitializerMemberValue::class.java,
      KtLightPsiLiteral::class.java,
      KtLiteralStringTemplateEntry::class.java,
      KtNameReferenceExpression::class.java,
      KtNamedFunction::class.java,
      KtObjectDeclaration::class.java,
      KtObjectLiteralExpression::class.java,
      KtOperationReferenceExpression::class.java,
      KtParameter::class.java,
      KtParameterList::class.java,
      KtParenthesizedExpression::class.java,
      KtPostfixExpression::class.java,
      KtPrefixExpression::class.java,
      KtPrimaryConstructor::class.java,
      KtProperty::class.java,
      KtPropertyAccessor::class.java,
      KtReturnExpression::class.java,
      KtSafeQualifiedExpression::class.java,
      KtScript::class.java,
      KtScriptInitializer::class.java,
      KtSecondaryConstructor::class.java,
      KtSimpleNameStringTemplateEntry::class.java,
      KtStringTemplateExpression::class.java,
      KtSuperExpression::class.java,
      KtSuperTypeCallEntry::class.java,
      KtThisExpression::class.java,
      KtThrowExpression::class.java,
      KtTryExpression::class.java,
      KtTypeAlias::class.java,
      KtTypeParameter::class.java,
      KtTypeReference::class.java,
      KtWhenConditionInRange::class.java,
      KtWhenConditionIsPattern::class.java,
      KtWhenConditionWithExpression::class.java,
      KtWhenEntry::class.java,
      KtWhenExpression::class.java,
      KtWhileExpression::class.java,
      LeafPsiElement::class.java,
      PsiComment::class.java,
      UastFakeSourceLightMethod::class.java,
      UastFakeSourceLightPrimaryConstructor::class.java,
      UastKotlinPsiParameter::class.java,
      UastKotlinPsiParameterBase::class.java,
      UastKotlinPsiVariable::class.java
    ),
    UElementWithLocation::class.java to classSetOf<PsiElement>(
    ),
    UEnumConstant::class.java to classSetOf<PsiElement>(
        KtEnumEntry::class.java,
        KtLightField::class.java
    ),
    UEnumConstantEx::class.java to classSetOf<PsiElement>(
        KtEnumEntry::class.java,
        KtLightField::class.java
    ),
    UExpression::class.java to classSetOf<PsiElement>(
        KDocName::class.java,
        KtAnnotatedExpression::class.java,
        KtArrayAccessExpression::class.java,
        KtBinaryExpression::class.java,
        KtBinaryExpressionWithTypeRHS::class.java,
        KtBlockExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtBreakExpression::class.java,
        KtCallExpression::class.java,
        KtCallableReferenceExpression::class.java,
        KtClass::class.java,
        KtClassBody::class.java,
        KtClassInitializer::class.java,
        KtClassLiteralExpression::class.java,
        KtCollectionLiteralExpression::class.java,
        KtConstantExpression::class.java,
        KtConstructorCalleeExpression::class.java,
        KtConstructorDelegationCall::class.java,
        KtConstructorDelegationReferenceExpression::class.java,
        KtContinueExpression::class.java,
        KtDelegatedSuperTypeEntry::class.java,
        KtDestructuringDeclaration::class.java,
        KtDoWhileExpression::class.java,
        KtDotQualifiedExpression::class.java,
        KtEnumEntry::class.java,
        KtEnumEntrySuperclassReferenceExpression::class.java,
        KtEscapeStringTemplateEntry::class.java,
        KtForExpression::class.java,
        KtFunctionLiteral::class.java,
        KtIfExpression::class.java,
        KtIsExpression::class.java,
        KtLabelReferenceExpression::class.java,
        KtLabeledExpression::class.java,
        KtLambdaArgument::class.java,
        KtLambdaExpression::class.java,
        KtLightAnnotationForSourceEntry::class.java,
        KtLightDeclaration::class.java,
        KtLightField::class.java,
        KtLightPsiArrayInitializerMemberValue::class.java,
        KtLightPsiLiteral::class.java,
        KtLiteralStringTemplateEntry::class.java,
        KtNameReferenceExpression::class.java,
        KtNamedFunction::class.java,
        KtObjectDeclaration::class.java,
        KtObjectLiteralExpression::class.java,
        KtOperationReferenceExpression::class.java,
        KtParameter::class.java,
        KtParameterList::class.java,
        KtParenthesizedExpression::class.java,
        KtPostfixExpression::class.java,
        KtPrefixExpression::class.java,
        KtPrimaryConstructor::class.java,
        KtProperty::class.java,
        KtPropertyAccessor::class.java,
        KtReturnExpression::class.java,
        KtSafeQualifiedExpression::class.java,
        KtScript::class.java,
        KtScriptInitializer::class.java,
        KtSecondaryConstructor::class.java,
        KtSimpleNameStringTemplateEntry::class.java,
        KtStringTemplateExpression::class.java,
        KtSuperExpression::class.java,
        KtSuperTypeCallEntry::class.java,
        KtThisExpression::class.java,
        KtThrowExpression::class.java,
        KtTryExpression::class.java,
        KtTypeAlias::class.java,
        KtTypeParameter::class.java,
        KtTypeReference::class.java,
        KtWhenConditionInRange::class.java,
        KtWhenConditionIsPattern::class.java,
        KtWhenConditionWithExpression::class.java,
        KtWhenEntry::class.java,
        KtWhenExpression::class.java,
        KtWhileExpression::class.java
    ),
    UExpressionList::class.java to classSetOf<PsiElement>(
        KtBinaryExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtClassBody::class.java,
        KtDelegatedSuperTypeEntry::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UField::class.java to classSetOf<PsiElement>(
        KtEnumEntry::class.java,
        KtLightField::class.java,
        KtLightFieldForSourceDeclarationSupport::class.java,
        KtParameter::class.java,
        KtProperty::class.java
    ),
    UFieldEx::class.java to classSetOf<PsiElement>(
        KtLightFieldForSourceDeclarationSupport::class.java,
        KtParameter::class.java,
        KtProperty::class.java
    ),
    UFile::class.java to classSetOf<PsiElement>(
        FakeFileForLightClass::class.java,
        KtFile::class.java
    ),
    UForEachExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtForExpression::class.java
    ),
    UForExpression::class.java to classSetOf<PsiElement>(
    ),
    UIdentifier::class.java to classSetOf<PsiElement>(
        LeafPsiElement::class.java
    ),
    UIfExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtIfExpression::class.java
    ),
    UImportStatement::class.java to classSetOf<PsiElement>(
        KtImportDirective::class.java
    ),
    UInjectionHost::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtLightPsiArrayInitializerMemberValue::class.java,
        KtLightPsiLiteral::class.java,
        KtStringTemplateExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UInstanceExpression::class.java to classSetOf<PsiElement>(
        KtBlockStringTemplateEntry::class.java,
        KtSimpleNameStringTemplateEntry::class.java,
        KtSuperExpression::class.java,
        KtThisExpression::class.java
    ),
    UJumpExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBreakExpression::class.java,
        KtContinueExpression::class.java,
        KtReturnExpression::class.java
    ),
    ULabeled::class.java to classSetOf<PsiElement>(
        KtBlockStringTemplateEntry::class.java,
        KtLabeledExpression::class.java,
        KtSimpleNameStringTemplateEntry::class.java,
        KtSuperExpression::class.java,
        KtThisExpression::class.java
    ),
    ULabeledExpression::class.java to classSetOf<PsiElement>(
        KtLabeledExpression::class.java
    ),
    ULambdaExpression::class.java to classSetOf<PsiElement>(
        KtFunctionLiteral::class.java,
        KtLambdaArgument::class.java,
        KtLambdaExpression::class.java,
        KtLightDeclaration::class.java,
        KtNamedFunction::class.java,
        KtPrimaryConstructor::class.java
    ),
    ULiteralExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtConstantExpression::class.java,
        KtEscapeStringTemplateEntry::class.java,
        KtLightPsiArrayInitializerMemberValue::class.java,
        KtLightPsiLiteral::class.java,
        KtLiteralStringTemplateEntry::class.java,
        KtStringTemplateExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    ULocalVariable::class.java to classSetOf<PsiElement>(
        KtDestructuringDeclarationEntry::class.java,
        KtProperty::class.java,
        UastKotlinPsiVariable::class.java
    ),
    ULocalVariableEx::class.java to classSetOf<PsiElement>(
        KtDestructuringDeclarationEntry::class.java,
        KtProperty::class.java,
        UastKotlinPsiVariable::class.java
    ),
    ULoopExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtDoWhileExpression::class.java,
        KtForExpression::class.java,
        KtWhileExpression::class.java
    ),
    UMethod::class.java to classSetOf<PsiElement>(
      KtClass::class.java,
      KtLightDeclaration::class.java,
      KtNamedFunction::class.java,
      KtParameter::class.java,
      KtPrimaryConstructor::class.java,
      KtProperty::class.java,
      KtPropertyAccessor::class.java,
      KtSecondaryConstructor::class.java,
      UastFakeSourceLightAccessor::class.java,
      UastFakeSourceLightDefaultAccessor::class.java,
      UastFakeSourceLightMethod::class.java,
      UastFakeSourceLightPrimaryConstructor::class.java
    ),
    UMultiResolvable::class.java to classSetOf<PsiElement>(
        KDocName::class.java,
        KtAnnotatedExpression::class.java,
        KtAnnotationEntry::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtCallExpression::class.java,
        KtCallableReferenceExpression::class.java,
        KtCollectionLiteralExpression::class.java,
        KtConstructorDelegationCall::class.java,
        KtDotQualifiedExpression::class.java,
        KtEnumEntry::class.java,
        KtImportDirective::class.java,
        KtLightAnnotationForSourceEntry::class.java,
        KtLightField::class.java,
        KtObjectLiteralExpression::class.java,
        KtPostfixExpression::class.java,
        KtSafeQualifiedExpression::class.java,
        KtSimpleNameStringTemplateEntry::class.java,
        KtStringTemplateExpression::class.java,
        KtSuperExpression::class.java,
        KtSuperTypeCallEntry::class.java,
        KtThisExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UNamedExpression::class.java to classSetOf<PsiElement>(
    ),
    UObjectLiteralExpression::class.java to classSetOf<PsiElement>(
        KtObjectLiteralExpression::class.java,
        KtSuperTypeCallEntry::class.java
    ),
    UParameter::class.java to classSetOf<PsiElement>(
        KtLightParameter::class.java,
        KtParameter::class.java,
        KtTypeReference::class.java,
        UastKotlinPsiParameter::class.java,
        UastKotlinPsiParameterBase::class.java
    ),
    UParameterEx::class.java to classSetOf<PsiElement>(
        KtLightParameter::class.java,
        KtParameter::class.java,
        KtTypeReference::class.java,
        UastKotlinPsiParameter::class.java,
        UastKotlinPsiParameterBase::class.java
    ),
    UParenthesizedExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtParenthesizedExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UPolyadicExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBinaryExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtLightPsiArrayInitializerMemberValue::class.java,
        KtLightPsiLiteral::class.java,
        KtStringTemplateExpression::class.java,
        KtWhenConditionInRange::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UPostfixExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtPostfixExpression::class.java
    ),
    UPrefixExpression::class.java to classSetOf<PsiElement>(
        KtBlockStringTemplateEntry::class.java,
        KtPrefixExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UQualifiedReferenceExpression::class.java to classSetOf<PsiElement>(
        KDocName::class.java,
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtDotQualifiedExpression::class.java,
        KtSafeQualifiedExpression::class.java,
        KtStringTemplateExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UReferenceExpression::class.java to classSetOf<PsiElement>(
        KDocName::class.java,
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtCallableReferenceExpression::class.java,
        KtDotQualifiedExpression::class.java,
        KtEnumEntrySuperclassReferenceExpression::class.java,
        KtLabelReferenceExpression::class.java,
        KtNameReferenceExpression::class.java,
        KtOperationReferenceExpression::class.java,
        KtSafeQualifiedExpression::class.java,
        KtSimpleNameStringTemplateEntry::class.java,
        KtStringTemplateExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UResolvable::class.java to classSetOf<PsiElement>(
        KDocName::class.java,
        KtAnnotatedExpression::class.java,
        KtAnnotationEntry::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtCallExpression::class.java,
        KtCallableReferenceExpression::class.java,
        KtCollectionLiteralExpression::class.java,
        KtConstructorDelegationCall::class.java,
        KtDotQualifiedExpression::class.java,
        KtEnumEntry::class.java,
        KtEnumEntrySuperclassReferenceExpression::class.java,
        KtImportDirective::class.java,
        KtLabelReferenceExpression::class.java,
        KtLightAnnotationForSourceEntry::class.java,
        KtLightField::class.java,
        KtNameReferenceExpression::class.java,
        KtObjectLiteralExpression::class.java,
        KtOperationReferenceExpression::class.java,
        KtPostfixExpression::class.java,
        KtSafeQualifiedExpression::class.java,
        KtSimpleNameStringTemplateEntry::class.java,
        KtStringTemplateExpression::class.java,
        KtSuperExpression::class.java,
        KtSuperTypeCallEntry::class.java,
        KtThisExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UReturnExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtReturnExpression::class.java
    ),
    USimpleNameReferenceExpression::class.java to classSetOf<PsiElement>(
        KDocName::class.java,
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtEnumEntrySuperclassReferenceExpression::class.java,
        KtLabelReferenceExpression::class.java,
        KtNameReferenceExpression::class.java,
        KtOperationReferenceExpression::class.java,
        KtSimpleNameStringTemplateEntry::class.java,
        KtStringTemplateExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    USuperExpression::class.java to classSetOf<PsiElement>(
        KtSuperExpression::class.java
    ),
    USwitchClauseExpression::class.java to classSetOf<PsiElement>(
        KtWhenEntry::class.java
    ),
    USwitchClauseExpressionWithBody::class.java to classSetOf<PsiElement>(
        KtWhenEntry::class.java
    ),
    USwitchExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtWhenExpression::class.java
    ),
    UThisExpression::class.java to classSetOf<PsiElement>(
        KtBlockStringTemplateEntry::class.java,
        KtSimpleNameStringTemplateEntry::class.java,
        KtThisExpression::class.java
    ),
    UThrowExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtThrowExpression::class.java
    ),
    UTryExpression::class.java to classSetOf<PsiElement>(
        KtBlockStringTemplateEntry::class.java,
        KtTryExpression::class.java
    ),
    UTypeReferenceExpression::class.java to classSetOf<PsiElement>(
        KtTypeReference::class.java
    ),
    UUnaryExpression::class.java to classSetOf<PsiElement>(
        KtAnnotatedExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtPostfixExpression::class.java,
        KtPrefixExpression::class.java,
        KtWhenConditionWithExpression::class.java
    ),
    UVariable::class.java to classSetOf<PsiElement>(
        KtDestructuringDeclarationEntry::class.java,
        KtEnumEntry::class.java,
        KtLightField::class.java,
        KtLightFieldForSourceDeclarationSupport::class.java,
        KtLightParameter::class.java,
        KtParameter::class.java,
        KtProperty::class.java,
        KtTypeReference::class.java,
        UastKotlinPsiParameter::class.java,
        UastKotlinPsiParameterBase::class.java,
        UastKotlinPsiVariable::class.java
    ),
    UVariableEx::class.java to classSetOf<PsiElement>(
        KtDestructuringDeclarationEntry::class.java,
        KtEnumEntry::class.java,
        KtLightField::class.java,
        KtLightFieldForSourceDeclarationSupport::class.java,
        KtLightParameter::class.java,
        KtParameter::class.java,
        KtProperty::class.java,
        KtTypeReference::class.java,
        UastKotlinPsiParameter::class.java,
        UastKotlinPsiParameterBase::class.java,
        UastKotlinPsiVariable::class.java
    ),
    UWhileExpression::class.java to classSetOf<PsiElement>(
        KtWhileExpression::class.java
    ),
    UYieldExpression::class.java to classSetOf<PsiElement>(
    ),
    UastEmptyExpression::class.java to classSetOf<PsiElement>(
        KtBinaryExpression::class.java,
        KtBlockStringTemplateEntry::class.java,
        KtClass::class.java,
        KtEnumEntry::class.java,
        KtLightAnnotationForSourceEntry::class.java,
        KtObjectDeclaration::class.java,
        KtStringTemplateExpression::class.java
    ),
    UnknownKotlinExpression::class.java to classSetOf<PsiElement>(
        KtClassInitializer::class.java,
        KtConstructorCalleeExpression::class.java,
        KtConstructorDelegationReferenceExpression::class.java,
        KtLightDeclaration::class.java,
        KtParameter::class.java,
        KtPropertyAccessor::class.java,
        KtScript::class.java,
        KtScriptInitializer::class.java,
        KtTypeAlias::class.java,
        KtTypeParameter::class.java
    )
)

