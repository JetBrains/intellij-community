package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 * Intention to add documentation string for function
 * (with checked format)
 */
public class PyDocStubIntention extends BaseIntentionAction {

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.doc.string.stub");
  }

  @NotNull
  @Override
  public String getText() {
    return PyBundle.message("INTN.doc.string.stub");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyFunction function = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyFunction.class);
    PyStatementList list = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyStatementList.class,
                                                       false, PyFunction.class);
    if (function != null && list == null) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                          PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() != null) return false;
      }
      final PyStatementList statementList = function.getStatementList();
      if (statementList != null && statementList.getStatements().length != 0)
        return true;
    }
    return  false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(file)) return;
    PyFunction function = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyFunction.class);
    PythonDocumentationProvider.inserDocStub(function, project, editor);


  }

}
