package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;

import java.util.Comparator;

public class DocumentUtil {
  public static final Comparator<RangeMarker> RANGE_ORDER = new Comparator<RangeMarker>() {
    public int compare(RangeMarker rangeMarker, RangeMarker rangeMarker1) {
      return rangeMarker.getStartOffset() - rangeMarker1.getStartOffset();
    }
  };

  public static String getText(RangeMarker range) {
    return range.getDocument().getText().substring(range.getStartOffset(), range.getEndOffset());
  }

  public static boolean isEmpty(RangeMarker rangeMarker) {
    return rangeMarker.getStartOffset() == rangeMarker.getEndOffset();
  }

  public static int getStartLine(RangeMarker range) {
    final Document doc = range.getDocument();
    if (doc.getTextLength() == 0) return 0;

    return doc.getLineNumber(range.getStartOffset());
  }

  public static int getEndLine(RangeMarker range) {
    Document document = range.getDocument();
    int endOffset = range.getEndOffset();
    if (document.getTextLength() == endOffset) return document.getLineCount();
    return document.getLineNumber(endOffset);
  }

  public static int getLength(RangeMarker rangeMarker) {
    return rangeMarker.getEndOffset() - rangeMarker.getStartOffset();
  }
}
