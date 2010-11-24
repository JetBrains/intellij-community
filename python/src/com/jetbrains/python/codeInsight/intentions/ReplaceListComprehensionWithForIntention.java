package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenSeparatorGenerator;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyStatementListImpl;
import org.jetbrains.annotations.NotNull;
import sun.tools.tree.Statement;

import javax.swing.*;
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
    PyListCompExpression expression =
      PsiTreeUtil.getTopmostParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyListCompExpression.class);
    if (expression == null) {
      return;
    }
    PsiElement parent = expression.getParent();
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

    if (parent instanceof PyAssignmentStatement) {

      PsiElement leftExpr = ((PyAssignmentStatement)parent).getLeftHandSideExpression();
      PyAssignmentStatement initAssignment = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyAssignmentStatement.class,
                                                                         leftExpr.getText() + " = []");
      PyStatement result = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyStatement.class,
                                                          leftExpr.getText() + ".append("+ getResult(expression).getText() +")");
      PyForStatement forStatement = createForLoop(expression, elementGenerator, result);

      PyStatementList stList = new PyStatementListImpl(initAssignment.getNode());
      stList.add(initAssignment);
      stList.add(forStatement);
      stList.getStatements()[0].delete();
      parent.replace(stList);

    }
    else if (parent instanceof PyPrintStatement) {
      PyStatement result = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyStatement.class,
                                                          "print " + "(" + getResult(expression).getText() +")");
      PyForStatement forStatement = createForLoop(expression, elementGenerator, result);
      parent.replace(forStatement);
    }
  }

  private static PyForStatement createForLoop(PyListCompExpression expression, PyElementGenerator elementGenerator, PyStatement result) {
    List <ComprhForComponent> forComps = expression.getForComponents();

    if (forComps.size() != 0) {
      ComprhForComponent forComponent = forComps.get(0);
      PyForStatement forStatement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyForStatement.class,
                             "for " + forComponent.getIteratorVariable().getText()  + " in " +
                             forComponent.getIteratedList().getText() + ":\n  a+1");

      List<ComprhIfComponent> ifComps = expression.getIfComponents();
      if (ifComps.size() != 0) {
        addIfComponents(forStatement, ifComps, elementGenerator);
      }
      addForComponents(forStatement, expression.getResultExpression(), elementGenerator, result);
      return forStatement;
    }
    return null;
  }

  private static void addIfComponents(PyForStatement forStatement,
                               List <ComprhIfComponent> ifComps,
                               PyElementGenerator elementGenerator) {
    PyStatementList pyStatementList = forStatement.getForPart().getStatementList();
    for (ComprhIfComponent ifComp : ifComps) {
      PyIfStatement ifStat = elementGenerator.createFromText(LanguageLevel.forElement(forStatement), PyIfStatement.class,
                           "if " + ifComp.getTest().getText() + ":\n  a+1");
      pyStatementList.getStatements()[0].replace(ifStat);
      pyStatementList = ((PyIfStatement)pyStatementList.getStatements()[0]).getIfPart().getStatementList();
    }
  }

  private static PyElement getResult(PyListCompExpression expression) {
    PyElement result = expression.getResultExpression();
    if (result instanceof PyListCompExpression) {
      return getResult((PyListCompExpression)result);
    }
    return result;
  }


  private static void addForComponents(PyForStatement statement, PyExpression expression, PyElementGenerator elementGenerator, PsiElement result) {
    PyStatement pyStatement = statement.getForPart().getStatementList().getStatements()[0];
    while (pyStatement instanceof PyIfStatement) {
      pyStatement = ((PyIfStatement)pyStatement).getIfPart().getStatementList().getStatements()[0];
    }

    if (expression instanceof PyListCompExpression) {
      List <ComprhForComponent> forComps = ((PyListCompExpression)expression).getForComponents();
      if ( forComps.size() != 0) {
        ComprhForComponent comp = forComps.get(0);
        PyForStatement pyForStatement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyForStatement.class,
                               "for " + comp.getIteratorVariable().getText()  + " in "+ comp.getIteratedList().getText() + ":\n  a+1");
        List<ComprhIfComponent> ifComps = ((PyListCompExpression)expression).getIfComponents();
        if (ifComps.size() != 0) {
          addIfComponents(pyForStatement, ifComps, elementGenerator);
        }
        addForComponents(pyForStatement, ((PyListCompExpression)expression).getResultExpression(), elementGenerator, result);
        pyStatement.replace(pyForStatement);
      }
    }
    else {
      pyStatement.replace(result);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}