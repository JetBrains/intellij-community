package org.jetbrains.postfixCompletion.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.templates.PostfixTemplate;

public abstract class CommonUtils {
  private CommonUtils() {
  }

  public static void showErrorHint(Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, "Can't perform postfix completion", "Can't perform postfix completion", "");
  }

  public static void createSimpleStatement(@NotNull PsiElement context, @NotNull Editor editor, @NotNull String text) {
    PsiExpression expr = PostfixTemplate.getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    assert parent instanceof PsiStatement;
    PsiElementFactory factory = JavaPsiFacade.getInstance(context.getProject()).getElementFactory();
    PsiStatement assertStatement = factory.createStatementFromText(text + " " + expr.getText() + ";", parent);
    PsiElement replace = parent.replace(assertStatement);
    editor.getCaretModel().moveToOffset(replace.getTextRange().getEndOffset());
  }
}

