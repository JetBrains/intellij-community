// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.backend.action.lombok;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import de.plushnikov.intellij.plugin.util.LombokProcessorUtil;
import de.plushnikov.intellij.plugin.util.PsiAnnotationSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static de.plushnikov.intellij.plugin.util.PsiAnnotationUtil.addAnnotation;

abstract class BaseLombokModCommandAction extends PsiUpdateModCommandAction<PsiClass> {
  BaseLombokModCommandAction(@NotNull PsiClass element) {
    super(element);
  }

  @Override
  protected final @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass element) {
    return isValidForFile(context, element) ? super.getPresentation(context, element) : null;
  }

  static boolean isValidForFile(@NotNull ActionContext context, @NotNull PsiClass psiClass) {
    return psiClass.isWritable() &&
           (psiClass.getContainingFile() instanceof PsiJavaFile) &&
           !(psiClass instanceof PsiCompiledElement) &&
           !psiClass.isInterface() &&
           LombokLibraryUtil.hasLombokLibrary(context.project());
  }

  protected static void processIntern(@NotNull Map<PsiField, PsiMethod> fieldMethodMap,
                                      @NotNull PsiClass psiClass, String annotationClassName) {
    if (fieldMethodMap.isEmpty()) {
      return;
    }

    final PsiMethod firstPropertyMethod = fieldMethodMap.values().iterator().next();

    final boolean useAnnotationOnClass = haveAllMethodsSameAccessLevel(fieldMethodMap.values()) &&
      isNotAnnotatedWithOrSameAccessLevelAs(psiClass, firstPropertyMethod, annotationClassName);

    if (useAnnotationOnClass) {
      addAnnotation(psiClass, firstPropertyMethod, annotationClassName);
    }

    for (Map.Entry<PsiField, PsiMethod> fieldMethodEntry : fieldMethodMap.entrySet()) {
      final PsiField propertyField = fieldMethodEntry.getKey();
      final PsiMethod propertyMethod = fieldMethodEntry.getValue();

      if (null != propertyField) {
        boolean isStatic = propertyField.hasModifierProperty(PsiModifier.STATIC);
        if (isStatic || !useAnnotationOnClass) {
          addAnnotation(propertyField, propertyMethod, annotationClassName);
        }

        // Move all annotations to field declaration
        for (PsiAnnotation psiMethodAnnotation : propertyMethod.getModifierList().getAnnotations()) {
          psiClass.addBefore(psiMethodAnnotation, propertyField);
        }

        propertyMethod.delete();
      }
    }
  }

  private static boolean isNotAnnotatedWithOrSameAccessLevelAs(PsiClass psiClass,
                                                               PsiMethod firstPropertyMethod,
                                                               String annotationClassName) {
    final PsiAnnotation presentAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiClass, annotationClassName);
    if (null != presentAnnotation) {

      final String presentAccessModifier = LombokProcessorUtil.getMethodModifier(presentAnnotation);
      final String currentAccessModifier = PsiUtil.getAccessModifier(PsiUtil.getAccessLevel(firstPropertyMethod.getModifierList()));

      return presentAccessModifier != null && presentAccessModifier.equals(currentAccessModifier);
    }
    return true;
  }

  private static boolean haveAllMethodsSameAccessLevel(Collection<PsiMethod> psiMethods) {
    final Set<Integer> accessLevelSet = new HashSet<>();
    for (PsiMethod psiMethod : psiMethods) {
      accessLevelSet.add(PsiUtil.getAccessLevel(psiMethod.getModifierList()));
    }
    return accessLevelSet.size() <= 1;
  }

  protected static void removeDefaultAnnotation(@NotNull PsiModifierListOwner targetElement, String annotationClassName) {
    final PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(targetElement, annotationClassName);
    if (null != psiAnnotation) {
      boolean hasOnlyDefaultValues = true;

      final PsiAnnotationParameterList psiAnnotationParameterList = psiAnnotation.getParameterList();
      for (PsiNameValuePair nameValuePair : psiAnnotationParameterList.getAttributes()) {
        if (null != psiAnnotation.findDeclaredAttributeValue(nameValuePair.getName())) {
          hasOnlyDefaultValues = false;
          break;
        }
      }

      if (hasOnlyDefaultValues) {
        psiAnnotation.delete();
      }
    }
  }

  protected static @Nullable PsiMethod findPublicNonStaticMethod(@NotNull PsiClass psiClass, @NotNull String methodName, @NotNull PsiType returnType, PsiType... params) {
    final PsiMethod[] toStringMethods = psiClass.findMethodsByName(methodName, false);
    for (PsiMethod method : toStringMethods) {
      if (method.hasModifierProperty(PsiModifier.PUBLIC) &&
        !method.hasModifierProperty(PsiModifier.STATIC) &&
        returnType.equals(method.getReturnType())) {

        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] psiParameters = parameterList.getParameters();
        final int paramsCount = params.length;

        if (psiParameters.length == paramsCount) {
          boolean allParametersFound = true;
          for (int i = 0; i < paramsCount; i++) {

            if (!psiParameters[i].getType().equals(params[i])) {
              allParametersFound = false;
              break;
            }
          }
          if (allParametersFound) {
            return method;
          }
        }
      }
    }
    return null;
  }
}
