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
package com.intellij.uiDesigner.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.InspectionMessage;
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

/**
 * @author yole
 */
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
                       @NotNull final IComponent component,
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
