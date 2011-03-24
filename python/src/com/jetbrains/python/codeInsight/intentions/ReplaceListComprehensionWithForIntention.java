package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyStatementListImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: catherine
 */
public class ReplaceListComprehensionWithForIntention implements IntentionAction {
  @NotNull
  public String getText() {
    return PyBundle.message("INTN.replace.list.comprehensions.with.for");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.replace.list.comprehensions.with.for");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PyListCompExpression expression =
      PsiTreeUtil.getTopmostParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyListCompExpression.class);
    if (expression == null) {
      return false;
    }

    PsiElement parent = expression.getParent();
    if (parent instanceof PyAssignmentStatement || parent instanceof PyPrintStatement) {
      return true;
    }
    return false;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PyListCompExpression expression = PsiTreeUtil.getTopmostParentOfType(
                                          file.findElementAt(editor.getCaretModel().getOffset()), PyListCompExpression.class);
    if (expression == null) {
      return;
    }
    PsiElement parent = expression.getParent();
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    if (parent instanceof PyAssignmentStatement) {
      PsiElement leftExpr = ((PyAssignmentStatement)parent).getLeftHandSideExpression();
      PyAssignmentStatement initAssignment = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyAssignmentStatement.class,
                                                                         leftExpr.getText() + " = []");
      PyForStatement forStatement = createForLoop(expression, elementGenerator,
                                                  leftExpr.getText() + ".append("+ getResult(expression).getText() +")");

      PyStatementList stList = new PyStatementListImpl(initAssignment.getNode());
      stList.add(initAssignment);
      stList.add(forStatement);
      stList.getStatements()[0].delete();
      parent.replace(stList);

    }
    else if (parent instanceof PyPrintStatement) {
      PyForStatement forStatement = createForLoop(expression, elementGenerator, "print " + "(" + getResult(expression).getText() +")");
      parent.replace(forStatement);
    }
  }

  private static PyForStatement createForLoop(PyListCompExpression expression, PyElementGenerator elementGenerator, String result) {
    List<ComprehensionComponent> components = expression.getComponents();
    StringBuilder stringBuilder = new StringBuilder();
    int slashNum = 1;
    for (ComprehensionComponent component : components) {
      if (component instanceof ComprhForComponent) {
        stringBuilder.append("for " + ((ComprhForComponent)component).getIteratorVariable().getText()  + " in " +
                             ((ComprhForComponent)component).getIteratedList().getText() + ":\n");
      }
      if (component instanceof ComprhIfComponent) {
        stringBuilder.append("if " + ((ComprhIfComponent)component).getTest().getText() + ":\n");
      }
      for (int i = 0; i != slashNum; ++i)
        stringBuilder.append("\t");
      ++slashNum;
    }
    stringBuilder.append(result);
    return elementGenerator.createFromText(LanguageLevel.forElement(expression), PyForStatement.class,
                             stringBuilder.toString());
  }

  private static PyElement getResult(PyListCompExpression expression) {
    PyElement result = expression.getResultExpression();
    if (result instanceof PyListCompExpression) {
      return getResult((PyListCompExpression)result);
    }
    return result;
  }

 public boolean startInWriteAction() {
    return true;
  }
}