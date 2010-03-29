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

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 20.02.2010
 * Time: 15:49:35
 */
public class ConvertDictCompIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.dict.comp.to");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.dict.comp.expression");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && !LanguageLevel.forFile(virtualFile).isPy3K()) {
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PyDictCompExpression expression = PsiTreeUtil.getParentOfType(element, PyDictCompExpression.class);
      if (expression == null) {
        return false;
      }
      return expression.getResultExpression() instanceof PyKeyValueExpression;
    }
    return false;
  }

  // TODO: {k, v for k in range(4) for v in range(4)}
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && !LanguageLevel.forFile(virtualFile).isPy3K()) {
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PyDictCompExpression expression = PsiTreeUtil.getParentOfType(element, PyDictCompExpression.class);
      assert expression != null;
      replaceComprehension(project, expression);
    }
  }

  private static void replaceComprehension(Project project, PyDictCompExpression expression) {
    List<ComprhForComponent> forComponents = expression.getForComponents();
    if (expression.getResultExpression() instanceof PyKeyValueExpression) {
      PyKeyValueExpression keyValueExpression = (PyKeyValueExpression)expression.getResultExpression();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      assert keyValueExpression.getValue() != null;
      expression.replace(elementGenerator.createFromText(PyExpressionStatement.class,
                                                         "dict([(" + keyValueExpression.getKey().getText() + ", " +
                                                         keyValueExpression.getValue().getText() + ") for " +
                                                         forComponents.get(0).getIteratorVariable().getText() + " in " +
                                                         forComponents.get(0).getIteratedList().getText() + "])"));
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
