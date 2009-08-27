package com.jetbrains.python.refactoring.introduce.constant;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 25, 2009
 * Time: 6:48:16 PM
 */
public class ConstantIntroduceHandler extends IntroduceHandler {
  public ConstantIntroduceHandler() {
    super(new ConstantValidator(), PyBundle.message("refactoring.introduce.constant.dialog.title"));
  }

  protected PsiElement addDeclaration(@NotNull final PsiElement expression,
                                      @NotNull final PsiElement declaration,
                                      @NotNull final List<PsiElement> occurrences,
                                      final boolean replaceAll) {
    PsiElement anchor;
    anchor = expression.getContainingFile();
    assert anchor instanceof PyFile;
    return anchor.addBefore(declaration, ((PyFile)anchor).getStatements().get(0));
  }

  protected String[] getSuggestedNames(@NotNull final PyExpression expression) {
    Collection<String> names = new HashSet<String>();
    for (String name : super.getSuggestedNames(expression)) {
      names.add(name.toUpperCase());
    }
    return names.toArray(new String[names.size()]);
  }
}
