package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ui.UIUtil;
import gnu.trove.Equality;

import java.awt.*;

public class BorderEffect {
  private final Graphics myGraphics;
  private final int myStartOffset;
  private final int myEndOffset;
  private final TextRange myRange;
  private final EditorImpl myEditor;
  private static final Equality<TextAttributes> SAME_COLOR_BOXES = new Equality<TextAttributes>() {
    public boolean equals(final TextAttributes attributes1, final TextAttributes attributes2) {
      Color effectColor = attributes1.getEffectColor();
      EffectType effectType = attributes1.getEffectType();
      return effectColor != null
             && effectColor.equals(attributes2.getEffectColor())
             && EffectType.BOXED == effectType &&
             effectType == attributes2.getEffectType();
    }
  };
  private static final Condition<TextAttributes> BOX_FILTER = new Condition<TextAttributes>() {
                              public boolean value(TextAttributes attributes) {
                                return attributes.getEffectColor() != null && attributes.getEffectType() == EffectType.BOXED;
                              }
                            };

  public BorderEffect(EditorImpl editor, Graphics graphics) {
    myEditor = editor;
    myGraphics = graphics;
    Rectangle clipBounds = myGraphics.getClipBounds();
    myStartOffset = yToLineStartOffset(editor, clipBounds.y);
    myEndOffset = yToLineStartOffset(editor, clipBounds.y + clipBounds.height + editor.getLineHeight());
    myRange = new TextRange(myStartOffset, myEndOffset);
  }

  private static int yToLineStartOffset(EditorImpl editor, int y) {
    Point point = new Point(0, y);
    LogicalPosition logicalStart = editor.xyToLogicalPosition(point);
    return editor.logicalPositionToOffset(logicalStart);
  }

  public void paintHighlighters(RangeHighlighter[] highlighterList) {
    for (RangeHighlighter aHighlighterList : highlighterList) {
      RangeHighlighterImpl rangeHighlighter = (RangeHighlighterImpl)aHighlighterList;
      if (rangeHighlighter.isValid()) {
        TextAttributes textAttributes = rangeHighlighter.getTextAttributes();
        if (isBorder(textAttributes) && intersectsRange(rangeHighlighter)) paintBorder(rangeHighlighter);
      }
    }
  }

  private static boolean isBorder(TextAttributes textAttributes) {
    return textAttributes != null &&
           textAttributes.getEffectColor() != null &&
           EffectType.BOXED == textAttributes.getEffectType();
  }

  private void paintBorder(RangeHighlighterImpl rangeHighlighter) {
    paintBorder(rangeHighlighter.getTextAttributes().getEffectColor(),
                rangeHighlighter.getAffectedAreaStartOffset(),
                rangeHighlighter.getAffectedAreaEndOffset());
  }

  private void paintBorder(Color color, int startOffset, int endOffset) {
    paintBorder(myGraphics, myEditor, startOffset, endOffset, color);
  }

  private boolean intersectsRange(RangeHighlighterImpl rangeHighlighter) {
    return myRange.contains(rangeHighlighter.getAffectedAreaStartOffset()) ||
           myRange.contains(rangeHighlighter.getAffectedAreaEndOffset());
  }

  public void paintHighlighters(EditorHighlighter highlighter) {
    int startOffset = startOfLineByOffset(myStartOffset);
    if (0 > startOffset || startOffset >= myEditor.getDocument().getTextLength()) return;
    RangeIterator iterator = new RangeIterator(new FoldingOrNewLineGaps(myEditor), SAME_COLOR_BOXES,
                                               highlighter.createIterator(startOffset),
                                               BOX_FILTER);
    iterator.init(myRange);
    for (; !iterator.atEnd();) {
      iterator.advance();
      paintBorder(myGraphics, myEditor, iterator.getStart(), iterator.getEnd(),
                  iterator.getTextAttributes().getEffectColor());
    }
  }

  private int startOfLineByOffset(int offset) {
    int line = myEditor.offsetToLogicalPosition(offset).line;
    if (line >= myEditor.getDocument().getLineCount()) return -1;
    return myEditor.getDocument().getLineStartOffset(line);
  }

  private static void paintBorder(Graphics g, EditorImpl editor, int startOffset, int endOffset, Color color) {
    Color savedColor = g.getColor();
    g.setColor(color);
    paintBorder(g, editor, startOffset, endOffset);
    g.setColor(savedColor);
  }

  private static void paintBorder(Graphics g, EditorImpl editor, int startOffset, int endOffset) {
    Point startPoint = offsetToXY(editor, startOffset);
    Point endPoint = offsetToXY(editor, endOffset);
    int height = endPoint.y - startPoint.y;
    int startX = startPoint.x;
    int startY = startPoint.y;
    int endX = endPoint.x;
    if (height == 0) {
      int width = endX == startX ? 1 : endX - startX - 1;
      g.drawRect(startX, startY, width, editor.getLineHeight() - 1);
      return;
    }
    BorderGraphics border = new BorderGraphics(g, startX, startY);
    border.horizontalTo(editor.getMaxWidthInRange(startOffset, endOffset) - 1);
    border.verticalRel(height - 1);
    border.horizontalTo(endX);
    border.verticalRel(editor.getLineHeight());
    border.horizontalTo(0);
    border.verticalRel(-height + 1);
    border.horizontalTo(startX);
    border.verticalTo(startY);
  }

  private static Point offsetToXY(EditorImpl editor, int offset) {
    return editor.logicalPositionToXY(editor.offsetToLogicalPosition(offset));
  }

  public static void paintFoldedEffect(Graphics g, int foldingXStart,
                                       int y, int foldingXEnd, int lineHeight, Color effectColor,
                                       EffectType effectType) {
    if (effectColor == null || effectType != EffectType.BOXED) return;
    g.setColor(effectColor);
    g.drawRect(foldingXStart, y, foldingXEnd - foldingXStart, lineHeight - 1);
  }

  private static class FoldingOrNewLineGaps implements RangeIterator.Gaps {
    private final RangeIterator.FoldingGaps myFoldingGaps;
    private final CharSequence myChars;

    public FoldingOrNewLineGaps(CharSequence chars, RangeIterator.FoldingGaps foldingGaps) {
      myChars = chars;
      myFoldingGaps = foldingGaps;
    }

    public FoldingOrNewLineGaps(EditorImpl editor) {
      this(editor.getDocument().getCharsSequence(), new RangeIterator.FoldingGaps(editor.getFoldingModel()));
    }

    public boolean isGapAt(int offset) {
      return myChars.charAt(offset) == '\n' || myFoldingGaps.isGapAt(offset);
    }
  }

  public static class BorderGraphics {
    private final Graphics myGraphics;

    private int myX;
    private int myY;

    public BorderGraphics(Graphics graphics, int startX, int stIntY) {
      myGraphics = graphics;

      myX = startX;
      myY = stIntY;
    }

    public void horizontalTo(int x) {
      lineTo(x, myY);
    }

    public void horizontalRel(int width) {
      lineTo(myX + width, myY);
    }

    private void lineTo(int x, int y) {
      UIUtil.drawLine(myGraphics, myX, myY, x, y);
      myX = x;
      myY = y;
    }

    public void verticalRel(int height) {
      lineTo(myX, myY + height);
    }

    public void verticalTo(int y) {
      lineTo(myX, y);
    }
  }
}
