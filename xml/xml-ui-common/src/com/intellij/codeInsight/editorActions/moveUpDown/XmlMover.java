// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.xml.TagNameVariantCollector;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.XmlNsDescriptorUtil;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlMover extends LineMover {
  @Override
  public boolean checkAvailable(final @NotNull Editor editor, final @NotNull PsiFile file, final @NotNull MoveInfo info, final boolean down) {
    if (!super.checkAvailable(editor, file, info, down)) return false;

    // updated moved range end to cover multiline tag start
    final Document document = editor.getDocument();
    int movedLineStart = document.getLineStartOffset(info.toMove.startLine);
    final int movedLineEnd = document.getLineEndOffset(info.toMove.endLine - 1);
    XmlElement xmlElementAtStart = getSourceElement(file, movedLineStart, true);
    XmlElement xmlElementAtEnd = getSourceElement(file, movedLineEnd, false);
    if (xmlElementAtStart == null || xmlElementAtEnd == null) return false;
    if (checkInjections(xmlElementAtStart, xmlElementAtEnd)) return false;

    XmlElement movedParent = null;

    if (xmlElementAtEnd == xmlElementAtStart) movedParent = xmlElementAtEnd;
    else if (xmlElementAtEnd instanceof XmlAttribute && xmlElementAtStart instanceof XmlTag && xmlElementAtEnd.getParent() == xmlElementAtStart) {
      movedParent = xmlElementAtStart;
    } else if (xmlElementAtStart instanceof XmlAttribute && xmlElementAtEnd instanceof XmlTag && xmlElementAtStart.getParent() == xmlElementAtEnd) {
      movedParent = xmlElementAtEnd;
    }

    if (movedParent == null) {
      return false;
    }

    final TextRange textRange = movedParent.getTextRange();

    if (movedParent instanceof XmlTag tag) {
      PsiElement parent = tag.getParent();
      if (!(parent instanceof XmlTag) && PsiTreeUtil.getChildrenOfType(parent, XmlTag.class).length < 2) {
        // the only top-level tag
        return info.prohibitMove();
      }
      final TextRange valueRange = getTagContentRange(tag);
      final int valueStart = valueRange.getStartOffset();

      if (HtmlUtil.isHtmlTag(tag) && (HtmlUtil.isScriptTag(tag) || HtmlUtil.STYLE_TAG_NAME.equals(tag.getName()))) {
        info.toMove = new LineRange(tag);
        int nextLine = down ? info.toMove.endLine : info.toMove.startLine - 1;
        info.toMove2 = new LineRange(nextLine, nextLine + 1);
      }

      if (movedLineStart < valueStart && valueStart + 1 < document.getTextLength()) {
        movedLineStart = updateMovedRegionEnd(document, movedLineStart, valueStart + 1, info, down);
      }
      if (movedLineStart < valueStart) {
        movedLineStart = updateMovedRegionStart(document, movedLineStart, tag.getTextRange().getStartOffset(), info, down);
      }
    } else if (movedParent instanceof XmlTagChild || movedParent instanceof XmlAttribute) {
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
        final TextRange valueRange = getTagContentRange(parent);

        // Do not move attributes out of tags
        if ( (down && moveDestinationRange.getEndOffset() >= valueRange.getStartOffset()) ||
             (!down && moveDestinationRange.getStartOffset() <= parent.getTextRange().getStartOffset())
          ) {
          return info.prohibitMove();
        }
      }
    }

    if (down) {
      final XmlElement targetParent = getDestinationElement(file, movedParent, moveDestinationRange.getEndOffset(), false);
      if (targetParent != null) {
        if (movedParent instanceof XmlTagChild && targetParent instanceof XmlTag tag) {
          if (targetParent == movedParent) return false;
          if (movedParent instanceof XmlTag && moveTags(info, (XmlTag)movedParent, tag, true)) return true;

          final int offset = tag.isEmpty() ? tag.getTextRange().getStartOffset() : getTagContentRange(tag).getStartOffset();
          updatedMovedIntoEnd(document, info, offset);
          if (tag.isEmpty()) {
            info.toMove2 = new LineRange(targetParent);
          }
        } else if ((movedParent instanceof XmlTagChild && targetParent instanceof XmlTagChild) || targetParent instanceof XmlAttribute) {
          updatedMovedIntoEnd(document, info, targetParent.getTextRange().getEndOffset());
        }
      }
    } else {
      final XmlElement targetParent = getDestinationElement(file, movedParent, moveDestinationRange.getStartOffset(), true);
      if (targetParent != null) {
        if (movedParent instanceof XmlTagChild && targetParent instanceof XmlTag tag) {
          final TextRange tagValueRange = getTagContentRange(tag);

          // We need to update destination range to jump over tag start
          final XmlTag[] subtags = tag.getSubTags();
          XmlTagChild[] children = tag.getValue().getChildren();
          if ((tagValueRange.contains(movedLineStart)
               && (subtags.length > 0 && subtags[0] == movedParent || children.length > 0 && children[0] == movedParent)) ||
              ( tagValueRange.getLength() == 0 && tag.getTextRange().intersects(moveDestinationRange))
             ) {
            final int line = document.getLineNumber(tag.getTextRange().getStartOffset());
            final LineRange toMove2 = info.toMove2;
            info.toMove2 = new LineRange(Math.min(line, toMove2.startLine), toMove2.endLine);
          }
          if (targetParent == movedParent) return false;
          if (movedParent instanceof XmlTag && moveTags(info, (XmlTag)movedParent, tag, false)) return true;

        } else if ((movedParent instanceof XmlTagChild && targetParent instanceof XmlTagChild) || targetParent instanceof XmlAttribute) {
          final int line = document.getLineNumber(targetParent.getTextRange().getStartOffset());
          final LineRange toMove2 = info.toMove2;
          info.toMove2 = new LineRange(Math.min(line, toMove2.startLine), toMove2.endLine);
        }
      }
    }

    if (movedParent instanceof XmlTagChild) {
      // it's quite simple after all...
      info.toMove = new LineRange(movedParent);
    }
    return true;
  }

  protected @Nullable XmlElement getSourceElement(@NotNull PsiFile file, int offset, boolean forward) {
    return getMeaningfulElementAtOffset(file, offset, forward, t -> t instanceof XmlTag || t instanceof XmlAttribute);
  }

  protected @Nullable XmlElement getDestinationElement(@NotNull PsiFile file, @NotNull XmlElement sourceElement, int offset, boolean forward) {
    return getMeaningfulElementAtOffset(file, offset, forward, t -> sourceElement instanceof XmlAttribute
                                                                    ? t instanceof XmlAttribute
                                                                    : t instanceof XmlTag);
  }

  protected static @Nullable XmlElement getMeaningfulElementAtOffset(@NotNull PsiFile file, int offset, boolean forward,
                                                                     @NotNull Condition<? super PsiElement> condition) {
    PsiElement element = file.findElementAt(offset);
    if (element instanceof PsiWhiteSpace) {
      element = forward ? PsiTreeUtil.nextLeaf(element) : PsiTreeUtil.prevLeaf(element);
    }
    return ObjectUtils.tryCast(PsiTreeUtil.findFirstParent(element, false, condition), XmlElement.class);
  }

  protected @NotNull TextRange getTagContentRange(@NotNull XmlTag parent) {
    return parent.getValue().getTextRange();
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
    targetRange = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(target.getNode()) == null ?
                  new LineRange(targetRange.startLine, targetRange.endLine - 1) : targetRange;
    if (targetRange.contains(info.toMove2)) {
      // we are going to jump into sibling tag
      XmlElementDescriptor descriptor = moved.getDescriptor();
      if (descriptor == null) return false;
      XmlNSDescriptor nsDescriptor = descriptor.getNSDescriptor();
      if (nsDescriptor == null) return false;
      XmlFile descriptorFile = nsDescriptor.getDescriptorFile();
      if (descriptorFile == null || XmlNsDescriptorUtil.isAutoGeneratedSchema(descriptorFile)) return false;
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

  private static void updatedMovedIntoEnd(final Document document, final @NotNull MoveInfo info, final int offset) {
    if (offset + 1 < document.getTextLength()) {
      final int line = document.getLineNumber(offset + 1);
      final LineRange toMove2 = info.toMove2;
      if (toMove2 == null) return;
      info.toMove2 = new LineRange(toMove2.startLine, MathUtil.clamp(line, toMove2.endLine, document.getLineCount() - 1));
    }
  }

  private static int updateMovedRegionStart(final Document document,
                                            int movedLineStart,
                                            final int offset,
                                            final @NotNull MoveInfo info,
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
                                          final @NotNull MoveInfo info,
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