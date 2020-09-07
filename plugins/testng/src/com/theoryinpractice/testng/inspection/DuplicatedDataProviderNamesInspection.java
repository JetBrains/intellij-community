// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import com.theoryinpractice.testng.TestngBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.DataProvider;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Batkovich
 */
public class DuplicatedDataProviderNamesInspection extends AbstractBaseJavaLocalInspectionTool {
  private final static Logger LOG = Logger.getInstance(DuplicatedDataProviderNamesInspection.class);
  private final static String NAME_ATTRIBUTE = "name";

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final String dataProviderFqn = DataProvider.class.getCanonicalName();

    final MultiMap<String, PsiMethod> dataProvidersByName = new MultiMap<>();
    for (HierarchicalMethodSignature signature : aClass.getVisibleSignatures()) { //include only visible signatures to hide overridden methods
      PsiMethod method = signature.getMethod();
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, dataProviderFqn);
      if (annotation != null) {
        final PsiAnnotationMemberValue value = annotation.findAttributeValue(NAME_ATTRIBUTE);
        if (value != null) {
          final String dataProviderName = PsiTreeUtil.isAncestor(annotation, value, false)
                                          ? AnnotationUtil.getStringAttributeValue(annotation, NAME_ATTRIBUTE)
                                          : method.getName();
          if (dataProviderName != null) {
            dataProvidersByName.putValue(dataProviderName, method);
          }
        }
      }
    }

    final List<ProblemDescriptor> descriptors = new SmartList<>();
    for (Map.Entry<String, Collection<PsiMethod>> entry : dataProvidersByName.entrySet()) {
      if (entry.getValue().size() > 1) {
        for (PsiMethod method : entry.getValue()) {
          if (method.getContainingClass() != aClass) continue; //don't highlight methods in super class
          final String description =
            TestngBundle.message("inspection.message.data.provider.with.name.already.exists.in.context", entry.getKey());
          final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, dataProviderFqn);
          LOG.assertTrue(annotation != null);
          final PsiAnnotationMemberValue nameElement = annotation.findAttributeValue(NAME_ATTRIBUTE);
          LOG.assertTrue(nameElement != null);
          PsiElement problemElement = PsiTreeUtil.isAncestor(aClass, nameElement, false) ? nameElement : method.getNameIdentifier();
          LOG.assertTrue(problemElement != null);
          descriptors.add(manager.createProblemDescriptor(problemElement, description, isOnTheFly, LocalQuickFix.EMPTY_ARRAY, ProblemHighlightType.ERROR));
        }
      }
    }
    return descriptors.isEmpty() ? null : descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }
}
