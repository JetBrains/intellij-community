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
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnTypeFix;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class DataProviderReturnTypeInspection extends BaseJavaLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(DataProviderReturnTypeInspection.class);

  @Nullable
  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final String dataProviderFqn = DataProvider.class.getName();
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, dataProviderFqn);
    if (annotation != null) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null && !isSuitableReturnType(returnType)) {
        final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        LOG.assertTrue(returnTypeElement != null);
        //noinspection DialogTitleCapitalization
        return new ProblemDescriptor[]{manager.createProblemDescriptor(returnTypeElement,
                                                                       "Data provider must return Object[][] or Iterator<Object[]>",
                                                                       true,
                                                                       createFixes(method),
                                                                       ProblemHighlightType.ERROR)};
      }
    }
    return null;
  }

  private static LocalQuickFix[] createFixes(final @NotNull PsiMethod method) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
    final PsiType iterator =
      elementFactory.createTypeFromText(CommonClassNames.JAVA_UTIL_ITERATOR + "<" + CommonClassNames.JAVA_LANG_OBJECT + "[]>", null);
    final PsiType array = elementFactory.createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT + "[][]", null);

    final LocalQuickFix iteratorFix = new MethodReturnTypeFix(method, iterator, false);
    final LocalQuickFix arrayFix = new MethodReturnTypeFix(method, array, false);
    return new LocalQuickFix[]{iteratorFix, arrayFix};
  }

  private static boolean isSuitableReturnType(final @NotNull PsiType type) {
    if (type instanceof PsiArrayType) {
      return isObjectArray(((PsiArrayType)type).getComponentType());
    } else if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass resolvedClass = resolveResult.getElement();
      if (resolvedClass == null || !CommonClassNames.JAVA_UTIL_ITERATOR.equals(resolvedClass.getQualifiedName())) {
        return false;
      }
      final Map<PsiTypeParameter, PsiType> substitutionMap = resolveResult.getSubstitutor().getSubstitutionMap();
      if (substitutionMap.size() != 1) {
        return false;
      }
      final PsiType genericType = ContainerUtil.getFirstItem(substitutionMap.values());
      if (genericType == null) {
        return false;
      }
      return isObjectArray(genericType);
    }
    return false;
  }

  private static boolean isObjectArray(final @NotNull PsiType type) {
    if (!(type instanceof PsiArrayType)) {
      return false;
    }
    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    if (!(componentType instanceof PsiClassType)) {
      return false;
    }
    final PsiClass resolvedClass = ((PsiClassType)componentType).resolve();
    if (resolvedClass == null) {
      return false;
    }
    return CommonClassNames.JAVA_LANG_OBJECT.equals(resolvedClass.getQualifiedName());
  }
}
