package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyTryExceptStatement;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: 16.02.2010
 * Time: 18:07:55
 */
public class ReplaceExceptPartIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.except.to");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.Family.migration.to.python3");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(element, PyExceptPart.class);
      return (exceptPart != null);
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyExceptPart.class);
    PyElementGenerator elementGenerator = PythonLanguage.getInstance().getElementGenerator();
    assert exceptPart != null;
    PsiElement element = exceptPart.getExceptClass().getNextSibling();
    while (element instanceof PsiWhiteSpace) {
      element = element.getNextSibling();
    }
    assert element != null;
    PyTryExceptStatement newElement =
      elementGenerator.createFromText(project, PyTryExceptStatement.class, "try:  pass except a as b:  pass");
    ASTNode node = newElement.getExceptParts()[0].getNode().findChildByType(PyTokenTypes.AS_KEYWORD);
    assert node != null;
    element.replace(node.getPsi());
  }

  public boolean startInWriteAction() {
    return true;
  }
}
