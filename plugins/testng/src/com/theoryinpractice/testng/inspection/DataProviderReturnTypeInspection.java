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
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Version;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class DataProviderReturnTypeInspection extends BaseJavaLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(DataProviderReturnTypeInspection.class);
  private static final String[] KNOWN_RETURN_TYPES = {
    CommonClassNames.JAVA_UTIL_ITERATOR + "<" + CommonClassNames.JAVA_LANG_OBJECT + "[]>",
    CommonClassNames.JAVA_LANG_OBJECT + "[][]"
  };
  private static final String[] KNOWN_WITH_ONE_DIMENSIONAL_RETURN_TYPES = {
    CommonClassNames.JAVA_UTIL_ITERATOR + "<" + CommonClassNames.JAVA_LANG_OBJECT + "[]>",
    CommonClassNames.JAVA_LANG_OBJECT + "[][]",
    CommonClassNames.JAVA_UTIL_ITERATOR + "<" + CommonClassNames.JAVA_LANG_OBJECT + ">",
    CommonClassNames.JAVA_LANG_OBJECT + "[]"
  };

  @Nullable
  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final String dataProviderFqn = DataProvider.class.getName();
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, dataProviderFqn);
    if (annotation != null) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null && !isSuitableReturnType(method, returnType)) {
        final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        LOG.assertTrue(returnTypeElement != null);
        //noinspection DialogTitleCapitalization
        boolean supportOneDimensional = supportOneDimensional(method);
        String message = "Data provider must return " +
                         (supportOneDimensional ? "Object[][]/Object[] or Iterator<Object[]>/Iterator<Object>"
                                                : "Object[][] or Iterator<Object[]>");
        return new ProblemDescriptor[]{manager.createProblemDescriptor(returnTypeElement,
                                                                       message,
                                                                       isOnTheFly,
                                                                       createFixes(supportOneDimensional, method),
                                                                       ProblemHighlightType.ERROR)};
      }
    }
    return null;
  }

  private static LocalQuickFix[] createFixes(boolean supportOneDimensional, final @NotNull PsiMethod method) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
    List<LocalQuickFix> fixes = new ArrayList<>();

    String[] applicableReturnTypes = supportOneDimensional ? KNOWN_WITH_ONE_DIMENSIONAL_RETURN_TYPES : KNOWN_RETURN_TYPES;
    for (String typeText : applicableReturnTypes) {
      fixes.add(new MethodReturnTypeFix(method, elementFactory.createTypeFromText(typeText, method), false));
    }

    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static boolean isSuitableReturnType(PsiMethod method, final @NotNull PsiType type) {
    if (type instanceof PsiArrayType) {
      return isObjectArray(method, ((PsiArrayType)type).getComponentType());
    }
    else if (type instanceof PsiClassType) {
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
      return isObjectArray(method, genericType);
    }
    return false;
  }

  private static boolean supportOneDimensional(PsiMethod method) {
    Version version = TestNGUtil.detectVersion(method.getProject(), ModuleUtilCore.findModuleForPsiElement(method));
    return version != null && version.isOrGreaterThan(6, 11);
  }

  private static boolean isObjectArray(PsiMethod method, final @NotNull PsiType type) {
    if (!(type instanceof PsiArrayType)) {
      return supportOneDimensional(method) && type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
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
