package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 16.02.2010
 * Time: 21:33:28
 */
public class ConvertSetLiteralIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.set.literal.to");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.set.literal");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && !LanguageLevel.forFile(virtualFile).isPy3K()) {
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PySetLiteralExpression setLiteral = PsiTreeUtil.getParentOfType(element, PySetLiteralExpression.class);
      return (setLiteral != null);
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && !LanguageLevel.forFile(virtualFile).isPy3K()) {
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PySetLiteralExpression setLiteral = PsiTreeUtil.getParentOfType(element, PySetLiteralExpression.class);
      assert setLiteral != null;
      PyExpression[] expressions = setLiteral.getElements();
      if (expressions != null) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        assert expressions.length != 0;
        StringBuilder stringBuilder = new StringBuilder(expressions[0].getText());
        for (int i = 1; i < expressions.length; ++i) {
          stringBuilder.append(", ");
          stringBuilder.append(expressions[i].getText());
        }
        PyStatement newElement = elementGenerator.createFromText(PyExpressionStatement.class, "set([" + stringBuilder.toString() + "])");
        setLiteral.replace(newElement);
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
