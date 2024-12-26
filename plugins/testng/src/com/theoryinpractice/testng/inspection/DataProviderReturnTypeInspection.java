// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;

import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class DataProviderReturnTypeInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(DataProviderReturnTypeInspection.class);

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final String dataProviderFqn = DataProvider.class.getName();
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, dataProviderFqn);
    if (annotation != null) {
      final PsiType returnType = method.getReturnType();
      if (returnType != null && !isSuitableReturnType(returnType, annotation)) {
        final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        LOG.assertTrue(returnTypeElement != null);
        Module module = ModuleUtilCore.findModuleForPsiElement(annotation);
        if (module == null) return null;
        boolean supportOneDimensional = supportOneDimensional(module);
        String message;
        if (supportOneDimensional) {
          message = TestngBundle.message("inspection.data.provider.return.type.check");
        } else {
          message = TestngBundle.message("inspection.data.provider.return.type.multi.check");
        }
        return new ProblemDescriptor[]{manager.createProblemDescriptor(
          returnTypeElement,
          message,
          isOnTheFly,
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        )};
      }
    }
    return null;
  }

  private static boolean isSuitableReturnType(@NotNull PsiType type, @NotNull PsiAnnotation annotation) {
    if (type instanceof PsiArrayType) {
      return isSuitableInnerType(((PsiArrayType)type).getComponentType(), annotation);
    }
    if (type instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      final PsiClass resolvedClass = resolveResult.getElement();
      if (resolvedClass == null || !CommonClassNames.JAVA_UTIL_ITERATOR.equals(resolvedClass.getQualifiedName())) return false;
      final Map<PsiTypeParameter, PsiType> substitutionMap = resolveResult.getSubstitutor().getSubstitutionMap();
      if (substitutionMap.size() != 1) return false;
      final PsiType genericType = ContainerUtil.getFirstItem(substitutionMap.values());
      if (genericType == null) return false;
      return isSuitableInnerType(genericType, annotation);
    }
    return false;
  }

  private static boolean isSuitableInnerType(@NotNull PsiType type, @NotNull PsiAnnotation annotation) {
    if (!(type instanceof PsiArrayType)) {
      Module module = ModuleUtilCore.findModuleForPsiElement(annotation);
      if (module == null) return false;
      return supportOneDimensional(module);
    }
    final PsiType componentType = type.getDeepComponentType();
    if (!(componentType instanceof PsiClassType)) return false;
    final PsiClass resolvedClass = ((PsiClassType)componentType).resolve();
    if (resolvedClass == null) return false;
    return true;
  }

  // see https://github.com/cbeust/testng/issues/1139
  private static boolean supportOneDimensional(@NotNull Module module) {
    return TestNGUtil.isVersionOrGreaterThan(module.getProject(), module, 6, 10, 0);
  }
}