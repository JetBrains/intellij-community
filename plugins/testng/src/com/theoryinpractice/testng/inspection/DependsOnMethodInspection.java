/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hani Suleiman Date: Aug 3, 2005 Time: 3:34:56 AM
 */
public class DependsOnMethodInspection extends BaseJavaLocalInspectionTool
{
    private static final Logger LOGGER = Logger.getInstance("TestNG Runner");
    private static final Pattern PATTERN = Pattern.compile("\"([a-zA-Z1-9_\\(\\)]*)\"");

  @NotNull
    @Override
    public String getGroupDisplayName() {
        return "TestNG";
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "dependsOnMethods problem";
    }

    @NotNull
    @Override
    public String getShortName() {
        return "dependsOnMethodTestNG";
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    @Nullable
    public ProblemDescriptor[] checkClass(@NotNull PsiClass psiClass, @NotNull InspectionManager manager, boolean isOnTheFly) {

        //LOGGER.info("Looking for dependsOnMethods problems in " + psiClass.getName());

        if (!psiClass.getContainingFile().isWritable()) return null;

        PsiAnnotation[] annotations = TestNGUtil.getTestNGAnnotations(psiClass);
        if(annotations.length == 0) return ProblemDescriptor.EMPTY_ARRAY;
        List<ProblemDescriptor> problemDescriptors = new ArrayList<ProblemDescriptor>();

        for (PsiAnnotation annotation : annotations) {
          final PsiAnnotationMemberValue value = annotation.findAttributeValue("dependsOnMethods");
          if (value != null) {
            String text = value.getText();
            if (value instanceof PsiReferenceExpression) {
              final PsiElement resolve = ((PsiReferenceExpression)value).resolve();
              if (resolve instanceof PsiField && ((PsiField)resolve).hasModifierProperty(PsiModifier.STATIC) && ((PsiField)resolve).hasModifierProperty(PsiModifier.FINAL)) {
                final PsiExpression initializer = ((PsiField)resolve).getInitializer();
                if (initializer != null) {
                  text = initializer.getText();
                }
              }
            }
            Matcher matcher = PATTERN.matcher(text);
            while (matcher.find()) {
                String methodName = matcher.group(1);
                checkMethodNameDependency(manager, psiClass, methodName, value, problemDescriptors, isOnTheFly);
            }
          }
        }
        
        return problemDescriptors.toArray(new ProblemDescriptor[problemDescriptors.size()]);
    }

    private static void checkMethodNameDependency(InspectionManager manager, PsiClass psiClass, String methodName, PsiAnnotationMemberValue value,
                                                  List<ProblemDescriptor> problemDescriptors, boolean onTheFly) {
        LOGGER.debug("Found dependsOnMethods with text: " + methodName);
        if (methodName.length() > 0 && methodName.charAt(methodName.length() - 1) == ')') {

            LOGGER.debug("dependsOnMethods contains ()" + psiClass.getName());
            // TODO Add quick fix for removing brackets on annotation
            ProblemDescriptor descriptor = manager.createProblemDescriptor(value,
                                                               "Method '" + methodName + "' should not include () characters.",
                                                               (LocalQuickFix) null,
                                                               ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly);

            problemDescriptors.add(descriptor);

        } else {
            final String configAnnotation = TestNGUtil.getConfigAnnotation(PsiTreeUtil.getParentOfType(value, PsiMethod.class));
            PsiMethod[] foundMethods = psiClass.findMethodsByName(methodName, true);
            if (foundMethods.length == 0) {
                LOGGER.debug("dependsOnMethods method doesn't exist:" + methodName);
                ProblemDescriptor descriptor = manager.createProblemDescriptor(value,
                                                                   "Method '" + methodName + "' unknown.",
                                                                   (LocalQuickFix) null,
                                                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly);
                problemDescriptors.add(descriptor);

            } else {
              boolean hasTestsOrConfigs = false;
              for (PsiMethod foundMethod : foundMethods) {
                if (configAnnotation != null) {
                  hasTestsOrConfigs |= AnnotationUtil.isAnnotated(foundMethod, configAnnotation, true);
                } else {
                  hasTestsOrConfigs |= TestNGUtil.hasTest(foundMethod);
                }
              }

              if (!hasTestsOrConfigs) {
                ProblemDescriptor descriptor = manager.createProblemDescriptor(value,
                                                                     configAnnotation == null ? "Method '" + methodName + "' is not a test or configuration method." :
                                                                                                "Method '" + methodName + "' is not annotated with @" + configAnnotation,
                                                                     (LocalQuickFix) null,
                                                                     ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, onTheFly);
                problemDescriptors.add(descriptor);
              }
            }
        }
    }

}
