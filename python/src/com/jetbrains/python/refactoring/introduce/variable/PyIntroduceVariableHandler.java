package com.jetbrains.python.refactoring.introduce.variable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.InplaceVariableIntroducer;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class PyIntroduceVariableHandler extends IntroduceHandler {
  public PyIntroduceVariableHandler() {
    super(new VariableValidator(), PyBundle.message("refactoring.introduce.variable.dialog.title"));
  }

  @Override
  protected PsiElement addDeclaration(@NotNull final PsiElement expression,
                                      @NotNull final PsiElement declaration,
                                      @NotNull IntroduceOperation operation) {
    return doIntroduceVariable(expression, declaration, operation.getOccurrences(), operation.isReplaceAll());
  }

  public static PsiElement doIntroduceVariable(PsiElement expression,
                                               PsiElement declaration,
                                               List<PsiElement> occurrences,
                                               boolean replaceAll) {
    PsiElement anchor = replaceAll ? findAnchor(occurrences) : PsiTreeUtil.getParentOfType(expression, PyStatement.class);
    assert anchor != null;
    final PsiElement parent = anchor.getParent();
    return parent.addBefore(declaration, anchor);
  }

  @Override
  protected String getHelpId() {
    return "python.reference.introduceVariable";
  }

  @Override
  protected void performActionOnElementOccurrences(final IntroduceOperation operation) {
    final Editor editor = operation.getEditor();
    if (editor.getSettings().isVariableInplaceRenameEnabled() && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (operation.getName() == null) {
        final Collection<String> suggestedNames = operation.getSuggestedNames();
        if (suggestedNames.size() > 0) {
          operation.setName(suggestedNames.iterator().next());
        }
        else {
          operation.setName("x");
        }
      }
      new OccurrencesChooser<PsiElement>(editor)
              .showChooser(operation.getElement(), operation.getOccurrences(), new Pass<OccurrencesChooser.ReplaceChoice>() {
                @Override
                public void pass(OccurrencesChooser.ReplaceChoice replaceChoice) {
                  operation.setReplaceAll(replaceChoice == OccurrencesChooser.ReplaceChoice.ALL);
                  final PyAssignmentStatement statement = performRefactoring(operation);
                  PyTargetExpression target = (PyTargetExpression) statement.getTargets() [0];
                  final List<PsiElement> occurrences = operation.getOccurrences();
                  final InplaceVariableIntroducer<PsiElement> introducer =
                          new PyInplaceVariableIntroducer(target, operation, occurrences);
                  introducer.performInplaceRename(false, new LinkedHashSet<String>(operation.getSuggestedNames()));
                }
              });
    }
    else {
      super.performActionOnElementOccurrences(operation);
    }
  }

  private static class PyInplaceVariableIntroducer extends InplaceVariableIntroducer<PsiElement> {
    private final PyTargetExpression myTarget;

    public PyInplaceVariableIntroducer(PyTargetExpression target,
                                       IntroduceOperation operation,
                                       List<PsiElement> occurrences) {
      super(target, operation.getEditor(), operation.getProject(), "Introduce Variable",
            occurrences.toArray(new PsiElement[occurrences.size()]), null);
      myTarget = target;
    }

    @Override
    protected PsiElement checkLocalScope() {
      return myTarget.getContainingFile();
    }
  }
}
