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

  @Override
  protected PsiElement addDeclaration(@NotNull final PsiElement expression,
                                      @NotNull final PsiElement declaration,
                                      @NotNull final List<PsiElement> occurrences,
                                      final boolean replaceAll,
                                      InitPlace initInConstructor) {
    return doIntroduceVariable(expression, declaration, occurrences, replaceAll);
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

  private static PsiElement findAnchor(List<PsiElement> occurrences) {
    PsiElement anchor = occurrences.get(0);
    next:
    do {
      PyStatement statement = PsiTreeUtil.getParentOfType(anchor, PyStatement.class);

      final PsiElement parent = statement.getParent();
      for (PsiElement element : occurrences) {
        if (!PsiTreeUtil.isAncestor(parent, element, true)) {
          anchor = statement;
          continue next;
        }
      }

      return statement;
    }
    while (true);
  }

  @Override
  protected String getHelpId() {
    return "refactoring.introduceVariable";
  }
}
