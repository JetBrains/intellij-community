/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;

import java.util.ArrayList;
import java.util.List;

public class DataProviderReference extends PsiReferenceBase<PsiLiteral> {

  public DataProviderReference(PsiLiteral element) {
    super(element, false);
  }

  @Nullable
  public PsiElement resolve() {
    final PsiClass cls = TestNGUtil.getProviderClass(getElement(), PsiUtil.getTopLevelClass(getElement()));
    if (cls != null) {
      PsiMethod[] methods = cls.getAllMethods();
      @NonNls String val = getValue();
      for (PsiMethod method : methods) {
        PsiAnnotation dataProviderAnnotation = AnnotationUtil.findAnnotation(method, DataProvider.class.getName());
        if (dataProviderAnnotation != null) {
          final PsiAnnotationMemberValue dataProviderMethodName = dataProviderAnnotation.findDeclaredAttributeValue("name");
          if (dataProviderMethodName != null && val.equals(StringUtil.unquoteString(dataProviderMethodName.getText()))) {
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

  @NotNull
  public Object[] getVariants() {
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
        final PsiAnnotation dataProviderAnnotation = AnnotationUtil.findAnnotation(method, DataProvider.class.getName());
        if (dataProviderAnnotation != null) {
          final PsiAnnotationMemberValue memberValue = dataProviderAnnotation.findDeclaredAttributeValue("name");
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
