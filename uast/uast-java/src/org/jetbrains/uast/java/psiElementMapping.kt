// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.psi.PsiAnnotatedJavaCodeReferenceElement
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiArrayAccessExpression
import com.intellij.psi.PsiArrayInitializerExpression
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiAssertStatement
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiBinaryExpression
import com.intellij.psi.PsiBlockStatement
import com.intellij.psi.PsiBreakStatement
import com.intellij.psi.PsiCatchSection
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiClassObjectAccessExpression
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiConditionalExpression
import com.intellij.psi.PsiContinueStatement
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiDeconstructionPattern
import com.intellij.psi.PsiDoWhileStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEmptyStatement
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.PsiEnumConstantInitializer
import com.intellij.psi.PsiExpressionListStatement
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiForStatement
import com.intellij.psi.PsiForeachStatement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiIfStatement
import com.intellij.psi.PsiImportStatement
import com.intellij.psi.PsiImportStaticReferenceElement
import com.intellij.psi.PsiImportStaticStatement
import com.intellij.psi.PsiInstanceOfExpression
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiJavaModuleReferenceElement
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiLabeledStatement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiPatternVariable
import com.intellij.psi.PsiPolyadicExpression
import com.intellij.psi.PsiPostfixExpression
import com.intellij.psi.PsiPrefixExpression
import com.intellij.psi.PsiRecordComponent
import com.intellij.psi.PsiRecordHeader
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiResourceVariable
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.PsiSwitchExpression
import com.intellij.psi.PsiSwitchLabelStatement
import com.intellij.psi.PsiSwitchLabeledRuleStatement
import com.intellij.psi.PsiSwitchStatement
import com.intellij.psi.PsiSynchronizedStatement
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.PsiThrowStatement
import com.intellij.psi.PsiTryStatement
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.PsiTypeTestPattern
import com.intellij.psi.PsiUnnamedPattern
import com.intellij.psi.PsiWhileStatement
import com.intellij.psi.PsiYieldStatement
import com.intellij.psi.impl.light.LightRecordField
import com.intellij.psi.impl.source.PsiExtensibleClass
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocToken
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
import org.jetbrains.uast.UPatternExpression
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
import org.jetbrains.uast.psi.UElementWithLocation
import org.jetbrains.uast.util.ClassSet
import org.jetbrains.uast.util.classSetOf
import org.jetbrains.uast.util.emptyClassSet

internal fun canConvert(psiCls: Class<out PsiElement>, targets: Array<out Class<out UElement>>): Boolean {
  if (targets.size == 1) {
    val target = targets.single()
    return canConvert(psiCls, target)
  }

  return targets.any { getPossibleSourceTypes(it).contains(psiCls) }
}

internal fun canConvert(psiCls: Class<out PsiElement>, target: Class<out UElement>): Boolean {
  // checking the most popular cases before looking up in hashtable
  when (target) {
    UElement::class.java -> return uElementClassSet.contains(psiCls)
    UInjectionHost::class.java -> return uInjectionHostClassSet.contains(psiCls)
    UCallExpression::class.java -> return uCallClassSet.contains(psiCls)
  }
  return getPossibleSourceTypes(target).contains(psiCls)
}

internal fun getPossibleSourceTypes(uastType: Class<out UElement>): ClassSet<PsiElement> =
  possibleSourceTypes[uastType] ?: emptyClassSet()

/**
 * For every [UElement] subtype states from which [PsiElement] subtypes it can be obtained.
 *
 * This map is machine generated by `JavaUastMappingsAccountantOverLargeProjectTest`
 */
@Suppress("DEPRECATION", "RemoveExplicitTypeArguments")
private val possibleSourceTypes = mapOf<Class<*>, ClassSet<PsiElement>>(
  UAnchorOwner::class.java to classSetOf(
    PsiAnnotation::class.java,
    PsiAnnotationMethod::class.java,
    PsiAnonymousClass::class.java,
    PsiClassInitializer::class.java,
    PsiEnumConstant::class.java,
    PsiEnumConstantInitializer::class.java,
    PsiExtensibleClass::class.java,
    PsiField::class.java,
    PsiLocalVariable::class.java,
    PsiMethod::class.java,
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    PsiResourceVariable::class.java,
    PsiTypeParameter::class.java,
    PsiRecordComponent::class.java
  ),
  UAnnotated::class.java to classSetOf(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiAnnotation::class.java,
    PsiAnnotationMethod::class.java,
    PsiAnonymousClass::class.java,
    PsiArrayAccessExpression::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiAssignmentExpression::class.java,
    PsiBinaryExpression::class.java,
    PsiBlockStatement::class.java,
    PsiBreakStatement::class.java,
    PsiClassInitializer::class.java,
    PsiClassObjectAccessExpression::class.java,
    PsiCodeBlock::class.java,
    PsiConditionalExpression::class.java,
    PsiContinueStatement::class.java,
    PsiDeclarationStatement::class.java,
    PsiDoWhileStatement::class.java,
    PsiDocToken::class.java,
    PsiEmptyStatement::class.java,
    PsiEnumConstant::class.java,
    PsiEnumConstantInitializer::class.java,
    PsiExpressionListStatement::class.java,
    PsiExpressionStatement::class.java,
    PsiExtensibleClass::class.java,
    PsiField::class.java,
    PsiForStatement::class.java,
    PsiForeachStatement::class.java,
    PsiIfStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiInstanceOfExpression::class.java,
    PsiJavaFile::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiLabeledStatement::class.java,
    PsiLambdaExpression::class.java,
    PsiLiteralExpression::class.java,
    PsiLocalVariable::class.java,
    PsiMethod::class.java,
    PsiMethodCallExpression::class.java,
    PsiMethodReferenceExpression::class.java,
    PsiNameValuePair::class.java,
    PsiNewExpression::class.java,
    PsiParameter::class.java,
    PsiParenthesizedExpression::class.java,
    PsiPatternVariable::class.java,
    PsiPolyadicExpression::class.java,
    PsiPostfixExpression::class.java,
    PsiPrefixExpression::class.java,
    PsiReferenceExpression::class.java,
    PsiResourceVariable::class.java,
    PsiReturnStatement::class.java,
    PsiSuperExpression::class.java,
    PsiSwitchExpression::class.java,
    PsiSwitchLabelStatement::class.java,
    PsiSwitchLabeledRuleStatement::class.java,
    PsiSwitchStatement::class.java,
    PsiSynchronizedStatement::class.java,
    PsiThisExpression::class.java,
    PsiThrowStatement::class.java,
    PsiTryStatement::class.java,
    PsiTypeCastExpression::class.java,
    PsiTypeElement::class.java,
    PsiTypeParameter::class.java,
    PsiWhileStatement::class.java,
    PsiYieldStatement::class.java,
    PsiRecordComponent::class.java,
    PsiRecordHeader::class.java,
    PsiUnnamedPattern::class.java,
    PsiTypeTestPattern::class.java,
    PsiDeconstructionPattern::class.java,
    PsiPatternVariable::class.java
  ),
  UAnnotation::class.java to classSetOf<PsiElement>(
    PsiAnnotation::class.java
  ),
  UAnnotationEx::class.java to classSetOf<PsiElement>(
    PsiAnnotation::class.java
  ),
  UAnnotationMethod::class.java to classSetOf<PsiElement>(
    PsiAnnotationMethod::class.java
  ),
  UAnonymousClass::class.java to classSetOf<PsiElement>(
    PsiAnonymousClass::class.java,
    PsiEnumConstantInitializer::class.java
  ),
  UArrayAccessExpression::class.java to classSetOf<PsiElement>(
    PsiArrayAccessExpression::class.java
    //PsiExpressionStatement::class.java
  ),
  UBinaryExpression::class.java to classSetOf<PsiElement>(
    PsiAssignmentExpression::class.java,
    PsiBinaryExpression::class.java
    //PsiExpressionStatement::class.java
  ),
  UBinaryExpressionWithType::class.java to classSetOf<PsiElement>(
    PsiInstanceOfExpression::class.java,
    PsiTypeCastExpression::class.java
  ),
  UBlockExpression::class.java to classSetOf<PsiElement>(
    PsiBlockStatement::class.java,
    PsiCodeBlock::class.java,
    PsiSynchronizedStatement::class.java
  ),
  UBreakExpression::class.java to classSetOf<PsiElement>(
    PsiBreakStatement::class.java
  ),
  UCallExpression::class.java to classSetOf<PsiElement>(
    PsiAnnotation::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiEnumConstant::class.java,
    //PsiExpressionStatement::class.java,
    PsiMethodCallExpression::class.java,
    PsiNewExpression::class.java
  ),
  UCallExpressionEx::class.java to classSetOf<PsiElement>(
    PsiAnnotation::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiEnumConstant::class.java,
    //PsiExpressionStatement::class.java,
    PsiMethodCallExpression::class.java,
    PsiNewExpression::class.java
  ),
  UCallableReferenceExpression::class.java to classSetOf<PsiElement>(
    PsiMethodReferenceExpression::class.java
  ),
  UCatchClause::class.java to classSetOf<PsiElement>(
    PsiCatchSection::class.java
  ),
  UClass::class.java to classSetOf<PsiElement>(
    PsiAnonymousClass::class.java,
    PsiEnumConstantInitializer::class.java,
    PsiExtensibleClass::class.java,
    PsiTypeParameter::class.java
  ),
  UClassInitializer::class.java to classSetOf<PsiElement>(
    PsiClassInitializer::class.java
  ),
  UClassInitializerEx::class.java to classSetOf<PsiElement>(
    PsiClassInitializer::class.java
  ),
  UClassLiteralExpression::class.java to classSetOf<PsiElement>(
    PsiClassObjectAccessExpression::class.java
  ),
  UComment::class.java to classSetOf<PsiElement>(
    PsiComment::class.java,
    PsiDocComment::class.java
  ),
  UContinueExpression::class.java to classSetOf<PsiElement>(
    PsiContinueStatement::class.java
  ),
  UDeclaration::class.java to classSetOf<PsiElement>(
    PsiAnnotationMethod::class.java,
    PsiAnonymousClass::class.java,
    PsiClassInitializer::class.java,
    PsiEnumConstant::class.java,
    PsiEnumConstantInitializer::class.java,
    PsiExtensibleClass::class.java,
    PsiField::class.java,
    PsiLocalVariable::class.java,
    PsiMethod::class.java,
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    PsiResourceVariable::class.java,
    PsiTypeParameter::class.java,
    PsiRecordComponent::class.java,
    PsiRecordHeader::class.java,
    PsiPatternVariable::class.java
  ),
  UDeclarationEx::class.java to classSetOf<PsiElement>(
    PsiAnnotationMethod::class.java,
    PsiAnonymousClass::class.java,
    PsiClassInitializer::class.java,
    PsiEnumConstant::class.java,
    PsiEnumConstantInitializer::class.java,
    PsiExtensibleClass::class.java,
    PsiField::class.java,
    PsiLocalVariable::class.java,
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    PsiResourceVariable::class.java,
    PsiTypeParameter::class.java,
    PsiRecordComponent::class.java,
    PsiRecordHeader::class.java,
    PsiPatternVariable::class.java
  ),
  UDeclarationsExpression::class.java to classSetOf<PsiElement>(
    PsiDeclarationStatement::class.java,
    PsiExpressionListStatement::class.java
  ),
  UDoWhileExpression::class.java to classSetOf<PsiElement>(
    PsiDoWhileStatement::class.java
  ),
  UElement::class.java to classSetOf<PsiElement>(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiAnnotation::class.java,
    PsiAnnotationMethod::class.java,
    PsiAnonymousClass::class.java,
    PsiArrayAccessExpression::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiAssignmentExpression::class.java,
    PsiBinaryExpression::class.java,
    PsiBlockStatement::class.java,
    PsiBreakStatement::class.java,
    PsiCatchSection::class.java,
    PsiClassInitializer::class.java,
    PsiClassObjectAccessExpression::class.java,
    PsiCodeBlock::class.java,
    PsiComment::class.java,
    PsiConditionalExpression::class.java,
    PsiContinueStatement::class.java,
    PsiDeclarationStatement::class.java,
    PsiDoWhileStatement::class.java,
    PsiDocComment::class.java,
    PsiDocToken::class.java,
    PsiEmptyStatement::class.java,
    PsiEnumConstant::class.java,
    PsiEnumConstantInitializer::class.java,
    PsiExpressionListStatement::class.java,
    PsiExpressionStatement::class.java,
    PsiExtensibleClass::class.java,
    PsiField::class.java,
    PsiForStatement::class.java,
    PsiForeachStatement::class.java,
    PsiIdentifier::class.java,
    PsiIfStatement::class.java,
    PsiImportStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiImportStaticStatement::class.java,
    PsiInstanceOfExpression::class.java,
    PsiJavaFile::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiKeyword::class.java,
    PsiLabeledStatement::class.java,
    PsiLambdaExpression::class.java,
    PsiLiteralExpression::class.java,
    PsiLocalVariable::class.java,
    PsiMethod::class.java,
    PsiMethodCallExpression::class.java,
    PsiMethodReferenceExpression::class.java,
    PsiNameValuePair::class.java,
    PsiNewExpression::class.java,
    PsiParameter::class.java,
    PsiParenthesizedExpression::class.java,
    PsiPatternVariable::class.java,
    PsiPolyadicExpression::class.java,
    PsiPostfixExpression::class.java,
    PsiPrefixExpression::class.java,
    PsiReferenceExpression::class.java,
    PsiResourceVariable::class.java,
    PsiReturnStatement::class.java,
    PsiSuperExpression::class.java,
    PsiSwitchExpression::class.java,
    PsiSwitchLabelStatement::class.java,
    PsiSwitchLabeledRuleStatement::class.java,
    PsiSwitchStatement::class.java,
    PsiSynchronizedStatement::class.java,
    PsiThisExpression::class.java,
    PsiThrowStatement::class.java,
    PsiTryStatement::class.java,
    PsiTypeCastExpression::class.java,
    PsiTypeElement::class.java,
    PsiTypeParameter::class.java,
    PsiWhileStatement::class.java,
    PsiYieldStatement::class.java,
    PsiRecordComponent::class.java,
    PsiRecordHeader::class.java,
    PsiUnnamedPattern::class.java,
    PsiTypeTestPattern::class.java,
    PsiDeconstructionPattern::class.java,
    PsiPatternVariable::class.java
  ),
  UElementWithLocation::class.java to classSetOf<PsiElement>(
    //PsiExpressionStatement::class.java,
    PsiMethodCallExpression::class.java
  ),
  UEnumConstant::class.java to classSetOf<PsiElement>(
    PsiEnumConstant::class.java
  ),
  UEnumConstantEx::class.java to classSetOf<PsiElement>(
    PsiEnumConstant::class.java
  ),
  UPatternExpression::class.java to classSetOf<PsiElement>(
    PsiUnnamedPattern::class.java,
    PsiTypeTestPattern::class.java,
    PsiDeconstructionPattern::class.java
  ),
  UExpression::class.java to classSetOf<PsiElement>(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiAnnotation::class.java,
    PsiArrayAccessExpression::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiAssignmentExpression::class.java,
    PsiBinaryExpression::class.java,
    PsiBlockStatement::class.java,
    PsiBreakStatement::class.java,
    PsiClassObjectAccessExpression::class.java,
    PsiCodeBlock::class.java,
    PsiConditionalExpression::class.java,
    PsiContinueStatement::class.java,
    PsiDeclarationStatement::class.java,
    PsiDoWhileStatement::class.java,
    PsiDocToken::class.java,
    PsiEmptyStatement::class.java,
    PsiEnumConstant::class.java,
    PsiExpressionListStatement::class.java,
    PsiExpressionStatement::class.java,
    PsiForStatement::class.java,
    PsiForeachStatement::class.java,
    PsiIfStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiInstanceOfExpression::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiLabeledStatement::class.java,
    PsiLambdaExpression::class.java,
    PsiLiteralExpression::class.java,
    PsiMethodCallExpression::class.java,
    PsiMethodReferenceExpression::class.java,
    PsiNameValuePair::class.java,
    PsiNewExpression::class.java,
    PsiParenthesizedExpression::class.java,
    PsiPolyadicExpression::class.java,
    PsiPostfixExpression::class.java,
    PsiPrefixExpression::class.java,
    PsiReferenceExpression::class.java,
    PsiReturnStatement::class.java,
    PsiSuperExpression::class.java,
    PsiSwitchExpression::class.java,
    PsiSwitchLabelStatement::class.java,
    PsiSwitchLabeledRuleStatement::class.java,
    PsiSwitchStatement::class.java,
    PsiSynchronizedStatement::class.java,
    PsiThisExpression::class.java,
    PsiThrowStatement::class.java,
    PsiTryStatement::class.java,
    PsiTypeCastExpression::class.java,
    PsiTypeElement::class.java,
    PsiWhileStatement::class.java,
    PsiYieldStatement::class.java,
    PsiUnnamedPattern::class.java,
    PsiTypeTestPattern::class.java,
    PsiDeconstructionPattern::class.java
  ),
  UExpressionList::class.java to classSetOf<PsiElement>(
  ),
  UField::class.java to classSetOf<PsiElement>(
    PsiEnumConstant::class.java,
    PsiField::class.java,
    PsiRecordComponent::class.java
  ),
  UFieldEx::class.java to classSetOf<PsiElement>(
    PsiField::class.java,
    PsiRecordComponent::class.java
  ),
  UFile::class.java to classSetOf<PsiElement>(
    PsiJavaFile::class.java
  ),
  UForEachExpression::class.java to classSetOf<PsiElement>(
    PsiForeachStatement::class.java
  ),
  UForExpression::class.java to classSetOf<PsiElement>(
    PsiForStatement::class.java
  ),
  UIdentifier::class.java to classSetOf<PsiElement>(
    PsiIdentifier::class.java,
    PsiKeyword::class.java
  ),
  UIfExpression::class.java to classSetOf<PsiElement>(
    PsiConditionalExpression::class.java,
    //PsiExpressionStatement::class.java,
    PsiIfStatement::class.java
  ),
  UImportStatement::class.java to classSetOf<PsiElement>(
    PsiImportStatement::class.java,
    PsiImportStaticStatement::class.java
  ),
  UInjectionHost::class.java to classSetOf<PsiElement>(
    //PsiExpressionStatement::class.java,
    PsiLiteralExpression::class.java
  ),
  UInstanceExpression::class.java to classSetOf<PsiElement>(
    PsiSuperExpression::class.java,
    PsiThisExpression::class.java
  ),
  UJumpExpression::class.java to classSetOf<PsiElement>(
    PsiBreakStatement::class.java,
    PsiContinueStatement::class.java,
    PsiReturnStatement::class.java,
    PsiYieldStatement::class.java
  ),
  ULabeled::class.java to classSetOf<PsiElement>(
    PsiLabeledStatement::class.java,
    PsiSuperExpression::class.java,
    PsiThisExpression::class.java
  ),
  ULabeledExpression::class.java to classSetOf<PsiElement>(
    PsiLabeledStatement::class.java
  ),
  ULambdaExpression::class.java to classSetOf<PsiElement>(
    PsiLambdaExpression::class.java
  ),
  ULiteralExpression::class.java to classSetOf<PsiElement>(
    //PsiExpressionStatement::class.java,
    PsiLiteralExpression::class.java
  ),
  ULocalVariable::class.java to classSetOf<PsiElement>(
    PsiLocalVariable::class.java,
    PsiResourceVariable::class.java
  ),
  ULocalVariableEx::class.java to classSetOf<PsiElement>(
    PsiLocalVariable::class.java,
    PsiResourceVariable::class.java
  ),
  ULoopExpression::class.java to classSetOf<PsiElement>(
    PsiDoWhileStatement::class.java,
    PsiForStatement::class.java,
    PsiForeachStatement::class.java,
    PsiWhileStatement::class.java
  ),
  UMethod::class.java to classSetOf<PsiElement>(
    PsiAnnotationMethod::class.java,
    PsiMethod::class.java,
    PsiRecordHeader::class.java
  ),
  UMultiResolvable::class.java to classSetOf<PsiElement>(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiAnnotation::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiDocToken::class.java,
    PsiEnumConstant::class.java,
    //PsiExpressionStatement::class.java,
    PsiImportStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiImportStaticStatement::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiMethodCallExpression::class.java,
    PsiMethodReferenceExpression::class.java,
    PsiNewExpression::class.java,
    PsiReferenceExpression::class.java,
    PsiSuperExpression::class.java,
    PsiThisExpression::class.java
  ),
  UNamedExpression::class.java to classSetOf<PsiElement>(
    PsiNameValuePair::class.java
  ),
  UObjectLiteralExpression::class.java to classSetOf<PsiElement>(
    //PsiExpressionStatement::class.java,
    PsiNewExpression::class.java
  ),
  UParameter::class.java to classSetOf<PsiElement>(
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    LightRecordField::class.java,
    PsiRecordComponent::class.java,
    PsiPatternVariable::class.java
  ),
  UParameterEx::class.java to classSetOf<PsiElement>(
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    LightRecordField::class.java,
    PsiRecordComponent::class.java,
    PsiPatternVariable::class.java
  ),
  UParenthesizedExpression::class.java to classSetOf<PsiElement>(
    //PsiExpressionStatement::class.java,
    PsiParenthesizedExpression::class.java
  ),
  UPolyadicExpression::class.java to classSetOf<PsiElement>(
    PsiAssignmentExpression::class.java,
    PsiBinaryExpression::class.java,
    //PsiExpressionStatement::class.java,
    PsiPolyadicExpression::class.java
  ),
  UPostfixExpression::class.java to classSetOf<PsiElement>(
    //PsiExpressionStatement::class.java,
    PsiPostfixExpression::class.java
  ),
  UPrefixExpression::class.java to classSetOf<PsiElement>(
    //PsiExpressionStatement::class.java,
    PsiPrefixExpression::class.java
  ),
  UQualifiedReferenceExpression::class.java to classSetOf<PsiElement>(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    //PsiExpressionStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiMethodCallExpression::class.java,
    PsiReferenceExpression::class.java
  ),
  UReferenceExpression::class.java to classSetOf<PsiElement>(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiDocToken::class.java,
    //PsiExpressionStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiMethodCallExpression::class.java,
    PsiMethodReferenceExpression::class.java,
    PsiReferenceExpression::class.java
  ),
  UResolvable::class.java to classSetOf<PsiElement>(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiAnnotation::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiDocToken::class.java,
    PsiEnumConstant::class.java,
    //PsiExpressionStatement::class.java,
    PsiImportStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiImportStaticStatement::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiMethodCallExpression::class.java,
    PsiMethodReferenceExpression::class.java,
    PsiNewExpression::class.java,
    PsiReferenceExpression::class.java,
    PsiSuperExpression::class.java,
    PsiThisExpression::class.java
  ),
  UReturnExpression::class.java to classSetOf<PsiElement>(
    PsiReturnStatement::class.java
  ),
  USimpleNameReferenceExpression::class.java to classSetOf<PsiElement>(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiDocToken::class.java,
    //PsiExpressionStatement::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiReferenceExpression::class.java
  ),
  USuperExpression::class.java to classSetOf<PsiElement>(
    PsiSuperExpression::class.java
  ),
  USwitchClauseExpression::class.java to classSetOf<PsiElement>(
    PsiSwitchLabelStatement::class.java,
    PsiSwitchLabeledRuleStatement::class.java
  ),
  USwitchClauseExpressionWithBody::class.java to classSetOf<PsiElement>(
    PsiSwitchLabelStatement::class.java,
    PsiSwitchLabeledRuleStatement::class.java
  ),
  USwitchExpression::class.java to classSetOf<PsiElement>(
    PsiSwitchExpression::class.java,
    PsiSwitchStatement::class.java
  ),
  UThisExpression::class.java to classSetOf<PsiElement>(
    PsiThisExpression::class.java
  ),
  UThrowExpression::class.java to classSetOf<PsiElement>(
    PsiThrowStatement::class.java
  ),
  UTryExpression::class.java to classSetOf<PsiElement>(
    PsiTryStatement::class.java
  ),
  UTypeReferenceExpression::class.java to classSetOf<PsiElement>(
    PsiTypeElement::class.java
  ),
  UUnaryExpression::class.java to classSetOf<PsiElement>(
    //PsiExpressionStatement::class.java,
    PsiPostfixExpression::class.java,
    PsiPrefixExpression::class.java
  ),
  UVariable::class.java to classSetOf<PsiElement>(
    PsiEnumConstant::class.java,
    PsiField::class.java,
    PsiLocalVariable::class.java,
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    PsiResourceVariable::class.java,
    PsiRecordComponent::class.java,
    PsiPatternVariable::class.java
  ),
  UVariableEx::class.java to classSetOf<PsiElement>(
    PsiEnumConstant::class.java,
    PsiField::class.java,
    PsiLocalVariable::class.java,
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    PsiResourceVariable::class.java,
    PsiRecordComponent::class.java,
    PsiPatternVariable::class.java
  ),
  UWhileExpression::class.java to classSetOf<PsiElement>(
    PsiWhileStatement::class.java
  ),
  UYieldExpression::class.java to classSetOf<PsiElement>(
    PsiYieldStatement::class.java
  ),
  UastEmptyExpression::class.java to classSetOf<PsiElement>(
    PsiEmptyStatement::class.java
  )
)

private val uElementClassSet: ClassSet<PsiElement> = possibleSourceTypes.getValue(UElement::class.java)
private val uInjectionHostClassSet: ClassSet<PsiElement> = possibleSourceTypes.getValue(UInjectionHost::class.java)
private val uCallClassSet: ClassSet<PsiElement> = possibleSourceTypes.getValue(UCallExpression::class.java)