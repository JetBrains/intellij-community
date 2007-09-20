package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;

class XmlMover extends LineMover {
  //private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.XmlMover");

  public XmlMover(final boolean isDown) {
    super(isDown);
  }

  protected boolean checkAvailable(Editor editor, PsiFile file) {
    if (!(file instanceof XmlFile)) {
      return false;
    }
    boolean available = super.checkAvailable(editor, file);
    if (!available) return false;

    // updated moved range end to cover multiline tag start
    final Document document = editor.getDocument();
    int movedLineStart = document.getLineStartOffset(toMove.startLine);
    final int movedLineEnd = document.getLineEndOffset(toMove.endLine - 1);

    PsiElement movedEndElement = file.findElementAt(movedLineEnd);
    if (movedEndElement instanceof PsiWhiteSpace) movedEndElement = PsiTreeUtil.prevLeaf(movedEndElement);
    PsiElement movedStartElement = file.findElementAt(movedLineStart);
    if (movedStartElement instanceof PsiWhiteSpace) movedStartElement = PsiTreeUtil.nextLeaf(movedStartElement);

    if (movedEndElement == null || movedStartElement == null) return false;
    final PsiNamedElement namedParentAtEnd = PsiTreeUtil.getParentOfType(movedEndElement, PsiNamedElement.class);
    final PsiNamedElement namedParentAtStart = PsiTreeUtil.getParentOfType(movedStartElement, PsiNamedElement.class);

    final XmlText text = PsiTreeUtil.getParentOfType(movedStartElement, XmlText.class);
    final XmlText text2 = PsiTreeUtil.getParentOfType(movedEndElement, XmlText.class);

    // Let's do not care about injections for this mover
    if ( ( text != null &&
           ((PsiLanguageInjectionHost)text).getInjectedPsi() != null
         ) ||
         ( text2 != null &&
           ((PsiLanguageInjectionHost)text2).getInjectedPsi() != null
         )
       ) {
      return false;
    }

    PsiNamedElement movedParent = null;

    if (namedParentAtEnd == namedParentAtStart) movedParent = namedParentAtEnd;
    else if (namedParentAtEnd instanceof XmlAttribute && namedParentAtStart instanceof XmlTag && namedParentAtEnd.getParent() == namedParentAtStart) {
      movedParent = namedParentAtStart;
    } else if (namedParentAtStart instanceof XmlAttribute && namedParentAtEnd instanceof XmlTag && namedParentAtStart.getParent() == namedParentAtEnd) {
      movedParent = namedParentAtEnd;
    }

    if (movedParent == null) {
      return false;
    }
    
    final TextRange textRange = movedParent.getTextRange();

    if (movedParent instanceof XmlTag) {
      final XmlTag tag = (XmlTag)movedParent;
      final TextRange valueRange = tag.getValue().getTextRange();
      final int valueStart = valueRange.getStartOffset();

      if (movedLineStart < valueStart && valueStart + 1 < document.getTextLength()) {
        movedLineStart = updateMovedRegionEnd(document, movedLineStart, valueStart + 1);
      }
      if (movedLineStart < valueRange.getStartOffset()) {
        movedLineStart = updatedMovedRegionStart(document, movedLineStart, tag.getTextRange().getStartOffset());
      }
    } else if (movedParent instanceof XmlAttribute) {
      final int endOffset = textRange.getEndOffset() + 1;
      if (endOffset < document.getTextLength()) movedLineStart = updateMovedRegionEnd(document, movedLineStart, endOffset);
      movedLineStart = updatedMovedRegionStart(document, movedLineStart, textRange.getStartOffset());
    }

    final TextRange moveDestinationRange = new TextRange(
      document.getLineStartOffset(toMove2.startLine),
      document.getLineStartOffset(toMove2.endLine)
    );

    if (movedParent instanceof XmlAttribute) {
      final XmlTag parent = ((XmlAttribute)movedParent).getParent();

      if (parent != null) {
        final TextRange valueRange = parent.getValue().getTextRange();

        // Do not move attributes out of tags
        if ( (isDown && moveDestinationRange.getEndOffset() >= valueRange.getStartOffset()) ||
             (!isDown && moveDestinationRange.getStartOffset() <= parent.getTextRange().getStartOffset())
          ) {
          toMove2 = null;
        }
      }
    }

    if (isDown) {
      PsiElement updatedElement = file.findElementAt(moveDestinationRange.getEndOffset());
      if (updatedElement instanceof PsiWhiteSpace) updatedElement = PsiTreeUtil.prevLeaf(updatedElement);

      if (updatedElement != null) {
        final PsiNamedElement namedParent = PsiTreeUtil.getParentOfType(updatedElement, movedParent.getClass());

        if (namedParent instanceof XmlTag) {
          final XmlTag tag = (XmlTag)namedParent;
          updatedMovedIntoEnd(document, tag.getValue().getTextRange().getStartOffset());
        } else if (namedParent instanceof XmlAttribute) {
          updatedMovedIntoEnd(document, namedParent.getTextRange().getEndOffset());
        }
      }
    } else {
      PsiElement updatedElement = file.findElementAt(moveDestinationRange.getStartOffset());
      if (updatedElement instanceof PsiWhiteSpace) updatedElement = PsiTreeUtil.nextLeaf(updatedElement);

      if (updatedElement != null) {
        final PsiNamedElement namedParent = PsiTreeUtil.getParentOfType(updatedElement, movedParent.getClass());

        if (namedParent instanceof XmlTag) {
          final XmlTag tag = (XmlTag)namedParent;
          final TextRange tagValueRange = tag.getValue().getTextRange();

          // We need to update destination range to jump over tag start
          if (tagValueRange.contains(movedLineStart) ||
              ( tagValueRange.getLength() == 0 && tag.getTextRange().intersects(moveDestinationRange))
             ) {
            final int line = document.getLineNumber(tag.getTextRange().getStartOffset());
            toMove2 = new LineRange(Math.min(line, toMove2.startLine), toMove2.endLine);
          }
        } else if (namedParent instanceof XmlAttribute) {
          final int line = document.getLineNumber(namedParent.getTextRange().getStartOffset());
          toMove2 = new LineRange(Math.min(line, toMove2.startLine), toMove2.endLine);
        }
      }
    }

    return true;
  }

  private void updatedMovedIntoEnd(final Document document, final int offset) {
    if (offset + 1 < document.getTextLength()) {
      final int line = document.getLineNumber(offset + 1);
      toMove2 = new LineRange(toMove2.startLine, Math.min(Math.max(line, toMove2.endLine), document.getLineCount() - 1));
    }
  }

  private int updatedMovedRegionStart(final Document document, int movedLineStart, final int offset) {
    final int line = document.getLineNumber(offset);
    int delta = toMove.startLine - line;
    toMove = new LineRange(Math.min(line, toMove.startLine), toMove.endLine);

    // update moved range
    if (delta > 0 && !isDown) {
      toMove2 = new LineRange(toMove2.startLine - delta, toMove2.endLine - delta);
      movedLineStart = document.getLineStartOffset(toMove.startLine);
    }
    return movedLineStart;
  }

  private int updateMovedRegionEnd(final Document document, int movedLineStart, final int valueStart) {
    final int line = document.getLineNumber(valueStart);
    int delta = line - toMove.endLine;
    toMove = new LineRange(toMove.startLine, Math.max(line, toMove.endLine));

    // update moved range
    if (delta > 0 && isDown) {
      toMove2 = new LineRange(toMove2.startLine + delta, Math.min(toMove2.endLine + delta, document.getLineCount() - 1));
      movedLineStart = document.getLineStartOffset(toMove.startLine);
    }
    return movedLineStart;
  }
}