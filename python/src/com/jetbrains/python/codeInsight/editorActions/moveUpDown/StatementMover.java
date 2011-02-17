package com.jetbrains.python.codeInsight.editorActions.moveUpDown;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.editorActions.moveUpDown.LineMover;
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Alexey.Ivanov
 */
public class StatementMover extends LineMover {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.codeInsight.editorActions.moveUpDown.StatementMover");

  private static PsiElement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset) {
    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    while (true) {
      if (parent instanceof PyStatement) {
        parent = parent.getParent();
        break;
      }
      if (parent instanceof PyStatementList) {
        break;
      }
      if (parent == null || parent instanceof PsiFile) {
        return PsiElement.EMPTY_ARRAY;
      }
      parent = parent.getParent();
    }

    if (!parent.equals(element1)) {
      while (!parent.equals(element1.getParent())) {
        element1 = element1.getParent();
      }
    }

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }

    PsiElement[] children = parent.getChildren();
    ArrayList<PsiElement> array = new ArrayList<PsiElement>();
    boolean flag = false;
    for (PsiElement child : children) {
      if (child.equals(element1)) {
        flag = true;
      }
      if (flag && !(child instanceof PsiWhiteSpace)) {
        array.add(child);
      }
      if (child.equals(element2)) {
        break;
      }
    }

    for (PsiElement element : array) {
      if (!(element instanceof PyStatement || element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
        return PsiElement.EMPTY_ARRAY;
      }
    }
    return PsiUtilBase.toPsiElementArray(array);
  }

  private static boolean isNotValidStatementRange(Pair<PsiElement, PsiElement> range) {
    return range == null ||
           range.first == null ||
           range.second == null ||
           range.first.getParent() != range.second.getParent();
  }

  @Nullable
  private static LineRange expandLineRange(@NotNull final LineRange range,
                                           @NotNull final Editor editor,
                                           @NotNull final PsiFile file) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    Pair<PsiElement, PsiElement> psiRange;
    if (selectionModel.hasSelection()) {
      final int startOffset = selectionModel.getSelectionStart();
      final int endOffset = selectionModel.getSelectionEnd();
      final PsiElement[] psiElements = findStatementsInRange(file, startOffset, endOffset);
      if (psiElements.length == 0) {
        return null;
      }
      psiRange = new Pair<PsiElement, PsiElement>(psiElements[0], psiElements[psiElements.length - 1]);
    }
    else {
      final int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      if (element == null) {
        return null;
      }
      psiRange = new Pair<PsiElement, PsiElement>(element, element);
    }

    psiRange = new Pair<PsiElement, PsiElement>(PsiTreeUtil.getNonStrictParentOfType(psiRange.getFirst(), PyStatement.class),
                                                PsiTreeUtil.getNonStrictParentOfType(psiRange.getSecond(), PyStatement.class));
    if (psiRange.getFirst() == null || psiRange.getSecond() == null) {
      return null;
    }

    final PsiElement parent = PsiTreeUtil.findCommonParent(psiRange.getFirst(), psiRange.getSecond());
    final Pair<PsiElement, PsiElement> elementRange = getElementRange(parent, psiRange.getFirst(), psiRange.getSecond());
    if (isNotValidStatementRange(elementRange)) {
      return null;
    }

    final PsiElement first = elementRange.getFirst();
    final PsiElement second = elementRange.getSecond();
    if (first == second && first instanceof PyPassStatement) {
      return null;
    }

    int startOffset = first.getTextRange().getStartOffset();
    int endOffset = second.getTextRange().getEndOffset();
    final Document document = editor.getDocument();
    if (endOffset > document.getTextLength()) {
      LOG.assertTrue(!PsiDocumentManager.getInstance(file.getProject()).isUncommited(document));
      LOG.assertTrue(PsiDocumentManagerImpl.checkConsistency(file, document));
    }

    int endLine;
    if (endOffset == document.getTextLength()) {
      endLine = document.getLineCount();
    }
    else {
      endLine = editor.offsetToLogicalPosition(endOffset).line + 1;
      endLine = Math.min(endLine, document.getLineCount());
    }
    endLine = Math.max(endLine, range.endLine);
    final int startLine = Math.min(range.startLine, editor.offsetToLogicalPosition(startOffset).line);
    return new LineRange(startLine, endLine);
  }

  private @Nullable PyStatementList myStatementListToAddPass;
  private @Nullable PyStatementList myStatementListToRemovePass;
  private @NotNull PsiElement[] myElementsToChangeIndent;
  private int myIndentLevel;

  @Override
  public boolean checkAvailable(@NotNull Editor editor,
                                @NotNull PsiFile file,
                                @NotNull MoveInfo info,
                                boolean down) {
    myStatementListToAddPass = null;
    myStatementListToRemovePass = null;
    myElementsToChangeIndent = PsiElement.EMPTY_ARRAY;
    myIndentLevel = 0;

    if (!(file instanceof PyFile)) {
      return false;
    }
    if (!super.checkAvailable(editor, file, info, down)) {
      return false;
    }
    info.indentSource = true;
    final LineRange range = expandLineRange(info.toMove, editor, file);
    if (range == null) {
      return false;
    }

    info.toMove = range;
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0));
    final PsiElement[] statements = findStatementsInRange(file, startOffset, endOffset);
    final int length = statements.length;
    if (length == 0) {
      return false;
    }

    range.firstElement = statements[0];
    range.lastElement = statements[length - 1];
    final Document document = editor.getDocument();
    PyStatement statement;
    assert info.toMove2.startLine + 1 == info.toMove2.endLine;
    if (down) {
      statement = PsiTreeUtil.getNextSiblingOfType(range.lastElement, PyStatement.class);
    }
    else {
      statement = PsiTreeUtil.getPrevSiblingOfType(range.firstElement, PyStatement.class);
    }

    if (statement == null) {
      final PyStatementPart parentStatementPart =
        PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(range.firstElement, range.lastElement), PyStatementPart.class, false);
      if (parentStatementPart == null) {
        info.toMove2 = null;
      }
      else {  //we are in statement part

        final PyStatementList statementList = parentStatementPart.getStatementList();
        assert statementList != null;
        if (down) {
          PyStatementPart nextStatementPart = PsiTreeUtil.getNextSiblingOfType(statementList.getParent(), PyStatementPart.class);
          if (nextStatementPart != null) {
            info.toMove2 = new LineRange(range.endLine, range.endLine + 1);
            myStatementListToRemovePass = nextStatementPart.getStatementList();
          }
          else {
            PsiElement parent = statementList;
            PyStatement nextStatement;
            while (true) {
              parent = PsiTreeUtil.getParentOfType(parent, PyStatement.class);
              if (parent == null) {
                return false;
              }
              nextStatement = PsiTreeUtil.getNextSiblingOfType(parent, PyStatement.class);
              if (nextStatement instanceof PyFunction || nextStatement instanceof PyClass) {
                return false;
              }
              if (nextStatement != null) {
                break;
              }
            }
            final int startLine = editor.offsetToLogicalPosition(parent.getTextRange().getEndOffset()).line;
            final int endLine = editor.offsetToLogicalPosition(nextStatement.getTextRange().getEndOffset()).line;
            info.toMove2 = new LineRange(startLine + 1, endLine + 1);
            nextStatementPart = PsiTreeUtil.getChildOfType(nextStatement, PyStatementPart.class);
            if (nextStatementPart != null) {
              calculateIndent(editor, statementList, nextStatementPart.getStatementList(), down);
            }
            else {
              calculateIndent(editor, statementList, nextStatement, down);
            }
            myElementsToChangeIndent = statements;
          }
        }
        else {
          final PyStatementPart prevStatementPart = PsiTreeUtil.getPrevSiblingOfType(statementList.getParent(), PyStatementPart.class);
          if (prevStatementPart != null) {
            myStatementListToRemovePass = prevStatementPart.getStatementList();
          }
          else {
            myIndentLevel = -1;
            myElementsToChangeIndent = statements;
            info.toMove2 = new LineRange(range.startLine - 1, range.startLine);
          }
        }
        if (Arrays.equals(statementList.getStatements(), statements)) {
          myStatementListToAddPass = statementList;
        }
      }
      return true;
    }

    info.toMove2 = new LineRange(statement, statement, document);
    final PyStatementPart[] statementParts = PsiTreeUtil.getChildrenOfType(statement, PyStatementPart.class);

    // next/previous statement has a statement parts
    // move inside statement part
    if (statementParts != null) {
      int startLineNumber;
      int endLineNumber;
      PyStatementPart statementPart;
      if (down) {
        statementPart = statementParts[0];
        startLineNumber = document.getLineNumber(statementPart.getTextRange().getStartOffset());
        endLineNumber = document.getLineNumber(statementPart.getTextRange().getEndOffset());
      }
      else {
        statementPart = statementParts[statementParts.length - 1];
        startLineNumber = document.getLineNumber(statementPart.getTextRange().getEndOffset());
        endLineNumber = document.getLineNumber(statementPart.getTextRange().getStartOffset());
      }

      if (startLineNumber != endLineNumber) {
        info.toMove2 = new LineRange(startLineNumber, startLineNumber + 1);
        myStatementListToRemovePass = statementPart.getStatementList();
        calculateIndent(editor, range.firstElement, myStatementListToRemovePass, down);
        myElementsToChangeIndent = statements;
      }
    }
    return true;
  }


  private void calculateIndent(final Editor editor, final PsiElement firstElement, final PsiElement secondElement, final boolean down) {
    final PsiFile file = firstElement.getContainingFile();
    final int firstIndent = getIndent(editor, file, editor.offsetToLogicalPosition(firstElement.getTextRange().getStartOffset()).line);
    int line;
    if (down) {
      line = editor.offsetToLogicalPosition(secondElement.getTextRange().getStartOffset()).line;
    }
    else {
      line = editor.offsetToLogicalPosition(secondElement.getTextRange().getEndOffset()).line;
    }
    final int secondIndent = getIndent(editor, file, line);
    myIndentLevel = (secondIndent - firstIndent) >> 2;
  }

  private static int getIndent(final Editor editor, final PsiFile file, final int lineNumber) {
    int indent = 0;
    final int offset = editor.logicalPositionToOffset(new LogicalPosition(lineNumber, 0));
    PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) {
      final String text = element.getText();
      String[] lines = text.split("\n");
      if (lines.length == 0) {
        return 0;
      }
      indent = lines[lines.length - 1].length();
    }
    return indent;
  }

  private void decreaseIndent(final Editor editor) {
    final Document document = editor.getDocument();
    for (PsiElement statement : myElementsToChangeIndent) {
      final int startOffset = statement.getTextRange().getStartOffset() - 1;
      PsiElement element = statement.getContainingFile().findElementAt(startOffset);
      assert element instanceof PsiWhiteSpace;
      final String text = element.getText();
      String[] lines = text.split("\n");
      if (lines.length == 0) {
        continue;
      }
      final int indent = lines[lines.length - 1].length();
      final int startLine = editor.offsetToLogicalPosition(startOffset).line;
      final int endLine = editor.offsetToLogicalPosition(statement.getTextRange().getEndOffset()).line;
      for (int line = startLine; line <= endLine; ++line) {
        final int indentLevel = myIndentLevel * -4;
        if (indent >= 4 && indentLevel <= indent) {
          final int lineStartOffset = document.getLineStartOffset(line);
          document.deleteString(lineStartOffset, lineStartOffset + indentLevel);
        }
      }
    }
  }

  private void increaseIndent(final Editor editor) {
    final Document document = editor.getDocument();
    String indent = makeIndent();
    for (PsiElement statement : myElementsToChangeIndent) {
      final int startLine = editor.offsetToLogicalPosition(statement.getTextRange().getStartOffset()).line;
      final int endLine = editor.offsetToLogicalPosition(statement.getTextRange().getEndOffset()).line;
      for (int line = startLine; line <= endLine; ++line) {
        final int offset = document.getLineStartOffset(line);
        document.insertString(offset, indent);
      }
    }
  }

  private String makeIndent() {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < myIndentLevel; ++i) {
      result.append("    ");
    }
    return result.toString();
  }

  @Override
  public void beforeMove(@NotNull Editor editor, @NotNull MoveInfo info, boolean down) {
    super.beforeMove(editor, info, down);
    if (myStatementListToAddPass != null) {
      final PyPassStatement passStatement =
        PyElementGenerator.getInstance(editor.getProject()).createFromText(LanguageLevel.getDefault(), PyPassStatement.class, "pass");
      myStatementListToAddPass.add(passStatement);
      CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myStatementListToAddPass);
      if (down) {
        info.toMove2 = new LineRange(info.toMove2.startLine, info.toMove2.endLine + 1);
      }
    }
    if (myIndentLevel < 0) {
      decreaseIndent(editor);
    }
    else {
      increaseIndent(editor);
    }
  }

  @Override
  public void afterMove(@NotNull Editor editor,
                        @NotNull PsiFile file,
                        @NotNull MoveInfo info,
                        boolean down) {
    super.afterMove(editor, file, info, down);
    if (myStatementListToRemovePass != null) {
      PyPsiUtils.removeRedundantPass(myStatementListToRemovePass);
      CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myStatementListToRemovePass);
    }
  }
}
