// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonTemplateRunner;
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

  @Override
  @NotNull
  public String getName() {
    if (myAnchor.getElement() instanceof PyFile) {
      return PyPsiBundle.message("QFIX.create.class.in.module", myClassName, ((PyFile)myAnchor.getElement()).getName());
    }
    return PyPsiBundle.message("QFIX.create.class.0", myClassName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyPsiBundle.message("QFIX.create.class");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement anchor = myAnchor.getElement();
    if (anchor == null || !anchor.isValid()) {
      return;
    }

    if (!(anchor instanceof PyFile)) {
      anchor = PyPsiUtils.getParentRightBefore(anchor, anchor.getContainingFile());
      assert anchor != null;
    }

    final boolean isPy3K = LanguageLevel.forElement(anchor).isPy3K();
    String superClass = isPy3K ? "" : "(object)";
    PyClass pyClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class,
                                                                             "class " + myClassName + superClass + ":\n    pass");
    if (anchor instanceof PyFile) {
      pyClass = (PyClass) anchor.add(pyClass);
    }
    else {
      pyClass = (PyClass) anchor.getParent().addBefore(pyClass, anchor);
    }
    pyClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(pyClass);
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(pyClass);
    if (!isPy3K) {
      builder.replaceElement(pyClass.getSuperClassExpressions() [0], "object");
    }
    builder.replaceElement(pyClass.getStatementList(), PyNames.PASS);

    PythonTemplateRunner.runTemplate(pyClass.getContainingFile(), builder);
  }

}
