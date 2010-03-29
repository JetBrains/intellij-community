package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:16:52
 */
public class ReplaceNotEqOperatorIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.replace.noteq.operator");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyBinaryExpression.class);
    if (binaryExpression == null) {
      return;
    }
    PsiElement operator = binaryExpression.getPsiOperator();
    if (operator != null) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      operator.replace(elementGenerator.createFromText(LeafPsiElement.class, "!="));
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
