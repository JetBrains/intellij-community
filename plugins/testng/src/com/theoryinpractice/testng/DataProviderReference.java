// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInspection.reference.PsiMemberReference;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;

import java.util.ArrayList;
import java.util.List;

public class DataProviderReference extends PsiReferenceBase<PsiLiteral> implements PsiMemberReference {

  public DataProviderReference(PsiLiteral element) {
    super(element, false);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiMethod method) {
      return handleElementRename(method.getName());
    }
    return super.bindToElement(element);
  }

  @Override
  public @Nullable PsiElement resolve() {
    final PsiClass cls = TestNGUtil.getProviderClass(getElement(), PsiUtil.getTopLevelClass(getElement()));
    if (cls == null) return null;
    PsiMethod[] methods = cls.getAllMethods();
    @NonNls String val = getValue();
    for (PsiMethod method : methods) {
      PsiAnnotation dataProviderAnnotation = AnnotationUtil.findAnnotation(method, DataProvider.class.getName());
      if (dataProviderAnnotation == null) continue;
      if (val.equals(method.getName()) || val.equals(getAttributeValue(dataProviderAnnotation, "name"))) return method;
    }
    return null;
  }

  private static String getAttributeValue(@NotNull PsiAnnotation annotation, @SuppressWarnings("SameParameterValue") @NotNull String attributeName) {
    final PsiAnnotationMemberValue dataProviderMethodName = annotation.findDeclaredAttributeValue(attributeName);
    if (dataProviderMethodName == null) return null;
    return StringUtil.unquoteString(dataProviderMethodName.getText());
  }

  @Override
  public Object @NotNull [] getVariants() {
    final PsiClass topLevelClass = PsiUtil.getTopLevelClass(getElement());
    final PsiClass cls = TestNGUtil.getProviderClass(getElement(), topLevelClass);
    if (cls == null) return EMPTY_ARRAY;
    final List<Object> list = new ArrayList<>();

    final boolean needToBeStatic = cls != topLevelClass;
    final PsiMethod current = PsiTreeUtil.getParentOfType(getElement(), PsiMethod.class);
    final PsiMethod[] methods = cls.getAllMethods();
    for (PsiMethod method : methods) {
      if (current != null && method.getName().equals(current.getName())) continue;
      if (needToBeStatic && !method.hasModifierProperty(PsiModifier.STATIC)) continue;
      if (!needToBeStatic && cls != method.getContainingClass() && method.hasModifierProperty(PsiModifier.PRIVATE)) continue;

      final PsiAnnotation dataProviderAnnotation = AnnotationUtil.findAnnotation(method, DataProvider.class.getName());
      if (dataProviderAnnotation == null) continue;

      String value = getAttributeValue(dataProviderAnnotation, "name");
      list.add(LookupElementBuilder.create(value != null ? value : method.getName()));
    }
    return list.toArray();
  }
}
