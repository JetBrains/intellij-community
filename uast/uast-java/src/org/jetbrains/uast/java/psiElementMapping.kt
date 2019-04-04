// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.java

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import org.jetbrains.uast.*
import org.jetbrains.uast.internal.ClassSet
import org.jetbrains.uast.internal.UElementToPsiElementMapping

private val checkCanConvert = Registry.`is`("uast.java.use.psi.type.precheck")

internal fun canConvert(psiCls: Class<out PsiElement>, targets: Array<out Class<out UElement>>): Boolean {
  if (!checkCanConvert) return true

  if (targets.size == 1) {
    // checking the most popular cases before looking up in hashtable
    when (targets.single()) {
      UElement::class.java -> uElementClassSet.contains(psiCls)
      ULiteralExpression::class.java -> uLiteralClassSet.contains(psiCls)
      UCallExpression::class.java -> uCallClassSet.contains(psiCls)
    }
  }


  return conversionMapping.canConvert(psiCls, targets)
}

private val conversionMapping = UElementToPsiElementMapping(
  UClass::class.java to ClassSet(PsiClass::class.java),
  UMethod::class.java to ClassSet(PsiMethod::class.java),
  UClassInitializer::class.java to ClassSet(PsiClassInitializer::class.java),
  UEnumConstant::class.java to ClassSet(PsiEnumConstant::class.java),
  ULocalVariable::class.java to ClassSet(PsiLocalVariable::class.java),
  UParameter::class.java to ClassSet(PsiParameter::class.java),
  UField::class.java to ClassSet(PsiField::class.java),
  UVariable::class.java to ClassSet(PsiVariable::class.java),
  UAnnotation::class.java to ClassSet(PsiAnnotation::class.java),

  UBlockExpression::class.java to ClassSet(PsiCodeBlock::class.java, PsiBlockStatement::class.java,
                                           PsiSynchronizedStatement::class.java),
  UImportStatement::class.java to ClassSet(PsiImportStatementBase::class.java),
  USimpleNameReferenceExpression::class.java to ClassSet(PsiIdentifier::class.java,
                                                         PsiReferenceExpression::class.java,
                                                         PsiJavaCodeReferenceElement::class.java),
  UIdentifier::class.java to ClassSet(PsiIdentifier::class.java),
  UNamedExpression::class.java to ClassSet(PsiNameValuePair::class.java),
  UCallExpression::class.java to ClassSet(
    PsiArrayInitializerMemberValue::class.java,
    PsiAnnotation::class.java,
    PsiNewExpression::class.java,
    PsiMethodCallExpression::class.java,
    PsiAssertStatement::class.java,
    PsiArrayInitializerExpression::class.java
  ),
  UTypeReferenceExpression::class.java to ClassSet(PsiTypeElement::class.java),

  UBinaryExpression::class.java to ClassSet(
    PsiAssignmentExpression::class.java,
    PsiBinaryExpression::class.java
  ),
  UIfExpression::class.java to ClassSet(PsiConditionalExpression::class.java, PsiIfStatement::class.java),
  UObjectLiteralExpression::class.java to ClassSet(PsiNewExpression::class.java),
  UQualifiedReferenceExpression::class.java to ClassSet(PsiMethodCallExpression::class.java,
                                                        PsiReferenceExpression::class.java,
                                                        PsiJavaCodeReferenceElement::class.java),
  UPolyadicExpression::class.java to ClassSet(PsiPolyadicExpression::class.java),
  UParenthesizedExpression::class.java to ClassSet(PsiParenthesizedExpression::class.java),
  UPrefixExpression::class.java to ClassSet(PsiPrefixExpression::class.java),
  UPostfixExpression::class.java to ClassSet(PsiPostfixExpression::class.java),
  ULiteralExpression::class.java to ClassSet(PsiLiteralExpressionImpl::class.java),
  UCallableReferenceExpression::class.java to ClassSet(PsiMethodReferenceExpression::class.java),
  UThisExpression::class.java to ClassSet(PsiThisExpression::class.java),
  USuperExpression::class.java to ClassSet(PsiSuperExpression::class.java),
  UBinaryExpressionWithType::class.java to
    ClassSet(PsiInstanceOfExpression::class.java, PsiTypeCastExpression::class.java),

  UClassLiteralExpression::class.java to ClassSet(PsiClassObjectAccessExpression::class.java),
  UArrayAccessExpression::class.java to ClassSet(PsiArrayAccessExpression::class.java),
  ULambdaExpression::class.java to ClassSet(PsiLambdaExpression::class.java),
  USwitchExpression::class.java to ClassSet(PsiSwitchExpression::class.java, PsiSwitchStatement::class.java),

  UDeclarationsExpression::class.java to ClassSet(PsiDeclarationStatement::class.java,
                                                  PsiExpressionListStatement::class.java),
  ULabeledExpression::class.java to ClassSet(PsiLabeledStatement::class.java),
  UWhileExpression::class.java to ClassSet(PsiWhileStatement::class.java),
  UDoWhileExpression::class.java to ClassSet(PsiDoWhileStatement::class.java),
  UForExpression::class.java to ClassSet(PsiForStatement::class.java),
  UForEachExpression::class.java to ClassSet(PsiForeachStatement::class.java),
  UBreakExpression::class.java to ClassSet(PsiBreakStatement::class.java),
  UContinueExpression::class.java to ClassSet(PsiContinueStatement::class.java),
  UReturnExpression::class.java to ClassSet(PsiReturnStatement::class.java),
  UThrowExpression::class.java to ClassSet(PsiThrowStatement::class.java),
  UTryExpression::class.java to ClassSet(PsiTryStatement::class.java),
  UastEmptyExpression::class.java to ClassSet(PsiEmptyStatement::class.java),
  UExpressionList::class.java to ClassSet(PsiSwitchLabelStatementBase::class.java),

  UExpression::class.java to ClassSet(PsiExpressionStatement::class.java),
  USwitchClauseExpression::class.java to ClassSet(PsiSwitchLabelStatementBase::class.java)
)

val uElementClassSet = ClassSet(*conversionMapping.baseMapping.flatMap { it.value.initialClasses.asIterable() }.toTypedArray())

val uLiteralClassSet: ClassSet = conversionMapping[ULiteralExpression::class.java]

val uCallClassSet: ClassSet = conversionMapping[UCallExpression::class.java]
