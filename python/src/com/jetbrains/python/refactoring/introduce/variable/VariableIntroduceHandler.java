package com.jetbrains.python.refactoring.introduce.variable;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 5:22:02 PM
 */
public class VariableIntroduceHandler extends IntroduceHandler {
  public VariableIntroduceHandler() {
    super(new VariableValidator(), PyBundle.message("refactoring.introduce.variable.dialog.title"));
  }

  // TODO: Add declaration before first usage
  @Override
  protected PsiElement addDeclaration(@NotNull final PsiElement expression,
                                      @NotNull final PsiElement declaration,
                                      @NotNull final List<PsiElement> occurrences,
                                      final boolean replaceAll) {
    PyStatement anchorStatement;
    if (replaceAll) {
      final PsiElement parent = PsiTreeUtil.findCommonParent(occurrences.toArray(new PsiElement[occurrences.size()]));
      assert parent != null;
      anchorStatement = (parent instanceof PyStatement) ? ((PyStatement)parent) : PsiTreeUtil.getChildOfType(parent, PyStatement.class);
    }
    else {
      anchorStatement = PsiTreeUtil.getParentOfType(expression, PyStatement.class);
    }
    assert anchorStatement != null;
    anchorStatement.getParent().addBefore(declaration, anchorStatement);
    return anchorStatement.getParent().getParent();
  }
}
