package com.jetbrains.python.refactoring;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.classes.PyDependenciesComparator;
import org.jetbrains.annotations.NotNull;

public class PyPsiRefactoringUtil {
  /**
   * Adds element to statement list to the correct place according to its dependencies.
   *
   * @param element       to insert
   * @param statementList where element should be inserted
   * @return inserted element
   */
  public static <T extends PyElement> T addElementToStatementList(@NotNull final T element,
                                                                  @NotNull final PyStatementList statementList) {
    PsiElement before = null;
    PsiElement after = null;
    for (final PyStatement statement : statementList.getStatements()) {
      if (PyDependenciesComparator.depends(element, statement)) {
        after = statement;
      }
      else if (PyDependenciesComparator.depends(statement, element)) {
        before = statement;
      }
    }
    final PsiElement result;
    if (after != null) {

      result = statementList.addAfter(element, after);
    }
    else if (before != null) {
      result = statementList.addBefore(element, before);
    }
    else {
      result = addElementToStatementList(element, statementList, true);
    }
    @SuppressWarnings("unchecked") // Inserted element can't have different type
    final T resultCasted = (T)result;
    return resultCasted;
  }

  /**
   * Inserts specified element into the statement list either at the beginning or at its end. If new element is going to be
   * inserted at the beginning, any preceding docstrings and/or calls to super methods will be skipped.
   * Moreover if statement list previously didn't contain any statements, explicit new line and indentation will be inserted in
   * front of it.
   *
   * @param element        element to insert
   * @param statementList  statement list
   * @param toTheBeginning whether to insert element at the beginning or at the end of the statement list
   * @return actually inserted element as for {@link PsiElement#add(PsiElement)}
   */
  @NotNull
  public static PsiElement addElementToStatementList(@NotNull PsiElement element,
                                                     @NotNull PyStatementList statementList,
                                                     boolean toTheBeginning) {
    final PsiElement prevElem = PyPsiUtils.getPrevNonWhitespaceSibling(statementList);
    // If statement list is on the same line as previous element (supposedly colon), move its only statement on the next line
    if (prevElem != null && PyUtil.onSameLine(statementList, prevElem)) {
      final PsiDocumentManager manager = PsiDocumentManager.getInstance(statementList.getProject());
      final Document document = manager.getDocument(statementList.getContainingFile());
      if (document != null) {
        final PyStatementListContainer container = (PyStatementListContainer)statementList.getParent();
        manager.doPostponedOperationsAndUnblockDocument(document);
        final String indentation = "\n" + PyIndentUtil.getElementIndent(statementList);
        // If statement list was empty initially, we need to add some anchor statement ("pass"), so that preceding new line was not
        // parsed as following entire StatementListContainer (e.g. function). It's going to be replaced anyway.
        final String text = statementList.getStatements().length == 0 ? indentation + PyNames.PASS : indentation;
        document.insertString(statementList.getTextRange().getStartOffset(), text);
        manager.commitDocument(document);
        statementList = container.getStatementList();
      }
    }
    final PsiElement firstChild = statementList.getFirstChild();
    if (firstChild == statementList.getLastChild() && firstChild instanceof PyPassStatement) {
      element = firstChild.replace(element);
    }
    else {
      final PyStatement[] statements = statementList.getStatements();
      if (toTheBeginning && statements.length > 0) {
        final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(statementList, PyDocStringOwner.class);
        PyStatement anchor = statements[0];
        if (docStringOwner != null && anchor instanceof PyExpressionStatement &&
            ((PyExpressionStatement)anchor).getExpression() == docStringOwner.getDocStringExpression()) {
          final PyStatement next = PsiTreeUtil.getNextSiblingOfType(anchor, PyStatement.class);
          if (next == null) {
            return statementList.addAfter(element, anchor);
          }
          anchor = next;
        }
        while (anchor instanceof PyExpressionStatement) {
          final PyExpression expression = ((PyExpressionStatement)anchor).getExpression();
          if (expression instanceof PyCallExpression) {
            final PyExpression callee = ((PyCallExpression)expression).getCallee();
            if ((PyUtil.isSuperCall((PyCallExpression)expression) || (callee != null && PyNames.INIT.equals(callee.getName())))) {
              final PyStatement next = PsiTreeUtil.getNextSiblingOfType(anchor, PyStatement.class);
              if (next == null) {
                return statementList.addAfter(element, anchor);
              }
              anchor = next;
              continue;
            }
          }
          break;
        }
        element = statementList.addBefore(element, anchor);
      }
      else {
        element = statementList.add(element);
      }
    }
    return element;
  }
}
