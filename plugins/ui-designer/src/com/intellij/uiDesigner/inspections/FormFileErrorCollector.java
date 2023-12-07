// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.make.FormElementNavigatable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class FormFileErrorCollector extends FormErrorCollector {
  private final PsiFile myFile;
  private final boolean myOnTheFly;
  private final List<ProblemDescriptor> myProblems = new ArrayList<>();

  FormFileErrorCollector(final PsiFile file, final InspectionManager manager, boolean onTheFly) {
    myFile = file;
    myOnTheFly = onTheFly;
  }

  @Override
  public void addError(final String inspectionId,
                       final @NotNull IComponent component,
                       @Nullable IProperty prop,
                       @NotNull String errorMessage,
                       EditorQuickFixProvider @NotNull ... editorQuickFixProviders) {
    List<LocalQuickFix> quickFixes = new ArrayList<>();
    for (EditorQuickFixProvider provider : editorQuickFixProviders) {
      if (provider instanceof LocalQuickFixProvider) {
        LocalQuickFix[] localQuickFixes = ((LocalQuickFixProvider)provider).getQuickFixes();
        if (localQuickFixes != null) {
          ContainerUtil.addAll(quickFixes, localQuickFixes);
        }
      }
    }
    final ProblemDescriptorBase problemDescriptor = new FormElementProblemDescriptor(myFile, JDOMUtil.escapeText(errorMessage),
                                                                                     quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY),
                                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                                     true,
                                                                                     myOnTheFly,
                                                                                     component.getId(),
                                                                                     prop != null ? prop.getName() : null);
    FormElementNavigatable navigatable = new FormElementNavigatable(myFile.getProject(), myFile.getVirtualFile(),
                                                                    component.getId());
    problemDescriptor.setNavigatable(navigatable);
    myProblems.add(problemDescriptor);
  }

  public ProblemDescriptor[] result() {
    return myProblems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }
}
