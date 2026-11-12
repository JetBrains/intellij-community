// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.util;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiEmptyStatement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.tryCast;
import static java.util.function.Predicate.not;

@ApiStatus.Internal
public final class LombokAccessorMethodUtil {
  public static @Nullable PsiField findFieldIfMethodIsSimpleGetter(@NotNull PsiMethod method) {
    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) {
      return null;
    }
    final PsiStatement @NotNull [] methodStatements =
      Arrays.stream(methodBody.getStatements()).filter(not(PsiEmptyStatement.class::isInstance)).toArray(PsiStatement[]::new);
    if (methodStatements.length != 1) {
      return null;
    }
    final PsiReturnStatement returnStatement = tryCast(methodStatements[0], PsiReturnStatement.class);
    if (returnStatement == null) {
      return null;
    }
    final PsiReferenceExpression targetRef = tryCast(
      PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue()), PsiReferenceExpression.class);
    if (targetRef == null) {
      return null;
    }
    final @Nullable PsiExpression qualifier = targetRef.getQualifierExpression();
    final @Nullable PsiThisExpression thisExpression = tryCast(qualifier, PsiThisExpression.class);
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
    if (psiClass == null) {
      return null;
    }
    if (qualifier != null) {
      if (thisExpression == null) {
        return null;
      }
      else if (thisExpression.getQualifier() != null) {
        if (!thisExpression.getQualifier().isReferenceTo(psiClass)) {
          return null;
        }
      }
    }
    final @Nullable String fieldIdentifier = targetRef.getReferenceName();
    if (fieldIdentifier == null) {
      return null;
    }

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final PsiField field = psiClass.findFieldByName(fieldIdentifier, false);
    if (field == null
        || !field.isWritable()
        || isMethodStatic != field.hasModifierProperty(PsiModifier.STATIC)
        || !field.getType().equals(method.getReturnType())) {
      return null;
    }

    //Check lombok would generate the same method name (e.g. for boolean methods prefixed with "is")
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(field);
    final String lombokMethodName = LombokUtils.getGetterName(field, accessorsInfo);
    if (!method.getName().equals(lombokMethodName)) {
      return null;
    }
    return field;
  }

  public static @Nullable PsiField findFieldIfMethodIsSimpleSetter(@NotNull PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    final PsiCodeBlock methodBody = method.getBody();
    if (methodBody == null) {
      return null;
    }
    final PsiStatement @NotNull [] methodStatements =
      Arrays.stream(methodBody.getStatements()).filter(Predicate.not(PsiEmptyStatement.class::isInstance)).toArray(PsiStatement[]::new);
    if (methodStatements.length != 1) {
      return null;
    }
    final PsiExpressionStatement assignmentStatement = tryCast(methodStatements[0], PsiExpressionStatement.class);
    if (assignmentStatement == null) {
      return null;
    }
    final PsiAssignmentExpression assignment = tryCast(assignmentStatement.getExpression(), PsiAssignmentExpression.class);
    if (assignment == null || assignment.getOperationTokenType() != JavaTokenType.EQ) {
      return null;
    }
    final PsiReferenceExpression sourceRef =
      tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiReferenceExpression.class);
    if (sourceRef == null || sourceRef.getQualifierExpression() != null) {
      return null;
    }
    final @Nullable String paramIdentifier = sourceRef.getReferenceName();
    if (paramIdentifier == null) {
      return null;
    }

    final PsiParameter parameter = method.getParameterList().getParameters()[0];
    if (!paramIdentifier.equals(parameter.getName())) {
      return null;
    }
    final PsiReferenceExpression targetRef = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
    if (targetRef == null) {
      return null;
    }
    final @Nullable PsiExpression qualifier = targetRef.getQualifierExpression();
    final @Nullable PsiThisExpression thisExpression = tryCast(qualifier, PsiThisExpression.class);
    if (qualifier != null) {
      if (thisExpression == null) {
        return null;
      }
      else if (thisExpression.getQualifier() != null) {
        if (!thisExpression.getQualifier().isReferenceTo(containingClass)) {
          return null;
        }
      }
    }
    final @Nullable String fieldIdentifier = targetRef.getReferenceName();
    if (fieldIdentifier == null) {
      return null;
    }
    if (qualifier == null
        && paramIdentifier.equals(fieldIdentifier)) {
      return null;
    }

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final PsiField field = containingClass.findFieldByName(fieldIdentifier, false);
    if (field == null
        || !field.isWritable()
        || isMethodStatic != field.hasModifierProperty(PsiModifier.STATIC)
        || !field.getType().equals(parameter.getType())) {
      return null;
    }

    //Check lombok would generate same method name (e.g. for boolean methods prefixed with "is")
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(field);
    final String lombokMethodName = LombokUtils.getSetterName(field, accessorsInfo);
    if (!method.getName().equals(lombokMethodName)) {
      return null;
    }
    return field;
  }
}
