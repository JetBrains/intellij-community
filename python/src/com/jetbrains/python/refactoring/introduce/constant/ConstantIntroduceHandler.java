package com.jetbrains.python.refactoring.introduce.constant;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
public class ConstantIntroduceHandler extends IntroduceHandler {
  public ConstantIntroduceHandler() {
    super(new ConstantValidator(), PyBundle.message("refactoring.introduce.constant.dialog.title"));
  }

  @Override
  protected PsiElement addDeclaration(@NotNull final PsiElement expression,
                                      @NotNull final PsiElement declaration,
                                      @NotNull final List<PsiElement> occurrences,
                                      final boolean replaceAll,
                                      InitPlace initInConstructor) {
    PsiElement anchor;
    anchor = expression.getContainingFile();
    assert anchor instanceof PyFile;
    final List<PyStatement> statements = ((PyFile)anchor).getStatements();
    int targetIndex = 0;
    while(targetIndex < statements.size() && statements.get(targetIndex) instanceof PyImportStatementBase) {
      targetIndex++;
    }
    if (targetIndex == statements.size()) {
      return anchor.add(declaration);
    }
    return anchor.addBefore(declaration, statements.get(targetIndex));
  }

  public Collection<String> getSuggestedNames(@NotNull final PyExpression expression) {
    Collection<String> names = new HashSet<String>();
    for (String name : super.getSuggestedNames(expression)) {
      names.add(name.toUpperCase());
    }
    return names;
  }

  @Override
  protected String getHelpId() {
    return "refactoring.introduceConstant";
  }
}
