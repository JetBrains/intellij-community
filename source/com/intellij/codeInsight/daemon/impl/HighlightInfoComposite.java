/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;

import java.util.ArrayList;
import java.util.List;

public class HighlightInfoComposite extends HighlightInfo {
  private static final String HTML_HEADER = "<html><body>";
  private static final String HTML_FOOTER = "</body></html>";

  public HighlightInfoComposite(List<HighlightInfo> infos) {
    super(getType(infos), infos.get(0).startOffset, infos.get(0).endOffset, createCompositeDescription(infos), createCompositeTooltip(infos));
    text = infos.get(0).text;
    highlighter = infos.get(0).highlighter;
    group = infos.get(0).group;
    quickFixActionMarkers = new ArrayList<Pair<IntentionAction, RangeMarker>>();
    quickFixActionRanges = new ArrayList<Pair<IntentionAction, TextRange>>();
    for (int i = 0; i < infos.size(); i++) {
      HighlightInfo info = infos.get(i);
      if (info.quickFixActionMarkers != null) {
        quickFixActionMarkers.addAll(info.quickFixActionMarkers);
      }
      if (info.quickFixActionRanges != null) {
        quickFixActionRanges.addAll(info.quickFixActionRanges);
      }
    }
  }

  private static HighlightInfoType getType(List<HighlightInfo> infos) {
    return infos.get(0).type;
  }

  private static String createCompositeDescription(List<HighlightInfo> infos) {
    StringBuffer description = new StringBuffer();
    boolean isNull = true;
    for (int i = 0; i < infos.size(); i++) {
      HighlightInfo info = infos.get(i);
      if (description.length() != 0) description.append(". ");
      description.append(info.description);
      isNull &= info.description == null;
    }
    return isNull ? null : description.toString();
  }

  private static String createCompositeTooltip(List<HighlightInfo> infos) {
    StringBuffer result = new StringBuffer();
    boolean isNull = true;
    for (int i = 0; i < infos.size(); i++) {
      HighlightInfo info = infos.get(i);
      if (result.length() != 0) result.append("\n<hr size=1 noshade>");
      String toolTip = info.toolTip;
      isNull &= info.toolTip == null;
      if (toolTip != null && toolTip.startsWith(HTML_HEADER)) {
        toolTip = toolTip.substring(HTML_HEADER.length());
      }
      if (toolTip != null && toolTip.endsWith(HTML_FOOTER)) {
        toolTip = toolTip.substring(0,toolTip.length()-HTML_FOOTER.length());
      }
      result.append(toolTip);
    }
    result.insert(0,HTML_HEADER);
    result.append(HTML_FOOTER);
    return isNull ? null : result.toString();
  }
}