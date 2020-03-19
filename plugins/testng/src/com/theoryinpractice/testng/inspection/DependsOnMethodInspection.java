// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.theoryinpractice.testng.TestngBundle;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hani Suleiman
 */
public class DependsOnMethodInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
  private static final Pattern PATTERN = Pattern.compile("\"([a-zA-Z1-9_()*]*)\"");

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return TestNGUtil.TESTNG_GROUP_NAME;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "dependsOnMethodTestNG";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {

    PsiAnnotation[] annotations = TestNGUtil.getTestNGAnnotations(psiClass);
    if (annotations.length == 0) return ProblemDescriptor.EMPTY_ARRAY;
    List<ProblemDescriptor> problemDescriptors = new ArrayList<>();

    for (PsiAnnotation annotation : annotations) {
      final PsiAnnotationMemberValue value = annotation.findDeclaredAttributeValue("dependsOnMethods");
      if (value != null && !TestNGUtil.isDisabled(annotation)) {
        String text = value.getText();
        if (value instanceof PsiReferenceExpression) {
          final PsiElement resolve = ((PsiReferenceExpression)value).resolve();
          if (resolve instanceof PsiField &&
              ((PsiField)resolve).hasModifierProperty(PsiModifier.STATIC) &&
              ((PsiField)resolve).hasModifierProperty(PsiModifier.FINAL)) {
            final PsiExpression initializer = ((PsiField)resolve).getInitializer();
            if (initializer != null) {
              text = initializer.getText();
            }
          }
        }
        final Set<String> names = new HashSet<>();
        final Matcher matcher = PATTERN.matcher(text);
        int idx = 0;
        while (matcher.find()) {
          String methodName = matcher.group(1);
          if (!names.add(methodName)) {
            PsiAnnotationMemberValue element2Highlight = value;
            if (value instanceof PsiArrayInitializerMemberValue) {
              final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
              if (idx < initializers.length) {
                element2Highlight = initializers[idx];
              }
            }
            problemDescriptors.add(manager.createProblemDescriptor(element2Highlight, TestngBundle.message("inspection.depends.on.method.duplicated.name.problem", methodName),
                                                                   (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                   isOnTheFly));
          }
          checkMethodNameDependency(manager, psiClass, methodName, value, problemDescriptors, isOnTheFly);
          idx++;
        }
      }
    }

    return problemDescriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static void checkMethodNameDependency(InspectionManager manager,
                                                PsiClass psiClass,
                                                String methodName,
                                                PsiAnnotationMemberValue value,
                                                List<ProblemDescriptor> problemDescriptors,
                                                boolean onTheFly) {
    LOGGER.debug("Found dependsOnMethods with text: " + methodName);
    if (!methodName.isEmpty() && methodName.charAt(methodName.length() - 1) == ')') {

      LOGGER.debug("dependsOnMethods contains ()" + psiClass.getName());
      // TODO Add quick fix for removing brackets on annotation
      String template = TestngBundle.message("inspection.depends.on.method.check", methodName);
      problemDescriptors.add(manager.createProblemDescriptor(
        value, template, (LocalQuickFix)null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly));
    }
    else {
      final String configAnnotation = TestNGUtil.getConfigAnnotation(PsiTreeUtil.getParentOfType(value, PsiMethod.class));
      final PsiMethod[] foundMethods;
      if (methodName.endsWith("*")) {
        final String methodNameMask = StringUtil.trimEnd(methodName, "*");
        final List<PsiMethod> methods = ContainerUtil.filter(psiClass.getMethods(),
                                                             method -> method.getName().startsWith(methodNameMask));
        foundMethods = methods.toArray(PsiMethod.EMPTY_ARRAY);
      }
      else {
        foundMethods = psiClass.findMethodsByName(methodName, true);
      }
      if (foundMethods.length == 0) {
        LOGGER.debug("dependsOnMethods method doesn't exist:" + methodName);
        problemDescriptors.add(manager.createProblemDescriptor(
          value, TestngBundle.message("inspection.depends.on.method.unknown.method.problem", methodName), (LocalQuickFix)null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly));
      }
      else {
        boolean hasTestsOrConfigs = false;
        for (PsiMethod foundMethod : foundMethods) {
          if (configAnnotation != null) {
            hasTestsOrConfigs |= AnnotationUtil.isAnnotated(foundMethod, configAnnotation, AnnotationUtil.CHECK_HIERARCHY);
          }
          else {
            hasTestsOrConfigs |= TestNGUtil.hasTest(foundMethod);
          }
        }

        if (!hasTestsOrConfigs) {
          String template = configAnnotation == null
                            ? TestngBundle.message("inspection.depends.on.method.is.not.test", methodName)
                            : TestngBundle.message("inspection.depends.on.method.is.not.annotated", methodName, configAnnotation);
          problemDescriptors.add(manager.createProblemDescriptor(
            value, template, (LocalQuickFix)null, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly));
        }
      }
    }
  }
}
