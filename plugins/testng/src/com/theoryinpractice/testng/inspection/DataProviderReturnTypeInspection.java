// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnTypeFix;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class DataProviderReturnTypeInspection extends AbstractBaseJavaLocalInspectionTool {
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
        String message = TestngBundle.message("inspection.data.provider.return.type.check",
                                              supportOneDimensional ? "Object[][]/Object[] or Iterator<Object[]>/Iterator<Object>"
                                                                    : "Object[][] or Iterator<Object[]>");
        return new ProblemDescriptor[]{manager.createProblemDescriptor(returnTypeElement,
                                                                       message,
                                                                       isOnTheFly,
                                                                       createFixes(supportOneDimensional, method),
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
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

  private static boolean supportOneDimensional(@NotNull Module module) {
    return TestNGUtil.isVersionOrGreaterThan(module.getProject(), module, 6, 11, 0);
  }
}