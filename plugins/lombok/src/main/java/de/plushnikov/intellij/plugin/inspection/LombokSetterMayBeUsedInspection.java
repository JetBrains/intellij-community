// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.tryCast;

public class LombokSetterMayBeUsedInspection extends LombokGetterOrSetterMayBeUsedInspection {
  @NotNull
  protected String getTagName() {
    return "param";
  }

  @NotNull
  protected String getJavaDocMethodMarkup() {
    return "SETTER";
  }

  @NotNull
  protected @NonNls String getAnnotationName() {
    return LombokClassNames.SETTER;
  }

  @NotNull
  protected @Nls String getFieldErrorMessage(String fieldName) {
    return LombokBundle.message("inspection.lombok.setter.may.be.used.display.field.message",
                                fieldName);
  }

  @NotNull
  protected @Nls String getClassErrorMessage(String className) {
    return LombokBundle.message("inspection.lombok.setter.may.be.used.display.class.message",
                                className);
  }

  protected boolean processMethod(
    @NotNull PsiMethod method,
    @NotNull List<Pair<PsiField, PsiMethod>> instanceCandidates,
    @NotNull List<Pair<PsiField, PsiMethod>> staticCandidates
  ) {
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)
        || method.isConstructor()
        || !method.hasParameters()
        || method.getParameterList().getParameters().length != 1
        || 0 < method.getThrowsTypes().length
        || method.hasModifierProperty(PsiModifier.FINAL)
        || method.hasModifierProperty(PsiModifier.ABSTRACT)
        || method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
        || method.hasModifierProperty(PsiModifier.NATIVE)
        || method.hasModifierProperty(PsiModifier.STRICTFP)
        || 0 < method.getAnnotations().length
        || !PsiTypes.voidType().equals(method.getReturnType())
        || !method.isWritable()) {
      return false;
    }
    final PsiParameter parameter = method.getParameterList().getParameters()[0];
    if (
      parameter.isVarArgs()
      || (
        parameter.getModifierList() != null
        && 0 < parameter.getModifierList().getChildren().length
        && (parameter.getModifierList().getChildren().length != 1 || !parameter.hasModifier(JvmModifier.FINAL))
      )
      || 0 < parameter.getAnnotations().length
    ) {
      return false;
    }
    final PsiType parameterType = parameter.getType();
    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final String methodName = method.getName();
    if (!methodName.startsWith("set")
        || methodName.length() == 3
        || Character.isDigit(methodName.charAt(3))) {
      return false;
    }
    final String fieldName = methodName.substring(3, 4).toLowerCase(Locale.ROOT) + methodName.substring(4);
    if (method.getBody() == null) {
      return false;
    }
    final PsiStatement @NotNull [] methodStatements = Arrays.stream(method.getBody().getStatements()).filter(e -> !(e instanceof PsiEmptyStatement)).toArray(PsiStatement[]::new);
    if (methodStatements.length != 1) {
      return false;
    }
    final PsiExpressionStatement assignmentStatement = tryCast(methodStatements[0], PsiExpressionStatement.class);
    if (assignmentStatement == null) {
      return false;
    }
    final PsiAssignmentExpression assignment = tryCast(assignmentStatement.getExpression(), PsiAssignmentExpression.class);
    if (assignment == null || assignment.getOperationTokenType() != JavaTokenType.EQ) {
      return false;
    }
    final PsiReferenceExpression sourceRef = tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiReferenceExpression.class);
    if (sourceRef == null || sourceRef.getQualifierExpression() != null) {
      return false;
    }
    final @Nullable String paramIdentifier = sourceRef.getReferenceName();
    if (paramIdentifier == null) {
      return false;
    }
    if (!paramIdentifier.equals(parameter.getName())) {
      return false;
    }
    final PsiReferenceExpression targetRef = tryCast(assignment.getLExpression(), PsiReferenceExpression.class);
    if (targetRef == null) {
      return false;
    }
    final @Nullable PsiExpression qualifier = targetRef.getQualifierExpression();
    final @Nullable PsiThisExpression thisExpression = tryCast(qualifier, PsiThisExpression.class);
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(method, PsiClass.class);
    if (psiClass == null) {
      return false;
    }
    if (qualifier != null) {
      if (thisExpression == null) {
        return false;
      } else if (thisExpression.getQualifier() != null) {
        if (!thisExpression.getQualifier().isReferenceTo(psiClass)) {
          return false;
        }
      }
    }
    final @Nullable String fieldIdentifier = targetRef.getReferenceName();
    if (fieldIdentifier == null) {
      return false;
    }
    if (!fieldIdentifier.equals(fieldName)
        && !fieldIdentifier.equals(fieldName.substring(0, 1).toUpperCase(Locale.ROOT) + fieldName.substring(1))) {
      return false;
    }
    if (qualifier == null
        && paramIdentifier.equals(fieldIdentifier)) {
      return false;
    }
    final PsiField field = psiClass.findFieldByName(fieldIdentifier, false);
    if (field == null
        || !field.isWritable()
        || isMethodStatic != field.hasModifierProperty(PsiModifier.STATIC)
        || !field.getType().equals(parameterType)) {
      return false;
    }
    if (isMethodStatic) {
      staticCandidates.add(Pair.pair(field, method));
    } else {
      instanceCandidates.add(Pair.pair(field, method));
    }
    return true;
  }

  @NotNull
  protected @Nls String getFixName(String text) {
    return LombokBundle.message("inspection.lombok.setter.may.be.used.display.fix.name", text);
  }

  @NotNull
  protected @Nls String getFixFamilyName() {
    return LombokBundle.message("inspection.lombok.setter.may.be.used.display.fix.family.name");
  }
}
