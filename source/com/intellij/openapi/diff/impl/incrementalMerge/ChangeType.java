package com.intellij.openapi.diff.impl.incrementalMerge;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.highlighting.LineRenderer;
import com.intellij.openapi.diff.impl.util.DocumentUtil;
import com.intellij.openapi.diff.impl.util.TextDiffType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.util.TextRange;

import java.awt.*;

public class ChangeType {
  private static final int LAYER = HighlighterLayer.SELECTION - 1;
  private static final ChangeType INSERT = new ChangeType(TextDiffType.INSERT);
  private static final ChangeType DELETED = new ChangeType(TextDiffType.DELETED);
  private static final ChangeType CHANGE = new ChangeType(TextDiffType.CHANGED);
  static final ChangeType CONFLICT = new ChangeType(TextDiffType.CONFLICT);

  private final TextDiffType myDiffType;

  private ChangeType(TextDiffType diffType) {
    myDiffType = diffType;
  }

  public RangeHighlighter addMarker(ChangeSide changeSide, MarkupHolder markup) {
    String text = changeSide.getText();
    if (text != null && text.length() > 0) return addBlock(text, changeSide, markup, myDiffType);
    else return addLine(markup, changeSide.getStartLine(), myDiffType, SeparatorPlacement.TOP);
  }

  public TextDiffType getTypeKey() {
    return myDiffType;
  }

  public TextDiffType getTextDiffType() { return getTypeKey(); }

  private static RangeHighlighter addBlock(String text, ChangeSide changeSide, MarkupHolder markup, TextDiffType diffType) {
    int length = text.length();
    int start = changeSide.getStart();
    int end = start + length;
    RangeHighlighter highlighter = markup.addRangeHighlighter(
      start, end, ChangeType.LAYER, diffType, HighlighterTargetArea.EXACT_RANGE);
    highlighter.setLineSeparatorPlacement(SeparatorPlacement.TOP);
    highlighter.setLineMarkerRenderer(LineRenderer.top());
    highlighter.setLineSeparatorColor(Color.GRAY);
    if (text.charAt(length - 1) == '\n') end--;
    highlighter = markup.addRangeHighlighter(start, end, LAYER, TextDiffType.NONE, HighlighterTargetArea.EXACT_RANGE);
    highlighter.setLineSeparatorPlacement(SeparatorPlacement.BOTTOM);
    highlighter.setLineSeparatorColor(Color.GRAY);
    highlighter.setLineMarkerRenderer(LineRenderer.bottom());
    return highlighter;
  }

  private static RangeHighlighter addLine(MarkupHolder markup, int line, TextDiffType type, SeparatorPlacement placement) {
    RangeHighlighter highlighter = markup.addLineHighlighter(line, LAYER, type);
    if (highlighter == null) return null;
    highlighter.setLineSeparatorPlacement(placement);
    return highlighter;
  }

  public static ChangeType fromDiffFragment(DiffFragment fragment) {
    if (fragment.getText1() == null) return INSERT;
    if (fragment.getText2() == null) return DELETED;
    return CHANGE;
  }

  public static ChangeType fromRanges(TextRange left, TextRange right) {
    if (left.getLength() == 0) return INSERT;
    if (right.getLength() == 0) return DELETED;
    return CHANGE;
  }

  public static void apply(RangeMarker original, RangeMarker target) {
    Document document = target.getDocument();
    if (DocumentUtil.isEmpty(original)) {
      int offset = target.getStartOffset();
      document.deleteString(offset, target.getEndOffset());
    }
    String text = DocumentUtil.getText(original);
    int startOffset = target.getStartOffset();
    if (DocumentUtil.isEmpty(target)) {
      document.insertString(startOffset, text);
    } else {
      document.replaceString(startOffset, target.getEndOffset(), text);
    }
  }

  public String toString() {
    return myDiffType.getDisplayName();
  }

  public interface MarkupHolder {
    RangeHighlighter addLineHighlighter(int line, int layer, TextDiffType diffType);
    RangeHighlighter addRangeHighlighter(int start, int end, int layer, TextDiffType type, HighlighterTargetArea targetArea);
  }

  public static abstract class ChangeSide {
    public int getStart() {
      return getRange().getStartOffset();
    }

    public int getStartLine() {
      return DocumentUtil.getStartLine(getRange());
    }

    public String getText() {
      return DocumentUtil.getText(getRange());
    }

    public int getEndLine() {
      return DocumentUtil.getEndLine(getRange());
    }

    public abstract DiffRangeMarker getRange();

    public abstract Change.HightlighterHolder getHighlighterHolder();

    public boolean contains(int offset) {
      return getStart() <= offset && offset < getEnd();
    }

    public int getEnd() {
      return getRange().getEndOffset();
    }
  }
}
