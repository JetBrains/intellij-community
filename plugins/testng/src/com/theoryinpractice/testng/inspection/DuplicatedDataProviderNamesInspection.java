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
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class DuplicatedDataProviderNamesInspection extends BaseJavaLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(DuplicatedDataProviderNamesInspection.class);
  private final static String NAME_ATTRIBUTE = "name";

  @Nullable
  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final String dataProviderFqn = DataProvider.class.getCanonicalName();

    final MultiMap<String, PsiMethod> dataProvidersByName = new MultiMap<>();
    for (PsiMethod method : aClass.getMethods()) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, dataProviderFqn);
      if (annotation != null) {
        final PsiAnnotationMemberValue value = annotation.findAttributeValue(NAME_ATTRIBUTE);
        LOG.assertTrue(value != null);
        final String dataProviderName = PsiTreeUtil.isAncestor(annotation, value, false)
                                        ? AnnotationUtil.getStringAttributeValue(annotation, NAME_ATTRIBUTE)
                                        : method.getName();
        if (dataProviderName != null) {
          dataProvidersByName.putValue(dataProviderName, method);
        }
      }
    }

    final List<ProblemDescriptor> descriptors = new SmartList<>();
    for (Map.Entry<String, Collection<PsiMethod>> entry : dataProvidersByName.entrySet()) {
      if (entry.getValue().size() > 1) {
        for (PsiMethod method : entry.getValue()) {
          final String description = String.format("Data provider with name '%s' already exists in context", entry.getKey());
          final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, dataProviderFqn);
          LOG.assertTrue(annotation != null);
          final PsiAnnotationMemberValue nameElement = annotation.findAttributeValue(NAME_ATTRIBUTE);
          LOG.assertTrue(nameElement != null);
          PsiElement problemElement = PsiTreeUtil.isAncestor(aClass, nameElement, false) ? nameElement : method.getNameIdentifier();
          LOG.assertTrue(problemElement != null);
          descriptors.add(manager.createProblemDescriptor(problemElement, description, true, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.ERROR));
        }
      }
    }
    return descriptors.isEmpty() ? null : descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }
}
