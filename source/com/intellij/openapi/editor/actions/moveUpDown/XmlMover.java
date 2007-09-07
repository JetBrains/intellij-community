package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

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

    final PsiElement movedEndElement = file.findElementAt(movedLineEnd);

    if (movedEndElement != null && movedEndElement.getParent() instanceof XmlTag) {
      final XmlTag tag = (XmlTag)movedEndElement.getParent();
      final TextRange valueRange = tag.getValue().getTextRange();
      final int valueStart = valueRange.getStartOffset();

      if (movedLineStart < valueStart && valueStart + 1 < document.getTextLength()) {
        final int line = document.getLineNumber(valueStart + 1);
        int delta = line - toMove.endLine;
        toMove = new LineRange(toMove.startLine, Math.max(line, toMove.endLine));

        // update moved range
        if (delta > 0 && isDown) {
          toMove2 = new LineRange(toMove2.startLine + delta, toMove2.endLine + delta);
          movedLineStart = document.getLineStartOffset(toMove.startLine);
        }
      }
    }

    final PsiElement movedStartElement = file.findElementAt(movedLineStart);

    // updated moved range start to cover multiline tag start
    if (movedStartElement != null && movedStartElement.getParent() instanceof XmlTag) {
      final XmlTag tag = (XmlTag)movedStartElement.getParent();
      final TextRange valueRange = tag.getValue().getTextRange();

      if (movedLineStart < valueRange.getStartOffset()) {
        final int line = document.getLineNumber(tag.getTextRange().getStartOffset());
        int delta = toMove.startLine - line;
        toMove = new LineRange(Math.min(line, toMove.startLine), toMove.endLine);

        // update moved range
        if (delta > 0 && !isDown) {
          toMove2 = new LineRange(toMove2.startLine - delta, toMove2.endLine - delta);
          movedLineStart = document.getLineStartOffset(toMove.startLine);
        }
      }
    }

    final TextRange moveDestinationRange = new TextRange(
      document.getLineStartOffset(toMove2.startLine),
      document.getLineStartOffset(toMove2.endLine)
    );
    
    if (isDown) {
      final PsiElement updatedElement = file.findElementAt(moveDestinationRange.getEndOffset());

      if (updatedElement != null && updatedElement.getParent() instanceof XmlTag) {
        final XmlTag tag = (XmlTag)updatedElement.getParent();
        final int line = document.getLineNumber(tag.getValue().getTextRange().getStartOffset() + 1);
        toMove2 = new LineRange(toMove2.startLine, Math.max(line, toMove2.endLine));
      }
    } else {
      final PsiElement updatedElement = file.findElementAt(moveDestinationRange.getStartOffset());

      if (updatedElement != null && updatedElement.getParent() instanceof XmlTag) {
        final XmlTag tag = (XmlTag)updatedElement.getParent();
        final TextRange tagValueRange = tag.getValue().getTextRange();

        // We need to update destination range to jump over tag start
        if (tagValueRange.contains(movedLineStart) ||
            ( tagValueRange.getLength() == 0 && tag.getTextRange().intersects(moveDestinationRange))
           ) {
          final int line = document.getLineNumber(tag.getTextRange().getStartOffset());
          toMove2 = new LineRange(Math.min(line, toMove2.startLine), toMove2.endLine);
        }
      }
    }

    return true;
  }
}