package com.intellij.openapi.editor.actions.moveUpDown;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
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

    if (isDown) {
      final PsiElement at = file.findElementAt(editor.getDocument().getLineStartOffset(toMove2.endLine));

      if (at != null && at.getParent() instanceof XmlTag) {
        final LineRange oldToMove2 = toMove2;
        final XmlTag tag = (XmlTag)at.getParent();
        final int line = editor.getDocument().getLineNumber(tag.getValue().getTextRange().getStartOffset() + 1);
        toMove2 = new LineRange(oldToMove2.startLine, Math.max(line, oldToMove2.endLine));
      }
    } else {
      final PsiElement at = file.findElementAt(editor.getDocument().getLineStartOffset(toMove2.startLine));

      if (at != null && at.getParent() instanceof XmlTag) {
        final XmlTag tag = (XmlTag)at.getParent();
        final LineRange oldToMove2 = toMove2;
        final TextRange textRange = tag.getValue().getTextRange();

        if (textRange.contains(editor.getDocument().getLineStartOffset(toMove.startLine))) {
          final int line = editor.getDocument().getLineNumber(tag.getTextRange().getStartOffset());
          toMove2 = new LineRange(Math.min(line, oldToMove2.startLine), oldToMove2.endLine);
        }
      }
    }

    return true;
  }
}