// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiExtensibleClass
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocToken
import org.jetbrains.uast.*
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.internal.ClassSet
import org.jetbrains.uast.psi.UElementWithLocation

private val checkCanConvert = Registry.`is`("uast.java.use.psi.type.precheck")

internal fun canConvert(psiCls: Class<out PsiElement>, targets: Array<out Class<out UElement>>): Boolean {
  if (!checkCanConvert) return true

  if (targets.size == 1) {
    // checking the most popular cases before looking up in hashtable
    when (targets.single()) {
      UElement::class.java -> return uElementClassSet.contains(psiCls)
      UInjectionHost::class.java -> return uInjectionHostClassSet.contains(psiCls)
      UCallExpression::class.java -> return uCallClassSet.contains(psiCls)
    }
  }

  return targets.any { getPossibleSourceTypes(it).contains(psiCls) }
}

internal fun getPossibleSourceTypes(uastType: Class<out UElement>) =
  possibleSourceTypes[uastType] ?: error("Java UAST possibleSourceTypes misses value for $uastType")

// Machine generated with PsiYieldStatement being added manually
@Suppress("DEPRECATION")
private val possibleSourceTypes = mapOf<Class<*>, ClassSet>(
  UAnchorOwner::class.java to ClassSet(
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
    PsiTypeParameter::class.java
  ),
  UAnnotated::class.java to ClassSet(
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
    PsiYieldStatement::class.java
  ),
  UAnnotation::class.java to ClassSet(
    PsiAnnotation::class.java
  ),
  UAnnotationEx::class.java to ClassSet(
    PsiAnnotation::class.java
  ),
  UAnnotationMethod::class.java to ClassSet(
    PsiAnnotationMethod::class.java
  ),
  UAnonymousClass::class.java to ClassSet(
    PsiAnonymousClass::class.java,
    PsiEnumConstantInitializer::class.java
  ),
  UArrayAccessExpression::class.java to ClassSet(
    PsiArrayAccessExpression::class.java
    //PsiExpressionStatement::class.java
  ),
  UBinaryExpression::class.java to ClassSet(
    PsiAssignmentExpression::class.java,
    PsiBinaryExpression::class.java
    //PsiExpressionStatement::class.java
  ),
  UBinaryExpressionWithType::class.java to ClassSet(
    PsiInstanceOfExpression::class.java,
    PsiTypeCastExpression::class.java
  ),
  UBlockExpression::class.java to ClassSet(
    PsiBlockStatement::class.java,
    PsiCodeBlock::class.java,
    PsiSynchronizedStatement::class.java
  ),
  UBreakExpression::class.java to ClassSet(
    PsiBreakStatement::class.java
  ),
  UCallExpression::class.java to ClassSet(
    PsiAnnotation::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiEnumConstant::class.java,
    //PsiExpressionStatement::class.java,
    PsiMethodCallExpression::class.java,
    PsiNewExpression::class.java
  ),
  UCallExpressionEx::class.java to ClassSet(
    PsiAnnotation::class.java,
    PsiArrayInitializerExpression::class.java,
    PsiArrayInitializerMemberValue::class.java,
    PsiAssertStatement::class.java,
    PsiEnumConstant::class.java,
    //PsiExpressionStatement::class.java,
    PsiMethodCallExpression::class.java,
    PsiNewExpression::class.java
  ),
  UCallableReferenceExpression::class.java to ClassSet(
    PsiMethodReferenceExpression::class.java
  ),
  UCatchClause::class.java to ClassSet(
    PsiCatchSection::class.java
  ),
  UClass::class.java to ClassSet(
    PsiAnonymousClass::class.java,
    PsiEnumConstantInitializer::class.java,
    PsiExtensibleClass::class.java,
    PsiTypeParameter::class.java
  ),
  UClassInitializer::class.java to ClassSet(
    PsiClassInitializer::class.java
  ),
  UClassInitializerEx::class.java to ClassSet(
    PsiClassInitializer::class.java
  ),
  UClassLiteralExpression::class.java to ClassSet(
    PsiClassObjectAccessExpression::class.java
  ),
  UClassTypeSpecific::class.java to ClassSet(
  ),
  UComment::class.java to ClassSet(
    PsiComment::class.java,
    PsiDocComment::class.java
  ),
  UContinueExpression::class.java to ClassSet(
    PsiContinueStatement::class.java
  ),
  UDeclaration::class.java to ClassSet(
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
    PsiTypeParameter::class.java
  ),
  UDeclarationEx::class.java to ClassSet(
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
    PsiTypeParameter::class.java
  ),
  UDeclarationsExpression::class.java to ClassSet(
    PsiDeclarationStatement::class.java,
    PsiExpressionListStatement::class.java
  ),
  UDoWhileExpression::class.java to ClassSet(
    PsiDoWhileStatement::class.java
  ),
  UElement::class.java to ClassSet(
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
    PsiYieldStatement::class.java
  ),
  UElementWithLocation::class.java to ClassSet(
    //PsiExpressionStatement::class.java,
    PsiMethodCallExpression::class.java
  ),
  UEnumConstant::class.java to ClassSet(
    PsiEnumConstant::class.java
  ),
  UEnumConstantEx::class.java to ClassSet(
    PsiEnumConstant::class.java
  ),
  UExpression::class.java to ClassSet(
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
    PsiYieldStatement::class.java
  ),
  UExpressionList::class.java to ClassSet(
  ),
  UField::class.java to ClassSet(
    PsiEnumConstant::class.java,
    PsiField::class.java
  ),
  UFieldEx::class.java to ClassSet(
    PsiField::class.java
  ),
  UFile::class.java to ClassSet(
    PsiJavaFile::class.java
  ),
  UForEachExpression::class.java to ClassSet(
    PsiForeachStatement::class.java
  ),
  UForExpression::class.java to ClassSet(
    PsiForStatement::class.java
  ),
  UIdentifier::class.java to ClassSet(
    PsiIdentifier::class.java,
    PsiKeyword::class.java
  ),
  UIfExpression::class.java to ClassSet(
    PsiConditionalExpression::class.java,
    //PsiExpressionStatement::class.java,
    PsiIfStatement::class.java
  ),
  UImportStatement::class.java to ClassSet(
    PsiImportStatement::class.java,
    PsiImportStaticStatement::class.java
  ),
  UInjectionHost::class.java to ClassSet(
    //PsiExpressionStatement::class.java,
    PsiLiteralExpression::class.java
  ),
  UInstanceExpression::class.java to ClassSet(
    PsiSuperExpression::class.java,
    PsiThisExpression::class.java
  ),
  UJumpExpression::class.java to ClassSet(
    PsiBreakStatement::class.java,
    PsiContinueStatement::class.java,
    PsiReturnStatement::class.java,
    PsiYieldStatement::class.java
  ),
  ULabeled::class.java to ClassSet(
    PsiLabeledStatement::class.java,
    PsiSuperExpression::class.java,
    PsiThisExpression::class.java
  ),
  ULabeledExpression::class.java to ClassSet(
    PsiLabeledStatement::class.java
  ),
  ULambdaExpression::class.java to ClassSet(
    PsiLambdaExpression::class.java
  ),
  ULiteralExpression::class.java to ClassSet(
    //PsiExpressionStatement::class.java,
    PsiLiteralExpression::class.java
  ),
  ULocalVariable::class.java to ClassSet(
    PsiLocalVariable::class.java,
    PsiResourceVariable::class.java
  ),
  ULocalVariableEx::class.java to ClassSet(
    PsiLocalVariable::class.java,
    PsiResourceVariable::class.java
  ),
  ULoopExpression::class.java to ClassSet(
    PsiDoWhileStatement::class.java,
    PsiForStatement::class.java,
    PsiForeachStatement::class.java,
    PsiWhileStatement::class.java
  ),
  UMethod::class.java to ClassSet(
    PsiAnnotationMethod::class.java,
    PsiMethod::class.java
  ),
  UMethodTypeSpecific::class.java to ClassSet(
  ),
  UMultiResolvable::class.java to ClassSet(
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
  UNamedExpression::class.java to ClassSet(
    PsiNameValuePair::class.java
  ),
  UObjectLiteralExpression::class.java to ClassSet(
    //PsiExpressionStatement::class.java,
    PsiNewExpression::class.java
  ),
  UParameter::class.java to ClassSet(
    PsiParameter::class.java,
    PsiPatternVariable::class.java
  ),
  UParameterEx::class.java to ClassSet(
    PsiParameter::class.java,
    PsiPatternVariable::class.java
  ),
  UParenthesizedExpression::class.java to ClassSet(
    //PsiExpressionStatement::class.java,
    PsiParenthesizedExpression::class.java
  ),
  UPolyadicExpression::class.java to ClassSet(
    PsiAssignmentExpression::class.java,
    PsiBinaryExpression::class.java,
    //PsiExpressionStatement::class.java,
    PsiPolyadicExpression::class.java
  ),
  UPostfixExpression::class.java to ClassSet(
    //PsiExpressionStatement::class.java,
    PsiPostfixExpression::class.java
  ),
  UPrefixExpression::class.java to ClassSet(
    //PsiExpressionStatement::class.java,
    PsiPrefixExpression::class.java
  ),
  UQualifiedReferenceExpression::class.java to ClassSet(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    //PsiExpressionStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiMethodCallExpression::class.java,
    PsiReferenceExpression::class.java
  ),
  UReferenceExpression::class.java to ClassSet(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiDocToken::class.java,
    //PsiExpressionStatement::class.java,
    PsiImportStaticReferenceElement::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiMethodCallExpression::class.java,
    PsiMethodReferenceExpression::class.java,
    PsiReferenceExpression::class.java
  ),
  UResolvable::class.java to ClassSet(
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
  UReturnExpression::class.java to ClassSet(
    PsiReturnStatement::class.java
  ),
  USimpleNameReferenceExpression::class.java to ClassSet(
    PsiAnnotatedJavaCodeReferenceElement::class.java,
    PsiDocToken::class.java,
    //PsiExpressionStatement::class.java,
    PsiJavaModuleReferenceElement::class.java,
    PsiReferenceExpression::class.java
  ),
  USuperExpression::class.java to ClassSet(
    PsiSuperExpression::class.java
  ),
  USwitchClauseExpression::class.java to ClassSet(
    PsiSwitchLabelStatement::class.java,
    PsiSwitchLabeledRuleStatement::class.java
  ),
  USwitchClauseExpressionWithBody::class.java to ClassSet(
    PsiSwitchLabelStatement::class.java,
    PsiSwitchLabeledRuleStatement::class.java
  ),
  USwitchExpression::class.java to ClassSet(
    PsiSwitchExpression::class.java,
    PsiSwitchStatement::class.java
  ),
  UThisExpression::class.java to ClassSet(
    PsiThisExpression::class.java
  ),
  UThrowExpression::class.java to ClassSet(
    PsiThrowStatement::class.java
  ),
  UTryExpression::class.java to ClassSet(
    PsiTryStatement::class.java
  ),
  UTypeReferenceExpression::class.java to ClassSet(
    PsiTypeElement::class.java
  ),
  UUnaryExpression::class.java to ClassSet(
    //PsiExpressionStatement::class.java,
    PsiPostfixExpression::class.java,
    PsiPrefixExpression::class.java
  ),
  UVariable::class.java to ClassSet(
    PsiEnumConstant::class.java,
    PsiField::class.java,
    PsiLocalVariable::class.java,
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    PsiResourceVariable::class.java
  ),
  UVariableEx::class.java to ClassSet(
    PsiEnumConstant::class.java,
    PsiField::class.java,
    PsiLocalVariable::class.java,
    PsiParameter::class.java,
    PsiPatternVariable::class.java,
    PsiResourceVariable::class.java
  ),
  UWhileExpression::class.java to ClassSet(
    PsiWhileStatement::class.java
  ),
  UYieldExpression::class.java to ClassSet(
    PsiYieldStatement::class.java
  ),
  UastEmptyExpression::class.java to ClassSet(
    PsiEmptyStatement::class.java
  )
)

private val uElementClassSet = possibleSourceTypes.getValue(UElement::class.java)
private val uInjectionHostClassSet = possibleSourceTypes.getValue(UInjectionHost::class.java)
private val uCallClassSet = possibleSourceTypes.getValue(UCallExpression::class.java)