package com.jetbrains.python.refactoring.introduce.parameter;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import com.jetbrains.python.refactoring.introduce.variable.VariableValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * User: ktisha
 */
public class PyIntroduceParameterHandler extends IntroduceHandler {
  public PyIntroduceParameterHandler() {
    super(new VariableValidator(), PyBundle.message("refactoring.introduce.parameter.dialog.title"));
  }

  @Override
  protected String getHelpId() {
    return "python.reference.introduceParameter";
  }

  @Nullable
  @Override
  protected PsiElement addDeclaration(@NotNull PsiElement expression,
                                      @NotNull PsiElement declaration,
                                      @NotNull IntroduceOperation operation) {
    return doIntroduceParameter(expression, (PyAssignmentStatement)declaration);
  }


  public PsiElement doIntroduceParameter(PsiElement expression, PyAssignmentStatement declaration) {
    PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
    if (function != null && declaration != null) {
      PyParameterList parameterList = function.getParameterList();
      parameterList.addParameter(PyElementGenerator.getInstance(function.getProject()).createParameter(declaration.getText()));
      CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(function);
      return parameterList.findParameterByName(declaration.getTargets()[0].getText());
    }
    return null;
  }

  @Override
  protected boolean checkIntroduceContext(PsiFile file, Editor editor, PsiElement element) {
    if (element != null) {
      final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
      if (function == null) {
        CommonRefactoringUtil.showErrorHint(file.getProject(), editor,
                                            "Introduce Parameter refactoring cannot be performed outside any function",
                                            RefactoringBundle.message("introduce.parameter.title"), null);
        return false;
      }
      if (isResolvedToParameter(element)) {
        CommonRefactoringUtil.showErrorHint(file.getProject(), editor,
                                            PyBundle.message("refactoring.introduce.selection.error"),
                                            RefactoringBundle.message("introduce.parameter.title"), null);
        return false;
      }
    }
    return super.checkIntroduceContext(file, editor, element);
  }

  private boolean isResolvedToParameter(PsiElement element) {
    while (element instanceof PyReferenceExpression) {
      final PsiReference reference = element.getReference();
      if (reference != null && reference.resolve() instanceof PyNamedParameter)
        return true;
      element = ((PyReferenceExpression)element).getQualifier();
    }
    return false;
  }

  @Override
  protected void performInplaceIntroduce(IntroduceOperation operation) {
    final PsiElement statement = performRefactoring(operation);
    if (statement instanceof PyNamedParameter) {
      final List<PsiElement> occurrences = operation.getOccurrences();
      final PsiElement occurrence = findOccurrenceUnderCaret(occurrences, operation.getEditor());
      PsiElement elementForCaret = occurrence != null ? occurrence : statement;
      operation.getEditor().getCaretModel().moveToOffset(elementForCaret.getTextRange().getStartOffset());
      final InplaceVariableIntroducer<PsiElement> introducer =
        new PyInplaceParameterIntroducer((PyNamedParameter)statement, operation, occurrences);
      introducer.performInplaceRefactoring(new LinkedHashSet<String>(operation.getSuggestedNames()));
    }
  }

  private static class PyInplaceParameterIntroducer extends InplaceVariableIntroducer<PsiElement> {
    private final PyNamedParameter myTarget;

    public PyInplaceParameterIntroducer(PyNamedParameter target,
                                       IntroduceOperation operation,
                                       List<PsiElement> occurrences) {
      super(target, operation.getEditor(), operation.getProject(), "Introduce Parameter",
            occurrences.toArray(new PsiElement[occurrences.size()]), null);
      myTarget = target;
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myTarget.getContainingFile();
    }
  }
}
