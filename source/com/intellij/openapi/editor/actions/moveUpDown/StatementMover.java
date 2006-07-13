package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

class StatementMover extends LineMover {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.StatementMover");

  private PsiElement statementToSurroundWithCodeBlock;

  public StatementMover(final boolean isDown) {
    super(isDown);
  }

  protected void beforeMove(final Editor editor) {
    super.beforeMove(editor);
    if (statementToSurroundWithCodeBlock != null) {
      try {
        final Document document = PsiDocumentManager.getInstance(statementToSurroundWithCodeBlock.getProject()).getDocument(statementToSurroundWithCodeBlock.getContainingFile());
        int startOffset = document.getLineStartOffset(toMove.startLine);
        int endOffset = getLineStartSafeOffset(document, toMove.endLine);
        if (document.getText().charAt(endOffset-1) == '\n') endOffset--;
        final RangeMarker lineRangeMarker = document.createRangeMarker(startOffset, endOffset);

        final PsiElementFactory factory = statementToSurroundWithCodeBlock.getManager().getElementFactory();
        PsiCodeBlock codeBlock = factory.createCodeBlock();
        codeBlock.add(statementToSurroundWithCodeBlock);
        final PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText("{}", statementToSurroundWithCodeBlock);
        blockStatement.getCodeBlock().replace(codeBlock);
        PsiBlockStatement newStatement = (PsiBlockStatement)statementToSurroundWithCodeBlock.replace(blockStatement);
        newStatement = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(newStatement);
        toMove = new LineRange(document.getLineNumber(lineRangeMarker.getStartOffset()), document.getLineNumber(lineRangeMarker.getEndOffset())+1);
        PsiCodeBlock newCodeBlock = newStatement.getCodeBlock();
        if (isDown) {
          PsiElement blockChild = firstNonWhiteElement(newCodeBlock.getFirstBodyElement(), true);
          if (blockChild == null) blockChild = newCodeBlock.getRBrace();
          toMove2 = new LineRange(document.getLineNumber(newCodeBlock.getParent().getTextRange().getStartOffset()),
                                  document.getLineNumber(blockChild.getTextRange().getStartOffset()));
        }
        else {
          toMove2 = new LineRange(newCodeBlock.getRBrace(), newCodeBlock.getRBrace(), document);
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  protected boolean checkAvailable(Editor editor, PsiFile file) {
    //if (!(file instanceof PsiJavaFile)) return false;
    final boolean available = super.checkAvailable(editor, file);
    if (!available) return false;
    LineRange range = toMove;

    range = expandLineRangeToCoverPsiElements(range, editor, file);
    if (range == null) return false;
    final int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    final int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0));
    final PsiElement[] statements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    if (statements.length == 0) return false;
    range.firstElement = statements[0];
    range.lastElement = statements[statements.length-1];

    if (!checkMovingInsideOutside(file, editor, range)) {
      toMove2 = null;
      return true;
    }
    return true;
  }

  private boolean calcInsertOffset(PsiFile file, final Editor editor, LineRange range) {
    int line = isDown ? range.endLine+1 : range.startLine - 1;
    int startLine = isDown ? range.endLine : range.startLine - 1;
    while (true) {
      final int offset = editor.logicalPositionToOffset(new LogicalPosition(line, 0));
      PsiElement element = firstNonWhiteElement(offset, file, true);

      while (element != null && !(element instanceof PsiFile)) {
        if (!element.getTextRange().grown(-1).shiftRight(1).contains(offset)) {
          PsiElement elementToSurround = null;
          boolean found = false;
          if ((element instanceof PsiStatement || element instanceof PsiComment || element instanceof OuterLanguageElement)
              && statementCanBePlacedAlong(element)) {
            found = true;
            if (!(element.getParent() instanceof PsiCodeBlock)) {
              elementToSurround = element;
            }
          }
          else if (element instanceof PsiJavaToken
                   && ((PsiJavaToken)element).getTokenType() == JavaTokenType.RBRACE
                   && element.getParent() instanceof PsiCodeBlock) {
            // before code block closing brace
            found = true;
          }
          else if (element instanceof PsiMember) {
            PsiClass containingClass = ((PsiMember)element).getContainingClass();
            if (!(containingClass instanceof PsiAnonymousClass) || PsiTreeUtil.isAncestor(containingClass, range.firstElement, false)) {
              found = true;
            }
          }
          if (found) {
            statementToSurroundWithCodeBlock = elementToSurround;
            toMove = range;
            int endLine = line;
            if (startLine > endLine) {
              int tmp = endLine;
              endLine = startLine;
              startLine = tmp;
            }
            toMove2 = isDown ? new LineRange(startLine, endLine) : new LineRange(startLine, endLine+1);
            return true;
          }
        }
        element = element.getParent();
      }
      line += isDown ? 1 : -1;
      if (line == 0 || line >= editor.getDocument().getLineCount()) {
        return false;
      }
    }
  }

  private static boolean statementCanBePlacedAlong(final PsiElement element) {
    if (element instanceof PsiBlockStatement) return false;
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiCodeBlock) return true;
    if (parent instanceof PsiIfStatement &&
        (element == ((PsiIfStatement)parent).getThenBranch() || element == ((PsiIfStatement)parent).getElseBranch())) {
      return true;
    }
    if (parent instanceof PsiWhileStatement && element == ((PsiWhileStatement)parent).getBody()) {
      return true;
    }
    if (parent instanceof PsiDoWhileStatement && element == ((PsiDoWhileStatement)parent).getBody()) {
      return true;
    }
    // know nothing about that
    return false;
  }

  private boolean checkMovingInsideOutside(PsiFile file, final Editor editor, final LineRange result) {
    final int offset = editor.getCaretModel().getOffset();

    PsiElement guard = file.getViewProvider().findElementAt(offset, StdLanguages.JAVA);
    if (guard == null) return false;

    do {
      guard = PsiTreeUtil.getParentOfType(guard, PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiComment.class);
    }
    while (guard instanceof PsiAnonymousClass);

    // cannot move in/outside method/class/initializer/comment
    if (!calcInsertOffset(file, editor, result)) return false;
    int insertOffset = isDown ? getLineStartSafeOffset(editor.getDocument(), toMove2.endLine) : editor.getDocument().getLineStartOffset(toMove2.startLine);
    PsiElement newGuard = file.getViewProvider().findElementAt(insertOffset, StdLanguages.JAVA);
    do {
      newGuard = PsiTreeUtil.getParentOfType(newGuard, PsiMethod.class, PsiClassInitializer.class, PsiClass.class, PsiComment.class);
    }
    while (newGuard instanceof PsiAnonymousClass);

    if (newGuard == guard && isInside(insertOffset, newGuard) == isInside(offset, guard)) return true;

    // moving in/out nested class is OK
    if (guard instanceof PsiClass && guard.getParent() instanceof PsiClass) return true;
    if (newGuard instanceof PsiClass && newGuard.getParent() instanceof PsiClass) return true;
    return false;
  }

  private static boolean isInside(final int offset, final PsiElement guard) {
    if (guard == null) return false;
    TextRange inside = guard instanceof PsiMethod
                       ? ((PsiMethod)guard).getBody().getTextRange()
                       : guard instanceof PsiClassInitializer
                         ? ((PsiClassInitializer)guard).getBody().getTextRange()
                         : guard instanceof PsiClass ? new TextRange(((PsiClass)guard).getLBrace().getTextOffset(),
                                                                     ((PsiClass)guard).getRBrace().getTextOffset()) : guard.getTextRange();
    return inside != null && inside.contains(offset);
  }

  private static LineRange expandLineRangeToCoverPsiElements(final LineRange range, Editor editor, final PsiFile file) {
    Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, range);
    if (psiRange == null) return null;
    final PsiElement parent = PsiTreeUtil.findCommonParent(psiRange.getFirst(), psiRange.getSecond());
    Pair<PsiElement, PsiElement> elementRange = getElementRange(parent, psiRange.getFirst(), psiRange.getSecond());
    if (elementRange == null) return null;
    int endOffset = elementRange.getSecond().getTextRange().getEndOffset();
    int endLine;
    if (endOffset == editor.getDocument().getTextLength()) {
      endLine = editor.getDocument().getLineCount();
    }
    else {
      endLine = editor.offsetToLogicalPosition(endOffset).line+1;
      endLine = Math.min(endLine, editor.getDocument().getLineCount());
    }
    int startLine = Math.min(range.startLine, editor.offsetToLogicalPosition(elementRange.getFirst().getTextOffset()).line);
    endLine = Math.max(endLine, range.endLine);
    return new LineRange(startLine, endLine);
  }
}
