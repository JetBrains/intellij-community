// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

import com.intellij.lang.jvm.JvmMethod;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

public final class LombokSetterMayBeUsedInspection extends LombokGetterOrSetterMayBeUsedInspection {
  @Override
  @NotNull
  protected String getTagName() {
    return "param";
  }

  @Override
  @NotNull
  protected String getJavaDocMethodMarkup() {
    return "SETTER";
  }

  @Override
  @NotNull
  protected @NonNls String getAnnotationName() {
    return LombokClassNames.SETTER;
  }

  @Override
  @NotNull
  protected @Nls String getFieldErrorMessage(String fieldName) {
    return LombokBundle.message("inspection.lombok.setter.may.be.used.display.field.message",
                                fieldName);
  }

  @Override
  @NotNull
  protected @Nls String getClassErrorMessage(String className) {
    return LombokBundle.message("inspection.lombok.setter.may.be.used.display.class.message",
                                className);
  }

  @Override
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

    final String methodName = method.getName();
    if (!methodName.startsWith("set")) {
      return false;
    }

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }

    // skip setter candidates if multiple methods with the same name (and without @Tolerate) exist (lombok would skip generation)
    final PsiMethod[] methods = containingClass.findMethodsByName(methodName, false);
    if (methods.length > 1) {
      boolean skipMethod = false;
      for (PsiMethod psiMethod : methods) {
        if (!psiMethod.hasAnnotation(LombokClassNames.TOLERATE) && !psiMethod.equals(method)) {
          skipMethod = true;
          break;
        }
      }
      if (skipMethod) {
        return false;
      }
    }

    if (method.getBody() == null) {
      return false;
    }
    final PsiStatement @NotNull [] methodStatements =
      Arrays.stream(method.getBody().getStatements()).filter(e -> !(e instanceof PsiEmptyStatement)).toArray(PsiStatement[]::new);
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
    final PsiReferenceExpression sourceRef =
      tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiReferenceExpression.class);
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
    if (qualifier != null) {
      if (thisExpression == null) {
        return false;
      }
      else if (thisExpression.getQualifier() != null) {
        if (!thisExpression.getQualifier().isReferenceTo(containingClass)) {
          return false;
        }
      }
    }
    final @Nullable String fieldIdentifier = targetRef.getReferenceName();
    if (fieldIdentifier == null) {
      return false;
    }
    if (qualifier == null
        && paramIdentifier.equals(fieldIdentifier)) {
      return false;
    }

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final PsiField field = containingClass.findFieldByName(fieldIdentifier, false);
    if (field == null
        || !field.isWritable()
        || isMethodStatic != field.hasModifierProperty(PsiModifier.STATIC)
        || !field.getType().equals(parameterType)) {
      return false;
    }

    //Check lombok would generate same method name (e.g. for boolean methods prefixed with "is")
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(field);
    final String lombokMethodName = LombokUtils.getSetterName(field, accessorsInfo);
    if (!methodName.equals(lombokMethodName)) {
      return false;
    }

    if (isMethodStatic) {
      staticCandidates.add(Pair.pair(field, method));
    }
    else {
      instanceCandidates.add(Pair.pair(field, method));
    }
    return true;
  }

  @Override
  @NotNull
  protected @Nls String getFixName(String text) {
    return LombokBundle.message("inspection.lombok.setter.may.be.used.display.fix.name", text);
  }

  @Override
  @NotNull
  protected @Nls String getFixFamilyName() {
    return LombokBundle.message("inspection.lombok.setter.may.be.used.display.fix.family.name");
  }
}
