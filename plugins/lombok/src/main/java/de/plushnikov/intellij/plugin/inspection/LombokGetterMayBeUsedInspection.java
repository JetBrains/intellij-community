// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package de.plushnikov.intellij.plugin.inspection;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.handler.LombokGetterHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class LombokGetterMayBeUsedInspection extends LombokGetterOrSetterMayBeUsedInspection {
  @Override
  protected @NotNull String getTagName() {
    return "return";
  }

  @Override
  protected @NotNull String getJavaDocMethodMarkup() {
    return "GETTER";
  }

  @Override
  protected @NotNull @NonNls String getAnnotationName() {
    return LombokClassNames.GETTER;
  }

  @Override
  protected @NotNull @Nls String getFieldErrorMessage(String fieldName) {
    return LombokBundle.message("inspection.lombok.getter.may.be.used.display.field.message",
                                fieldName);
  }

  @Override
  protected @NotNull @Nls String getClassErrorMessage(String className) {
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

    final PsiField field = LombokGetterHandler.findFieldIfMethodIsSimpleGetter(method);
    if (field == null) return false;

    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      staticCandidates.add(Pair.pair(field, method));
    }
    else {
      instanceCandidates.add(Pair.pair(field, method));
    }
    return true;
  }

  @Override
  protected @NotNull @Nls String getFixName(String text) {
    return LombokBundle.message("inspection.lombok.getter.may.be.used.display.fix.name", text);
  }

  @Override
  protected @NotNull @Nls String getFixFamilyName() {
    return LombokBundle.message("inspection.lombok.getter.may.be.used.display.fix.family.name");
  }
}