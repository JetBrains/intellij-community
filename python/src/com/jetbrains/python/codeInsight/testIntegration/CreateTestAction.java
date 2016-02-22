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
package com.jetbrains.python.codeInsight.testIntegration;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.testing.pytest.PyTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: catherine
 */
public class CreateTestAction extends PsiElementBaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.create.test");
  }


  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PyClass psiClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

    if (psiClass != null && PyTestUtil.isPyTestClass(psiClass, null))
      return false;
    return true;
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PyFunction srcFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    final PyClass srcClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

    if (srcClass == null && srcFunction == null) return;

    final PsiDirectory dir = element.getContainingFile().getContainingDirectory();
    final CreateTestDialog d = new CreateTestDialog(project);
    if (srcClass != null) {
      d.setClassName("Test" + StringUtil.capitalize(srcClass.getName()));
      d.setFileName("test_" + StringUtil.decapitalize(srcClass.getName()) + ".py");

      if (dir != null)
        d.setTargetDir(dir.getVirtualFile().getPath());

      if (srcFunction != null) {
        d.methodsSize(1);
        d.addMethod("test_" + srcFunction.getName(), 0);
      }
      else {
        final List<PyFunction> methods = Lists.newArrayList();
        srcClass.visitMethods(new Processor<PyFunction>() {
          @Override
          public boolean process(PyFunction pyFunction) {
            if (pyFunction.getName() != null && !pyFunction.getName().startsWith("__"))
              methods.add(pyFunction);
            return true;
          }
        }, false, null);

        d.methodsSize(methods.size());
        int i = 0;
        for (PyFunction f : methods) {
          d.addMethod("test_" + f.getName(), i);
          ++i;
        }
      }
    }
    else {
      d.setClassName("Test" + StringUtil.capitalize(srcFunction.getName()));
      d.setFileName("test_" + StringUtil.decapitalize(srcFunction.getName()) + ".py");
      if (dir != null)
        d.setTargetDir(dir.getVirtualFile().getPath());

      d.methodsSize(1);
      d.addMethod("test_" + srcFunction.getName(), 0);
    }

    if (!d.showAndGet()) {
      return;
    }
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        PsiFile e = PyTestCreator.generateTestAndNavigate(project, d);
        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        documentManager.commitAllDocuments();
      }
    }, CodeInsightBundle.message("intention.create.test"), this);
  }
}