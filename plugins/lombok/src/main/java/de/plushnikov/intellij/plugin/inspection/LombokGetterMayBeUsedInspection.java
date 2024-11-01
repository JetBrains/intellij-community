// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

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

public final class LombokGetterMayBeUsedInspection extends LombokGetterOrSetterMayBeUsedInspection {
  @Override
  @NotNull
  protected String getTagName() {
    return "return";
  }

  @Override
  @NotNull
  protected String getJavaDocMethodMarkup() {
    return "GETTER";
  }

  @Override
  @NotNull
  protected @NonNls String getAnnotationName() {
    return LombokClassNames.GETTER;
  }

  @Override
  @NotNull
  protected @Nls String getFieldErrorMessage(String fieldName) {
    return LombokBundle.message("inspection.lombok.getter.may.be.used.display.field.message",
                                fieldName);
  }

  @Override
  @NotNull
  protected @Nls String getClassErrorMessage(String className) {
    return LombokBundle.message("inspection.lombok.getter.may.be.used.display.class.message",
                                className);
  }

  @Override
  protected boolean processMethod(
    @NotNull PsiMethod method,
    @NotNull List<Pair<PsiField, PsiMethod>> instanceCandidates,
    @NotNull List<Pair<PsiField, PsiMethod>> staticCandidates
  ) {
    final PsiType returnType = method.getReturnType();
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)
        || method.isConstructor()
        || method.hasParameters()
        || method.getThrowsTypes().length != 0
        || method.hasModifierProperty(PsiModifier.FINAL)
        || method.hasModifierProperty(PsiModifier.ABSTRACT)
        || method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
        || method.hasModifierProperty(PsiModifier.NATIVE)
        || method.hasModifierProperty(PsiModifier.STRICTFP)
        || method.getAnnotations().length != 0
        || PsiTypes.voidType().equals(returnType)
        || returnType == null
        || returnType.getAnnotations().length != 0
        || !method.isWritable()) {
      return false;
    }
    final String methodName = method.getName();
    final boolean isBooleanType = PsiTypes.booleanType().equals(returnType);
    if (isBooleanType ? !methodName.startsWith("is") : !methodName.startsWith("get")) {
      return false;
    }

    if (method.getBody() == null) {
      return false;
    }
    final PsiStatement @NotNull [] methodStatements =
      Arrays.stream(method.getBody().getStatements()).filter(e -> !(e instanceof PsiEmptyStatement)).toArray(PsiStatement[]::new);
    if (methodStatements.length != 1) {
      return false;
    }
    final PsiReturnStatement returnStatement = tryCast(methodStatements[0], PsiReturnStatement.class);
    if (returnStatement == null) {
      return false;
    }
    final PsiReferenceExpression targetRef = tryCast(
      PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue()), PsiReferenceExpression.class);
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
      }
      else if (thisExpression.getQualifier() != null) {
        if (!thisExpression.getQualifier().isReferenceTo(psiClass)) {
          return false;
        }
      }
    }
    final @Nullable String fieldIdentifier = targetRef.getReferenceName();
    if (fieldIdentifier == null) {
      return false;
    }

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
    final PsiField field = psiClass.findFieldByName(fieldIdentifier, false);
    if (field == null
        || !field.isWritable()
        || isMethodStatic != field.hasModifierProperty(PsiModifier.STATIC)
        || !field.getType().equals(returnType)) {
      return false;
    }

    //Check lombok would generate same method name (e.g. for boolean methods prefixed with "is")
    final AccessorsInfo accessorsInfo = AccessorsInfo.buildFor(field);
    final String lombokMethodName = LombokUtils.getGetterName(field, accessorsInfo);
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
    return LombokBundle.message("inspection.lombok.getter.may.be.used.display.fix.name", text);
  }

  @Override
  @NotNull
  protected @Nls String getFixFamilyName() {
    return LombokBundle.message("inspection.lombok.getter.may.be.used.display.fix.family.name");
  }
}