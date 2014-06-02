/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.xml.TagNameVariantCollector;
import com.intellij.psi.impl.source.xml.XmlDocumentImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;

class XmlMover extends LineMover {
  //private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.editor.actions.moveUpDown.XmlMover");

  @Override
  public boolean checkAvailable(@NotNull final Editor editor, @NotNull final PsiFile file, @NotNull final MoveInfo info, final boolean down) {
    if (!(file instanceof XmlFile)) {
      return false;
    }
    if (!super.checkAvailable(editor, file, info, down)) return false;

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

    if (checkInjections(movedEndElement, movedStartElement)) return false;

    XmlTag nearestTag = PsiTreeUtil.getParentOfType(movedStartElement, XmlTag.class);
    if (nearestTag != null && HtmlUtil.isScriptTag(nearestTag)) {
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
      PsiElement parent = tag.getParent();
      if (!(parent instanceof XmlTag) && PsiTreeUtil.getChildrenOfType(parent, XmlTag.class).length < 2) {
        // the only top-level tag
        return info.prohibitMove();
      }
      final TextRange valueRange = tag.getValue().getTextRange();
      final int valueStart = valueRange.getStartOffset();

      if (movedLineStart < valueStart && valueStart + 1 < document.getTextLength()) {
        movedLineStart = updateMovedRegionEnd(document, movedLineStart, valueStart + 1, info, down);
      }
      if (movedLineStart < valueStart) {
        movedLineStart = updateMovedRegionStart(document, movedLineStart, tag.getTextRange().getStartOffset(), info, down);
      }
    } else if (movedParent instanceof XmlAttribute) {
      final int endOffset = textRange.getEndOffset() + 1;
      if (endOffset < document.getTextLength()) movedLineStart = updateMovedRegionEnd(document, movedLineStart, endOffset, info, down);
      movedLineStart = updateMovedRegionStart(document, movedLineStart, textRange.getStartOffset(), info, down);
    }

    final TextRange moveDestinationRange = new UnfairTextRange(
      document.getLineStartOffset(info.toMove2.startLine),
      document.getLineEndOffset(info.toMove2.endLine - 1)
    );

    if (movedParent instanceof XmlAttribute) {
      final XmlTag parent = ((XmlAttribute)movedParent).getParent();

      if (parent != null) {
        final TextRange valueRange = parent.getValue().getTextRange();

        // Do not move attributes out of tags
        if ( (down && moveDestinationRange.getEndOffset() >= valueRange.getStartOffset()) ||
             (!down && moveDestinationRange.getStartOffset() <= parent.getTextRange().getStartOffset())
          ) {
          return info.prohibitMove();
        }
      }
    }

    if (down) {
      PsiElement updatedElement = file.findElementAt(moveDestinationRange.getEndOffset());
      if (updatedElement instanceof PsiWhiteSpace) updatedElement = PsiTreeUtil.prevLeaf(updatedElement);

      if (updatedElement != null) {
        final PsiNamedElement targetParent = PsiTreeUtil.getParentOfType(updatedElement, movedParent.getClass());

        if (targetParent instanceof XmlTag) {
          if (targetParent == movedParent) return false;
          if (moveTags(info, (XmlTag)movedParent, (XmlTag)targetParent, down)) return true;

          final XmlTag tag = (XmlTag)targetParent;
          final int offset = tag.isEmpty() ? tag.getTextRange().getStartOffset() : tag.getValue().getTextRange().getStartOffset();
          updatedMovedIntoEnd(document, info, offset);
          if (tag.isEmpty()) {
            info.toMove2 = new LineRange(targetParent);
          }
        } else if (targetParent instanceof XmlAttribute) {
          updatedMovedIntoEnd(document, info, targetParent.getTextRange().getEndOffset());
        }
      }
    } else {
      PsiElement updatedElement = file.findElementAt(moveDestinationRange.getStartOffset());
      if (updatedElement instanceof PsiWhiteSpace) updatedElement = PsiTreeUtil.nextLeaf(updatedElement);

      if (updatedElement != null) {
        final PsiNamedElement targetParent = PsiTreeUtil.getParentOfType(updatedElement, movedParent.getClass());

        if (targetParent instanceof XmlTag) {
          final XmlTag tag = (XmlTag)targetParent;
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
          if (targetParent == movedParent) return false;
          if (moveTags(info, (XmlTag)movedParent, (XmlTag)targetParent, down)) return true;

        } else if (targetParent instanceof XmlAttribute) {
          final int line = document.getLineNumber(targetParent.getTextRange().getStartOffset());
          final LineRange toMove2 = info.toMove2;
          info.toMove2 = new LineRange(Math.min(line, toMove2.startLine), toMove2.endLine);
        }
      }
    }

    if (movedParent instanceof XmlTag) {
      // it's quite simple after all...
      info.toMove = new LineRange(movedParent);
    }
    return true;
  }

  private static boolean moveTags(MoveInfo info, XmlTag moved, XmlTag target, boolean down) {
    if (target.getParent() == moved) {
      // we are going to jump into our own children
      // this can mean that target computed incorrectly
      XmlTag next = down ? PsiTreeUtil.getNextSiblingOfType(moved, XmlTag.class) :
                           PsiTreeUtil.getPrevSiblingOfType(moved, XmlTag.class);
      if (next == null) return info.prohibitMove();
      info.toMove = new LineRange(moved);
      info.toMove2 = new LineRange(next);
      return true;
    }
    else if (moved.getParent() == target) {
      return false;
    }

    LineRange targetRange = new LineRange(target);
    if (targetRange.contains(info.toMove2)) {
      // we are going to jump into sibling tag
      XmlElementDescriptor descriptor = moved.getDescriptor();
      if (descriptor == null) return false;
      XmlNSDescriptor nsDescriptor = descriptor.getNSDescriptor();
      if (nsDescriptor == null) return false;
      XmlFile descriptorFile = nsDescriptor.getDescriptorFile();
      if (descriptorFile == null || XmlDocumentImpl.isAutoGeneratedSchema(descriptorFile)) return false;
      if (!TagNameVariantCollector.couldContain(target, moved)) {
        info.toMove = new LineRange(moved);
        info.toMove2 = targetRange;
        return true;
      }
    }

    return false;
  }

  private static boolean checkInjections(PsiElement movedEndElement, PsiElement movedStartElement) {
    final XmlText text = PsiTreeUtil.getParentOfType(movedStartElement, XmlText.class);
    final XmlText text2 = PsiTreeUtil.getParentOfType(movedEndElement, XmlText.class);

    // Let's do not care about injections for this mover
    if ( ( text != null && InjectedLanguageManager.getInstance(text.getProject()).getInjectedPsiFiles(text) != null) ||
         ( text2 != null && InjectedLanguageManager.getInstance(text2.getProject()).getInjectedPsiFiles(text2) != null)) {
      return true;
    }
    return false;
  }

  private static void updatedMovedIntoEnd(final Document document, @NotNull final MoveInfo info, final int offset) {
    if (offset + 1 < document.getTextLength()) {
      final int line = document.getLineNumber(offset + 1);
      final LineRange toMove2 = info.toMove2;
      if (toMove2 == null) return;
      info.toMove2 = new LineRange(toMove2.startLine, Math.min(Math.max(line, toMove2.endLine), document.getLineCount() - 1));
    }
  }

  private static int updateMovedRegionStart(final Document document,
                                            int movedLineStart,
                                            final int offset,
                                            @NotNull final MoveInfo info,
                                            final boolean down) {
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

  private static int updateMovedRegionEnd(final Document document,
                                          int movedLineStart,
                                          final int valueStart,
                                          @NotNull final MoveInfo info,
                                          final boolean down) {
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