package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
 * Date: 19.02.2010
 * Time: 18:50:24
 */
public class ReplaceBuiltinsIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.builtin.import");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.convert.builtin");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
    return (importStatement != null);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
    for (PyImportElement importElement : importStatement.getImportElements()) {
      PyReferenceExpression importReference = importElement.getImportReference();
      if (importReference != null) {
        if (LanguageLevel.forFile(file.getVirtualFile()).isPy3K()) {
          if ("__builtin__".equals(importReference.getName())) {
            importReference.replace(elementGenerator.createFromText(PyReferenceExpression.class, "builtins"));
          }
        } else {
          if ("builtins".equals(importReference.getName())) {
            importReference.replace(elementGenerator.createFromText(PyReferenceExpression.class, "__builtin__"));
          }
        }
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
