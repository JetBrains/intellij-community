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

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.make.FormElementNavigatable;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.JDOMUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class FormFileErrorCollector extends FormErrorCollector {
  private final InspectionManager myManager;
  private final PsiFile myFile;
  private final List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();

  public FormFileErrorCollector(final PsiFile file, final InspectionManager manager) {
    myManager = manager;
    myFile = file;
  }

  public void addError(final String inspectionId, final IComponent component, @Nullable IProperty prop,
                       @NotNull String errorMessage,
                       @Nullable EditorQuickFixProvider editorQuickFixProvider) {
    final ProblemDescriptor problemDescriptor = myManager.createProblemDescriptor(myFile, JDOMUtil.escapeText(errorMessage),
                                                                                  (LocalQuickFix)null,
                                                                                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    if (problemDescriptor instanceof ProblemDescriptorImpl && component != null) {
      FormElementNavigatable navigatable = new FormElementNavigatable(myFile.getProject(), myFile.getVirtualFile(),
                                                                      component.getId());
      ((ProblemDescriptorImpl) problemDescriptor).setNavigatable(navigatable);
    }
    myProblems.add(problemDescriptor);
  }

  public ProblemDescriptor[] result() {
    return myProblems.toArray(new ProblemDescriptor[myProblems.size()]);
  }
}
