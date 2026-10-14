// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.lang.ASTNode;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UnfairTextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.xml.TagNameVariantCollector;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.XmlNsDescriptorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class XmlMover extends LineMover {
  @Override
  public boolean checkAvailable(final @NotNull Editor editor, final @NotNull PsiFile file, final @NotNull MoveInfo info, final boolean down) {
    if (!super.checkAvailable(editor, file, info, down)) return false;

    final Document document = editor.getDocument();
    final int movedLineStart = document.getLineStartOffset(info.toMove.startLine);
    final int movedLineEnd = document.getLineEndOffset(info.toMove.endLine - 1);
    XmlElement xmlElementAtStart = getSourceElement(file, movedLineStart, true);
    // the last line of the document has no line break, so use the last character of the line
    XmlElement xmlElementAtEnd = getSourceElement(file, Math.max(movedLineStart, movedLineEnd - 1), false);
    if (xmlElementAtStart == null || xmlElementAtEnd == null) return false;
    if (checkInjections(xmlElementAtStart, xmlElementAtEnd)) return false;

    if (xmlElementAtStart instanceof XmlAttribute attribute && xmlElementAtEnd == attribute) {
      return moveAttribute(file, document, info, attribute, down);
    }
    return moveTagChildren(file, document, info, down);
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
    final ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(parent.getNode());
    final ASTNode endTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parent.getNode());
    if (startTagEnd != null && endTagStart != null) {
      return new TextRange(startTagEnd.getTextRange().getEndOffset(), endTagStart.getTextRange().getStartOffset());
    }
    return parent.getValue().getTextRange();
  }

  /**
   * Returns `true` if the mover can move the element as a child of a tag.
   */
  protected boolean isMovableChild(@NotNull PsiElement element) {
    return element instanceof XmlTagChild;
  }

  /**
   * Returns `true` if the mover moves the element only as a whole.
   * The mover can split text by lines, but it does not split other movable children.
   */
  private boolean isAtom(@NotNull PsiElement element) {
    return !(element instanceof XmlText) && isMovableChild(element);
  }

  private boolean moveAttribute(@NotNull PsiFile file, @NotNull Document document, @NotNull MoveInfo info,
                                @NotNull XmlAttribute attribute, boolean down) {
    final TextRange textRange = attribute.getTextRange();
    final int endOffset = textRange.getEndOffset() + 1;
    if (endOffset < document.getTextLength()) updateMovedRegionEnd(document, endOffset, info, down);
    updateMovedRegionStart(document, textRange.getStartOffset(), info, down);

    final TextRange moveDestinationRange = new UnfairTextRange(
      document.getLineStartOffset(info.toMove2.startLine),
      document.getLineEndOffset(info.toMove2.endLine - 1)
    );

    final XmlTag parent = attribute.getParent();
    if (parent != null) {
      final TextRange valueRange = getTagContentRange(parent);

      // Do not move attributes out of tags
      if ((down && moveDestinationRange.getEndOffset() >= valueRange.getStartOffset()) ||
          (!down && moveDestinationRange.getStartOffset() <= parent.getTextRange().getStartOffset())) {
        return info.prohibitMove();
      }
    }

    final int destinationOffset = down ? moveDestinationRange.getEndOffset() : moveDestinationRange.getStartOffset();
    if (getDestinationElement(file, attribute, destinationOffset, !down) instanceof XmlAttribute target) {
      if (down) {
        updatedMovedIntoEnd(document, info, target.getTextRange().getEndOffset());
      }
      else {
        final int line = document.getLineNumber(target.getTextRange().getStartOffset());
        info.toMove2 = new LineRange(Math.min(line, info.toMove2.startLine), info.toMove2.endLine);
      }
    }
    return true;
  }

  /**
   * Moves whole tag children: tags, comments and lines of text.
   * The moved element enters an adjacent sibling tag if the sibling can contain it.
   * Otherwise, the moved element swaps with the sibling.
   * The first and the last child leave the parent tag.
   */
  private boolean moveTagChildren(@NotNull PsiFile file, @NotNull Document document, @NotNull MoveInfo info, boolean down) {
    final LineRange toMove = expandToWholeElements(file, document, info.toMove);
    if (toMove == null) return false;
    final TextRange movedRange = getTrimmedRange(document, toMove);
    if (movedRange == null) return false;

    final PsiElement firstLeaf = file.findElementAt(movedRange.getStartOffset());
    final PsiElement lastLeaf = file.findElementAt(movedRange.getEndOffset() - 1);
    if (firstLeaf == null || lastLeaf == null || checkInjections(firstLeaf, lastLeaf)) return false;
    final PsiElement first = getMovedChild(firstLeaf, movedRange);
    final PsiElement last = getMovedChild(lastLeaf, movedRange);
    if (first == null || last == null || first.getParent() != last.getParent()) return false;
    final PsiElement parent = first.getParent();

    final List<PsiElement> moved = new ArrayList<>();
    for (PsiElement child = first; child != null; child = child.getNextSibling()) {
      if (!isWhiteSpace(child)) {
        if (!isMovableChild(child)) return false;
        moved.add(child);
      }
      if (child == last) break;
    }

    if (!(parent instanceof XmlTag) && ContainerUtil.exists(moved, XmlTag.class::isInstance)
        && PsiTreeUtil.getChildrenOfTypeAsList(parent, XmlTag.class).size() < 2) {
      // the only top-level tag
      return info.prohibitMove();
    }

    final int line = down ? toMove.endLine : toMove.startLine - 1;
    if (line < 0 || down && line >= getMaxLine(document)) return info.prohibitMove();

    if (isBlankLine(document, line)) {
      info.toMove = toMove;
      info.toMove2 = new LineRange(line, line + 1);
      return true;
    }

    final int lineStart = document.getLineStartOffset(line);
    final int lineEnd = document.getLineEndOffset(line);
    final CharSequence text = document.getCharsSequence();
    final int targetOffset = down ? CharArrayUtil.shiftForward(text, lineStart, lineEnd, " \t")
                                  : CharArrayUtil.shiftBackward(text, lineStart, lineEnd - 1, " \t");
    final PsiElement target = file.findElementAt(targetOffset);
    if (target == null) return info.prohibitMove();

    final LineRange toMove2;
    PsiElement entered = null;
    if (parent instanceof XmlTag parentTag && PsiTreeUtil.isAncestor(parentTag, target, false)
        && !getTagContentRange(parentTag).contains(target.getTextRange().getStartOffset())) {
      // the target is in the start or in the end tag of the parent, so the moved elements leave the parent
      final TextRange parentRange = getTrimmedRange(document, parentTag.getTextRange());
      toMove2 = down ? new LineRange(line, getEndLine(document, parentRange))
                     : new LineRange(document.getLineNumber(parentRange.getStartOffset()), line + 1);
    }
    else {
      final PsiElement container = PsiTreeUtil.findCommonParent(parent, target);
      PsiElement sibling = target;
      while (sibling != null && sibling.getParent() != container) sibling = sibling.getParent();
      if (sibling == null) return info.prohibitMove();

      if (sibling instanceof XmlTag siblingTag && canMoveInto(document, siblingTag, moved)) {
        entered = siblingTag;
        final TextRange contentRange = getTagContentRange(siblingTag);
        toMove2 = down ? new LineRange(line, document.getLineNumber(contentRange.getStartOffset() - 1) + 1)
                       : new LineRange(document.getLineNumber(contentRange.getEndOffset()), line + 1);
      }
      else if (isAtom(sibling)) {
        final TextRange siblingRange = getTrimmedRange(document, sibling.getTextRange());
        toMove2 = down ? new LineRange(line, getEndLine(document, siblingRange))
                       : new LineRange(document.getLineNumber(siblingRange.getStartOffset()), line + 1);
      }
      else {
        toMove2 = new LineRange(line, line + 1);
      }
    }

    // The destination lines can cut only the parent tag, which the moved elements leave, or the entered tag
    final TextRange destinationRange = getTrimmedRange(document, toMove2);
    if (destinationRange == null) return info.prohibitMove();
    for (PsiElement element : getCutElements(file, document, destinationRange)) {
      if (element != parent && element != entered) return info.prohibitMove();
    }

    info.toMove = toMove;
    info.toMove2 = toMove2;
    return true;
  }

  /**
   * Extends the lines until they contain each cut element as a whole.
   */
  private @Nullable LineRange expandToWholeElements(@NotNull PsiFile file, @NotNull Document document, @NotNull LineRange lines) {
    LineRange result = lines;
    while (true) {
      final TextRange range = getTrimmedRange(document, result);
      if (range == null) return null;
      int startLine = result.startLine;
      int endLine = result.endLine;
      for (PsiElement element : getCutElements(file, document, range)) {
        final TextRange elementRange = getTrimmedRange(document, element.getTextRange());
        startLine = Math.min(startLine, document.getLineNumber(elementRange.getStartOffset()));
        endLine = Math.max(endLine, getEndLine(document, elementRange));
      }
      if (startLine == result.startLine && endLine == result.endLine) return result;
      result = new LineRange(startLine, endLine);
    }
  }

  /**
   * Returns the elements, which the range cuts into parts.
   * The range does not cut a tag, if the range is inside the tag content.
   */
  private @NotNull List<PsiElement> getCutElements(@NotNull PsiFile file, @NotNull Document document, @NotNull TextRange range) {
    final PsiElement first = file.findElementAt(range.getStartOffset());
    final PsiElement last = file.findElementAt(range.getEndOffset() - 1);
    if (first == null || last == null) return List.of();
    final PsiElement common = PsiTreeUtil.findCommonParent(first, last);
    if (common == null) return List.of();

    final List<PsiElement> result = new ArrayList<>();
    for (PsiElement element = first; element != common; element = element.getParent()) {
      if (isAtom(element) && element.getTextRange().getStartOffset() < range.getStartOffset()) result.add(element);
    }
    for (PsiElement element = last; element != common; element = element.getParent()) {
      if (isAtom(element) && getTrimmedRange(document, element.getTextRange()).getEndOffset() > range.getEndOffset()) {
        result.add(element);
      }
    }
    for (PsiElement element = common; element != null && !(element instanceof PsiFile); element = element.getParent()) {
      if (element instanceof XmlTag tag && getTagContentRange(tag).contains(range)) break;
      if (isAtom(element) && !range.contains(getTrimmedRange(document, element.getTextRange()))) result.add(element);
    }
    return result;
  }

  /**
   * Returns the ancestor of the leaf, which is a child of the moved elements parent.
   */
  private @Nullable PsiElement getMovedChild(@NotNull PsiElement leaf, @NotNull TextRange movedRange) {
    PsiElement element = leaf;
    while (true) {
      final PsiElement parent = element.getParent();
      if (parent == null || parent instanceof PsiFile) return null;
      if (parent instanceof XmlTag tag ? getTagContentRange(tag).contains(movedRange)
                                       : parent instanceof XmlDocument || !(parent instanceof XmlElement)) {
        return element;
      }
      element = parent;
    }
  }

  private boolean canMoveInto(@NotNull Document document, @NotNull XmlTag target, @NotNull List<PsiElement> moved) {
    if (target.isEmpty()) return false;
    final TextRange tagRange = target.getTextRange();
    final TextRange contentRange = getTagContentRange(target);
    // the target must have the start tag and the end tag on different lines
    if (contentRange.getStartOffset() <= tagRange.getStartOffset() || contentRange.getEndOffset() >= tagRange.getEndOffset()) return false;
    if (document.getLineNumber(contentRange.getStartOffset() - 1) >= document.getLineNumber(contentRange.getEndOffset())) return false;

    for (PsiElement element : moved) {
      if (element instanceof XmlTag tag && !couldContain(target, tag)) return false;
    }
    return true;
  }

  private static boolean couldContain(@NotNull XmlTag target, @NotNull XmlTag moved) {
    final XmlElementDescriptor descriptor = moved.getDescriptor();
    if (descriptor == null) return true;
    final XmlNSDescriptor nsDescriptor = descriptor.getNSDescriptor();
    if (nsDescriptor == null) return true;
    final XmlFile descriptorFile = nsDescriptor.getDescriptorFile();
    if (descriptorFile == null || XmlNsDescriptorUtil.isAutoGeneratedSchema(descriptorFile)) return true;
    return TagNameVariantCollector.couldContain(target, moved);
  }

  private static boolean isWhiteSpace(@NotNull PsiElement element) {
    return element instanceof PsiWhiteSpace || element instanceof XmlText && StringUtil.isEmptyOrSpaces(element.getText());
  }

  private static boolean isBlankLine(@NotNull Document document, int line) {
    final CharSequence text = document.getCharsSequence();
    final int lineEnd = document.getLineEndOffset(line);
    return CharArrayUtil.shiftForward(text, document.getLineStartOffset(line), lineEnd, " \t") == lineEnd;
  }

  /**
   * Returns the line, which is next to the last line of the document with a character.
   */
  private static int getMaxLine(@NotNull Document document) {
    final int lineCount = document.getLineCount();
    return lineCount > 0 && document.getLineStartOffset(lineCount - 1) == document.getTextLength() ? lineCount - 1 : lineCount;
  }

  private static int getEndLine(@NotNull Document document, @NotNull TextRange range) {
    return document.getLineNumber(Math.max(range.getStartOffset(), range.getEndOffset() - 1)) + 1;
  }

  private static @Nullable TextRange getTrimmedRange(@NotNull Document document, @NotNull LineRange lines) {
    final TextRange range = getTrimmedRange(document, new TextRange(document.getLineStartOffset(lines.startLine),
                                                                    document.getLineEndOffset(lines.endLine - 1)));
    return range.isEmpty() ? null : range;
  }

  private static @NotNull TextRange getTrimmedRange(@NotNull Document document, @NotNull TextRange range) {
    final CharSequence text = document.getCharsSequence();
    final int start = CharArrayUtil.shiftForward(text, range.getStartOffset(), range.getEndOffset(), " \t\n\r");
    final int end = CharArrayUtil.shiftBackward(text, start, range.getEndOffset() - 1, " \t\n\r") + 1;
    return new TextRange(start, Math.max(start, end));
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

  private static void updateMovedRegionStart(final Document document,
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
    }
  }

  private static void updateMovedRegionEnd(final Document document,
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
    }
  }
}
