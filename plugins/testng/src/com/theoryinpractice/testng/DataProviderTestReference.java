// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.codeInspection.reference.PsiMemberReference;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DataProviderTestReference extends PsiReferenceBase<PsiLiteral> implements PsiMemberReference {

  public DataProviderTestReference(PsiLiteral element) {
    super(element, false);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiMethod) {
      return handleElementRename(((PsiMethod)element).getName());
    }
    return super.bindToElement(element);
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    final PsiClass cls = TestNGUtil.getProviderClass(getElement(), PsiUtil.getTopLevelClass(getElement()));
    if (cls != null) {
      PsiMethod[] methods = cls.getAllMethods();
      @NonNls String val = getValue();
      for (PsiMethod method : methods) {
        PsiAnnotation testAnnotation = AnnotationUtil.findAnnotation(method, TestNGUtil.TEST_ANNOTATION_FQN);
        if (testAnnotation != null) {
          final PsiAnnotationMemberValue dataProviderTestMethodName = testAnnotation.findDeclaredAttributeValue("dataProvider");
          if (dataProviderTestMethodName != null && val.equals(StringUtil.unquoteString(dataProviderTestMethodName.getText()))) {
            return method;
          }
          if (val.equals(method.getName())) {
            return method;
          }
        }
      }
    }
    return null;
  }

  @Override
  public Object @NotNull [] getVariants() {
    final List<Object> list = new ArrayList<>();
    final PsiClass topLevelClass = PsiUtil.getTopLevelClass(getElement());
    final PsiClass cls = TestNGUtil.getProviderClass(getElement(), topLevelClass);
    final boolean needToBeStatic = cls != topLevelClass;
    if (cls != null) {
      final PsiMethod current = PsiTreeUtil.getParentOfType(getElement(), PsiMethod.class);
      final PsiMethod[] methods = cls.getAllMethods();
      for (PsiMethod method : methods) {
        if (current != null && method.getName().equals(current.getName())) continue;
        if (needToBeStatic) {
          if (!method.hasModifierProperty(PsiModifier.STATIC)) continue;
        } else {
          if (cls != method.getContainingClass() && method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
        }
        final PsiAnnotation dataProviderAnnotation = AnnotationUtil.findAnnotation(method, TestNGUtil.TEST_ANNOTATION_FQN);
        if (dataProviderAnnotation != null) {
          final PsiAnnotationMemberValue memberValue = dataProviderAnnotation.findDeclaredAttributeValue("dataProvider");
          if (memberValue != null) {
            list.add(LookupValueFactory.createLookupValue(StringUtil.unquoteString(memberValue.getText()), null));
          } else {
            list.add(LookupValueFactory.createLookupValue(method.getName(), null));
          }
        }
      }
    }
    return list.toArray();
  }
}
