// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonTemplateRunner;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;


public class CreateClassQuickFix implements LocalQuickFix {
  private final @NotNull String myClassName;
  private final @NotNull SmartPsiElementPointer<PsiFile> myTargetFile;

  public CreateClassQuickFix(@NotNull String className, @NotNull PsiFile targetFile) {
    myClassName = className;
    myTargetFile = SmartPointerManager.getInstance(targetFile.getProject()).createSmartPsiElementPointer(targetFile);
  }

  @Override
  public @NotNull String getName() {
    PsiFile targetFile = myTargetFile.getElement();
    if (targetFile != null) {
      return PyPsiBundle.message("QFIX.create.class.in.module", myClassName, targetFile.getName());
    }
    return PyPsiBundle.message("QFIX.create.class.0", myClassName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PyPsiBundle.message("QFIX.create.class");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement unresolvedReference = descriptor.getPsiElement();
    if (unresolvedReference == null) {
      return;
    }

    PsiFile targetFile = myTargetFile.getElement();
    if (targetFile == null) {
      return;
    }

    final boolean isPy3K = LanguageLevel.forElement(targetFile).isPy3K();
    String superClass = isPy3K ? "" : "(object)";
    PyClass pyClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class,
                                                                             "class " + myClassName + superClass + ":\n" +
                                                                             "    pass");
    InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(unresolvedReference.getProject());
    PsiLanguageInjectionHost injectionHost = injectionManager.getInjectionHost(unresolvedReference);
    if (unresolvedReference.getContainingFile() == targetFile) {
      PsiElement insertionAnchor = PyPsiUtils.getParentRightBefore(unresolvedReference, targetFile);
      assert insertionAnchor != null;
      pyClass = (PyClass)insertionAnchor.getParent().addBefore(pyClass, insertionAnchor);
    }
    else if (injectionHost != null && injectionHost.getContainingFile() == targetFile) {
      PsiElement insertionAnchor = PyPsiUtils.getParentRightBefore(injectionHost, targetFile);
      assert insertionAnchor != null;
      pyClass = (PyClass)insertionAnchor.getParent().addBefore(pyClass, insertionAnchor);
    }
    else {
      pyClass = (PyClass)targetFile.add(pyClass);
    }
    pyClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(pyClass);
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(pyClass);
    if (!isPy3K) {
      builder.replaceElement(pyClass.getSuperClassExpressions()[0], "object");
    }
    builder.replaceElement(pyClass.getStatementList(), PyNames.PASS);

    PythonTemplateRunner.runTemplate(pyClass.getContainingFile(), builder);
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new CreateClassQuickFix(myClassName, target);
  }
}
