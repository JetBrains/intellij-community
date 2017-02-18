/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CreateClassQuickFix implements LocalQuickFix {
  private final String myClassName;
  private final SmartPsiElementPointer<PsiElement> myAnchor;

  public CreateClassQuickFix(String className, PsiElement anchor) {
    myClassName = className;

    final Project project = anchor.getProject();
    final PsiLanguageInjectionHost injectionHost = InjectedLanguageManager.getInstance(project).getInjectionHost(anchor);
    final PsiElement notInjectedAnchor = injectionHost != null ? injectionHost : anchor;
    
    myAnchor = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(notInjectedAnchor);
  }

  @NotNull
  public String getName() {
    if (myAnchor instanceof PyFile) {
      return "Create class '" + myClassName + "' in module " + ((PyFile)myAnchor).getName();
    }
    return "Create class '" + myClassName + "'";
  }

  @NotNull
  public String getFamilyName() {
    return "Create class";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement anchor = myAnchor.getElement();
    if (anchor == null || !anchor.isValid()) {
      return;
    }

    if (!(anchor instanceof PyFile)) {
      anchor = PyPsiUtils.getParentRightBefore(anchor, anchor.getContainingFile());
      assert anchor != null;
    }

    PyClass pyClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class,
                                                                             "class " + myClassName + "(object):\n    pass");
    if (anchor instanceof PyFile) {
      pyClass = (PyClass) anchor.add(pyClass);
    }
    else {
      pyClass = (PyClass) anchor.getParent().addBefore(pyClass, anchor);
    }
    pyClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(pyClass);
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(pyClass);
    builder.replaceElement(pyClass.getSuperClassExpressions() [0], "object");
    builder.replaceElement(pyClass.getStatementList(), PyNames.PASS);
    

    final FileEditor editor = FileEditorManager.getInstance(project).getSelectedEditor(anchor.getContainingFile().getVirtualFile());
    if (!(editor instanceof TextEditor)) {
      return;
    }
    
    builder.run(((TextEditor)editor).getEditor(), false);
  }

}
