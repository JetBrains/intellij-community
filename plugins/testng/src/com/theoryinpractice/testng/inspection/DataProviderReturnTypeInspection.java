// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.MethodReturnTypeFix;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Version;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.theoryinpractice.testng.util.TestNGUtil.TEST_ANNOTATION_FQN;

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
      if (returnType != null && !isSuitableReturnType(method, returnType, annotation)) {
        final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
        LOG.assertTrue(returnTypeElement != null);
        boolean supportOneDimensional = supportOneDimensional(method);
        String message = TestngBundle.message("inspection.data.provider.return.type.check",
                                              supportOneDimensional ? "Object[][]/Object[] or Iterator<Object[]>/Iterator<Object>"
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

  private static boolean isAppropriateType(PsiType returnType, @NotNull PsiAnnotation annotation) {
    PsiAnnotationMemberValue nameValue = annotation.findAttributeValue("name");
    ArrayList<PsiReference> references = new ArrayList<>();
    if (nameValue != null) {
      PsiReference[] refs = nameValue.getReferences();
      if (refs.length != 0) {
        references.addAll(Arrays.asList(refs));
      }
    }
    PsiMethod resolvedMethod = null;
    if (references.size() > 0) {
      for (PsiReference ref : references) {
        PsiElement resolve = ref.resolve();
        if (resolve instanceof PsiMethod && AnnotationUtil.findAnnotation((PsiMethod)resolve, TEST_ANNOTATION_FQN) != null) {
          resolvedMethod = (PsiMethod)resolve;
        }
      }
    }
    if (resolvedMethod == null) return false;
    PsiParameter[] parameters;
    parameters = resolvedMethod.getParameterList().getParameters();
    ArrayList<PsiType> appropriateTypes = Arrays.stream(parameters).map(PsiParameter::getType).map(PsiType::getDeepComponentType)
      .collect(Collectors.toCollection(ArrayList::new));
    return appropriateTypes.contains(returnType.getDeepComponentType());
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

  private static boolean isSuitableReturnType(PsiMethod method, final @NotNull PsiType type, @NotNull PsiAnnotation annotation) {
    if (type instanceof PsiArrayType) {
      return isAppropriateTypeArray(method, ((PsiArrayType)type).getComponentType(), annotation);
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
      return isAppropriateTypeArray(method, genericType, annotation);
    }
    return false;
  }

  private static boolean supportOneDimensional(PsiMethod method) {
    Version version = TestNGUtil.detectVersion(method.getProject(), ModuleUtilCore.findModuleForPsiElement(method));
    return version != null && version.isOrGreaterThan(6, 11);
  }

  private static boolean isAppropriateTypeArray(PsiMethod method, final @NotNull PsiType type, @NotNull PsiAnnotation annotation) {
    if (!(type instanceof PsiArrayType)) {
      return supportOneDimensional(method) && (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || isAppropriateType(type, annotation));
    }
    final PsiType componentType = type.getDeepComponentType();
    if (!(componentType instanceof PsiClassType)) {
      return false;
    }
    final PsiClass resolvedClass = ((PsiClassType)componentType).resolve();
    if (resolvedClass == null) {
      return false;
    }
    return CommonClassNames.JAVA_LANG_OBJECT.equals(resolvedClass.getQualifiedName()) || isAppropriateType(type, annotation);
  }
}