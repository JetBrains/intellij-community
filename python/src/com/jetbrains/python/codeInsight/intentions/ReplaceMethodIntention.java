package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:50:53
 */
public class ReplaceMethodIntention implements IntentionAction {
  private final String myNewName;

  public ReplaceMethodIntention(String newName) {
    myNewName = newName;
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.replace.method");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyCallExpression problemElement =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyCallExpression.class);
    if (problemElement != null) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      problemElement.getCallee().replace(elementGenerator.createCallExpression(LanguageLevel.forElement(problemElement), myNewName).getCallee());
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}