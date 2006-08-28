package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspTemplateDeclaration;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class DeclarationMover extends LineMover {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.DeclarationMover");
  private PsiEnumConstant myEnumToInsertSemicolonAfter;

  public DeclarationMover(final boolean isDown) {
    super(isDown);
  }

  protected void beforeMove(final Editor editor) {
    super.beforeMove(editor);
    if (myEnumToInsertSemicolonAfter != null) {
      TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, new char[]{';'}, 0, 1, null, myEnumToInsertSemicolonAfter.getManager());

      try {
        PsiElement inserted = myEnumToInsertSemicolonAfter.getParent().addAfter(semicolon.getPsi(), myEnumToInsertSemicolonAfter);
        inserted = CodeInsightUtil.forcePsiPostprocessAndRestoreElement(inserted);
        final LogicalPosition position = editor.offsetToLogicalPosition(inserted.getTextRange().getEndOffset());

        toMove2 = new LineRange(position.line+1, position.line+1);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      finally {
        myEnumToInsertSemicolonAfter = null;
      }
    }
  }

  protected void afterMove(final Editor editor, final PsiFile file) {
    super.afterMove(editor, file);
    final int line1 = editor.offsetToLogicalPosition(range2.getStartOffset()).line;
    final int line2 = editor.offsetToLogicalPosition(range2.getEndOffset()).line;
    Document document = editor.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
    documentManager.commitDocument(document);
    PsiWhiteSpace whiteSpace1 = findWhitespaceNear(document.getLineStartOffset(line1), file, false);
    PsiWhiteSpace whiteSpace2 = findWhitespaceNear(document.getLineStartOffset(line2), file, false);
    PsiWhiteSpace whiteSpace = findWhitespaceNear(isDown ? range1.getStartOffset() : range1.getEndOffset(), file, false);
    fixupWhiteSpace(whiteSpace1);
    fixupWhiteSpace(whiteSpace2);

    fixupWhiteSpace(whiteSpace);
  }

  private static PsiWhiteSpace findWhitespaceNear(final int offset, final PsiFile file, boolean lookRight) {
    PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) {
      return (PsiWhiteSpace)element;
    }
    if (element == null) return null;
    element = lookRight ? element.getNextSibling() : element.getPrevSibling();
    return element instanceof PsiWhiteSpace ? (PsiWhiteSpace)element : null;
  }

  private static void fixupWhiteSpace(final PsiWhiteSpace whitespace) {
    if (whitespace == null) return;
    PsiElement element1 = whitespace.getPrevSibling();
    PsiElement element2 = whitespace.getNextSibling();
    if (element2 == null || element1 == null) return;
    String ws = CodeEditUtil.getStringWhiteSpaceBetweenTokens(whitespace.getNode(), element2.getNode(), element1.getContainingFile());
    LeafElement node = Factory.createSingleLeafElement(TokenType.WHITE_SPACE, ws.toCharArray(), 0, ws.length(), SharedImplUtil.findCharTableByTree(whitespace.getNode()), whitespace.getManager());
    whitespace.getParent().getNode().replaceChild(whitespace.getNode(), node);
  }

  protected boolean checkAvailable(Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) {
      return false;
    }
    boolean available = super.checkAvailable(editor, file);
    if (!available) return false;
    LineRange oldRange = toMove;
    final Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, oldRange);
    if (psiRange == null) return false;

    final PsiMember firstMember = PsiTreeUtil.getParentOfType(psiRange.getFirst(), PsiMember.class, false);
    final PsiMember lastMember = PsiTreeUtil.getParentOfType(psiRange.getSecond(), PsiMember.class, false);
    if (firstMember == null || lastMember == null) return false;

    LineRange range;
    if (firstMember == lastMember) {
      range = memberRange(firstMember, editor, oldRange);
      if (range == null) return false;
      range.firstElement = range.lastElement = firstMember;
    }
    else {
      final PsiElement parent = PsiTreeUtil.findCommonParent(firstMember, lastMember);
      if (parent == null) return false;

      final Pair<PsiElement, PsiElement> combinedRange = getElementRange(parent, firstMember, lastMember);
      if (combinedRange == null) return false;
      final LineRange lineRange1 = memberRange(combinedRange.getFirst(), editor, oldRange);
      if (lineRange1 == null) return false;
      final LineRange lineRange2 = memberRange(combinedRange.getSecond(), editor, oldRange);
      if (lineRange2 == null) return false;

      range = new LineRange(lineRange1.startLine, lineRange2.endLine);
      range.firstElement = combinedRange.getFirst();
      range.lastElement = combinedRange.getSecond();
    }
    Document document = editor.getDocument();

    PsiElement sibling = isDown ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling();
    if (sibling == null) return false;
    sibling = firstNonWhiteElement(sibling, isDown);
    final boolean areWeMovingClass = range.firstElement instanceof PsiClass;
    toMove = range;
    try {
      LineRange intraClassRange = moveInsideOutsideClassPosition(editor, sibling, isDown, areWeMovingClass);
      if (intraClassRange == null) {
        toMove2 = new LineRange(sibling, sibling, document);
        if (isDown && sibling.getNextSibling() == null) return false;
      }
      else {
        toMove2 = intraClassRange;
      }
    }
    catch (IllegalMoveException e) {
      toMove2 = null;
    }
    return true;
  }

  private static LineRange memberRange(@NotNull PsiElement member, Editor editor, LineRange lineRange) {
    final TextRange textRange = member.getTextRange();
    if (editor.getDocument().getTextLength() < textRange.getEndOffset()) return null;
    final int startLine = editor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    final int endLine = editor.offsetToLogicalPosition(textRange.getEndOffset()).line+1;
    if (!isInsideDeclaration(member, startLine, endLine, lineRange, editor)) return null;

    return new LineRange(startLine, endLine);
  }

  private static boolean isInsideDeclaration(@NotNull final PsiElement member,
                                             final int startLine,
                                             final int endLine,
                                             final LineRange lineRange,
                                             final Editor editor) {
    // if we positioned on member start or end we'll be able to move it
    if (startLine == lineRange.startLine || startLine == lineRange.endLine || endLine == lineRange.startLine ||
        endLine == lineRange.endLine) {
      return true;
    }
    List<PsiElement> memberSuspects = new ArrayList<PsiElement>();
    PsiModifierList modifierList = member instanceof PsiMember ? ((PsiMember)member).getModifierList() : null;
    if (modifierList != null) memberSuspects.add(modifierList);
    if (member instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)member;
      if (aClass instanceof PsiAnonymousClass) return false; // move new expression instead of anon class
      PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
      if (nameIdentifier != null) memberSuspects.add(nameIdentifier);
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      PsiIdentifier nameIdentifier = method.getNameIdentifier();
      if (nameIdentifier != null) memberSuspects.add(nameIdentifier);
      PsiTypeElement returnTypeElement = method.getReturnTypeElement();
      if (returnTypeElement != null) memberSuspects.add(returnTypeElement);
    }
    if (member instanceof PsiField) {
      final PsiField field = (PsiField)member;
      PsiIdentifier nameIdentifier = field.getNameIdentifier();
      memberSuspects.add(nameIdentifier);
      PsiTypeElement typeElement = field.getTypeElement();
      if (typeElement != null) memberSuspects.add(typeElement);
    }
    TextRange lineTextRange = new TextRange(editor.getDocument().getLineStartOffset(lineRange.startLine), editor.getDocument().getLineEndOffset(lineRange.endLine));
    for (PsiElement suspect : memberSuspects) {
      TextRange textRange = suspect.getTextRange();
      if (textRange != null && lineTextRange.intersects(textRange)) return true;
    }
    return false;
  }

  private static class IllegalMoveException extends Exception {
  }

  // null means we are not crossing class border
  // throws IllegalMoveException when corresponding movement has no sense
  @Nullable
  private LineRange moveInsideOutsideClassPosition(Editor editor, PsiElement sibling, final boolean isDown, boolean areWeMovingClass) throws IllegalMoveException{
    if (sibling == null) throw new IllegalMoveException();
    if (sibling instanceof PsiJavaToken &&
        ((PsiJavaToken)sibling).getTokenType() == (isDown ? JavaTokenType.RBRACE : JavaTokenType.LBRACE) &&
        sibling.getParent() instanceof PsiClass) {
      // moving outside class
      final PsiClass aClass = (PsiClass)sibling.getParent();
      final PsiElement parent = aClass.getParent();
      if (!areWeMovingClass && !(parent instanceof PsiClass)) throw new IllegalMoveException();
      if (aClass instanceof PsiAnonymousClass) throw new IllegalMoveException();
      return new LineRange(sibling, sibling, editor.getDocument());
      //return isDown ? nextLineOffset(editor, aClass.getTextRange().getEndOffset()) : aClass.getTextRange().getStartOffset();
    }
    // trying to move up inside enum constant list, move outside of enum class instead
    if (!isDown
        && sibling.getParent() instanceof PsiClass
        && (sibling instanceof PsiJavaToken && ((PsiJavaToken)sibling).getTokenType() == JavaTokenType.SEMICOLON || sibling instanceof PsiErrorElement)
        && firstNonWhiteElement(sibling.getPrevSibling(), false) instanceof PsiEnumConstant) {
      PsiClass aClass = (PsiClass)sibling.getParent();
      Document document = editor.getDocument();
      int startLine = document.getLineNumber(aClass.getTextRange().getStartOffset());
      int endLine = document.getLineNumber(sibling.getTextRange().getEndOffset()) + 1;
      return new LineRange(startLine, endLine);
    }
    if (sibling instanceof PsiClass) {
      // moving inside class
      PsiClass aClass = (PsiClass)sibling;
      if (aClass instanceof PsiAnonymousClass) throw new IllegalMoveException();
      return isDown
             ? new LineRange(aClass.getFirstChild(), aClass.isEnum() ? afterEnumConstantsPosition(aClass) : aClass.getLBrace(), editor.getDocument())
             : new LineRange(aClass.getRBrace(), aClass.getRBrace(), editor.getDocument());
    }
    if (sibling instanceof JspTemplateDeclaration) {
      // there should be another scriptlet/decl to move
      if (firstNonWhiteElement(this.isDown ? sibling.getNextSibling() : sibling.getPrevSibling(), this.isDown) == null) throw new IllegalMoveException();
    }
    return null;
  }

  private PsiElement afterEnumConstantsPosition(final PsiClass aClass) {
    PsiField[] fields = aClass.getFields();
    for (int i = fields.length-1;i>=0; i--) {
      PsiField field = fields[i];
      if (field instanceof PsiEnumConstant) {
        PsiElement anchor = firstNonWhiteElement(field.getNextSibling(), true);
        if (!(anchor instanceof PsiJavaToken && ((PsiJavaToken)anchor).getTokenType() == JavaTokenType.SEMICOLON)) {
          anchor = field;
          myEnumToInsertSemicolonAfter = (PsiEnumConstant)field;
        }
        return anchor;
      }
    }
    // no enum constants at all ?
    return aClass.getLBrace();
  }
}
