package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: ktisha
 */
public class PyConvertStaticMethodToFunctionIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.static.method.to.function");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.static.method.to.function");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function == null) return false;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) return false;
    final PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList != null) {
      final PyDecorator staticMethod = decoratorList.findDecorator(PyNames.STATICMETHOD);
      if (staticMethod != null) return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;
    final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);
    final PyDecoratorList decoratorList = problemFunction.getDecoratorList();
    if (decoratorList != null) {
      final PyDecorator decorator = decoratorList.findDecorator(PyNames.STATICMETHOD);
      if (decorator != null) decorator.delete();
    }
    final PyElementGenerator generator = PyElementGenerator.getInstance(project);

    final PsiElement copy = problemFunction.copy();
    final PyStatementList classStatementList = containingClass.getStatementList();
    classStatementList.deleteChildRange(problemFunction, problemFunction);
    if (classStatementList.getStatements().length < 1) {
      classStatementList.add(generator.createPassStatement());
    }
    file.addAfter(copy, containingClass);

    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        PyUtil.removeQualifier((PyReferenceExpression)usageElement);
      }
    }
  }
}
