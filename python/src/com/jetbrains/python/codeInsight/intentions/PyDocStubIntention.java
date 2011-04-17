package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
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
    if (function != null) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()),
                                                                          PyDocStringOwner.class);
      if (docStringOwner != null) {
        if (docStringOwner.getDocStringExpression() != null) return false;
      }
      if (function.getStatementList() != null && function.getStatementList().getStatements().length != 0)
        return true;
    }
    return  false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyFunction function = PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyFunction.class);
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PythonDocumentationProvider documentationProvider = new PythonDocumentationProvider();
    PyStatementList list = function.getStatementList();
    PsiWhiteSpace whitespace = PsiTreeUtil.getPrevSiblingOfType(list, PsiWhiteSpace.class);
    String docContent = documentationProvider.generateDocumentationContentStub(function, (whitespace != null? whitespace.getText() : "\n"));
    PyExpressionStatement string = elementGenerator.createFromText(LanguageLevel.forElement(function), PyExpressionStatement.class,
                                                                       "\"\"\"" + docContent + "\"\"\"");
    if (list.getStatements().length != 0)
      list.addBefore(string, list.getStatements()[0]);
  }

}
