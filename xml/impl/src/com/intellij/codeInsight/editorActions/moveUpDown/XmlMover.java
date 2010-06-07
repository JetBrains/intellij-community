/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;

class XmlMover extends LineMover {
  //private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.XmlMover");

  public boolean checkAvailable(@NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final MoveInfo info, final boolean down) {
    if (!(file instanceof XmlFile)) {
      return false;
    }
    boolean available = super.checkAvailable(editor, file, info, down);
    if (!available) return false;

    // updated moved range end to cover multiline tag start
    final Document document = editor.getDocument();
    int movedLineStart = document.getLineStartOffset(info.toMove.startLine);
    final int movedLineEnd = document.getLineEndOffset(info.toMove.endLine - 1);

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

    XmlTag nearestTag = PsiTreeUtil.getParentOfType(movedStartElement, XmlTag.class);
    if (nearestTag != null &&
        ( "script".equals(nearestTag.getLocalName()) ||
          (nearestTag instanceof HtmlTag && "script".equalsIgnoreCase(nearestTag.getLocalName()))
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
        movedLineStart = updateMovedRegionEnd(document, movedLineStart, valueStart + 1, info, down);
      }
      if (movedLineStart < valueStart) {
        movedLineStart = updatedMovedRegionStart(document, movedLineStart, tag.getTextRange().getStartOffset(), info, down);
      }
    } else if (movedParent instanceof XmlAttribute) {
      final int endOffset = textRange.getEndOffset() + 1;
      if (endOffset < document.getTextLength()) movedLineStart = updateMovedRegionEnd(document, movedLineStart, endOffset, info, down);
      movedLineStart = updatedMovedRegionStart(document, movedLineStart, textRange.getStartOffset(), info, down);
    }
    else if (!movedParent.getLanguage().isKindOf(XMLLanguage.INSTANCE)) {
      return false;
    }

    final TextRange moveDestinationRange = new TextRange(
      document.getLineStartOffset(info.toMove2.startLine),
      document.getLineStartOffset(info.toMove2.endLine)
    );

    if (movedParent instanceof XmlAttribute) {
      final XmlTag parent = ((XmlAttribute)movedParent).getParent();

      if (parent != null) {
        final TextRange valueRange = parent.getValue().getTextRange();

        // Do not move attributes out of tags
        if ( (down && moveDestinationRange.getEndOffset() >= valueRange.getStartOffset()) ||
             (!down && moveDestinationRange.getStartOffset() <= parent.getTextRange().getStartOffset())
          ) {
          info.toMove2 = null;
        }
      }
    }

    if (down) {
      PsiElement updatedElement = file.findElementAt(moveDestinationRange.getEndOffset());
      if (updatedElement instanceof PsiWhiteSpace) updatedElement = PsiTreeUtil.prevLeaf(updatedElement);

      if (updatedElement != null) {
        final PsiNamedElement namedParent = PsiTreeUtil.getParentOfType(updatedElement, movedParent.getClass());

        if (namedParent instanceof XmlTag) {
          final XmlTag tag = (XmlTag)namedParent;
          final int offset = tag.isEmpty() ? tag.getTextRange().getStartOffset() : tag.getValue().getTextRange().getStartOffset();
          updatedMovedIntoEnd(document, info, offset);
        } else if (namedParent instanceof XmlAttribute) {
          updatedMovedIntoEnd(document, info, namedParent.getTextRange().getEndOffset());
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
          final XmlTag[] subtags = tag.getSubTags();
          if ((tagValueRange.contains(movedLineStart) && subtags.length > 0 && subtags[0] == movedParent) ||
              ( tagValueRange.getLength() == 0 && tag.getTextRange().intersects(moveDestinationRange))
             ) {
            final int line = document.getLineNumber(tag.getTextRange().getStartOffset());
            final LineRange toMove2 = info.toMove2;
            info.toMove2 = new LineRange(Math.min(line, toMove2.startLine), toMove2.endLine);
          }
        } else if (namedParent instanceof XmlAttribute) {
          final int line = document.getLineNumber(namedParent.getTextRange().getStartOffset());
          final LineRange toMove2 = info.toMove2;
          info.toMove2 = new LineRange(Math.min(line, toMove2.startLine), toMove2.endLine);
        }
      }
    }

    return true;
  }

  private void updatedMovedIntoEnd(final Document document, @NotNull final MoveInfo info, final int offset) {
    if (offset + 1 < document.getTextLength()) {
      final int line = document.getLineNumber(offset + 1);
      final LineRange toMove2 = info.toMove2;
      info.toMove2 = new LineRange(toMove2.startLine, Math.min(Math.max(line, toMove2.endLine), document.getLineCount() - 1));
    }
  }

  private int updatedMovedRegionStart(final Document document, int movedLineStart, final int offset, @NotNull final MoveInfo info, final boolean down) {
    final int line = document.getLineNumber(offset);
    final LineRange toMove = info.toMove;
    int delta = toMove.startLine - line;
    info.toMove = new LineRange(Math.min(line, toMove.startLine), toMove.endLine);

    // update moved range
    if (delta > 0 && !down) {
      final LineRange toMove2 = info.toMove2;
      info.toMove2 = new LineRange(toMove2.startLine - delta, toMove2.endLine - delta);
      movedLineStart = document.getLineStartOffset(toMove.startLine);
    }
    return movedLineStart;
  }

  private int updateMovedRegionEnd(final Document document, int movedLineStart, final int valueStart, @NotNull final MoveInfo info, final boolean down) {
    final int line = document.getLineNumber(valueStart);
    final LineRange toMove = info.toMove;
    int delta = line - toMove.endLine;
    info.toMove = new LineRange(toMove.startLine, Math.max(line, toMove.endLine));

    // update moved range
    if (delta > 0 && down) {
      final LineRange toMove2 = info.toMove2;
      info.toMove2 = new LineRange(toMove2.startLine + delta, Math.min(toMove2.endLine + delta, document.getLineCount() - 1));
      movedLineStart = document.getLineStartOffset(toMove.startLine);
    }
    return movedLineStart;
  }
}