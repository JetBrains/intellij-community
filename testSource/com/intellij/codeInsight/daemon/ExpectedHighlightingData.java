/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.Assert;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ExpectedHighlightingData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.ExpectedHighlightingData");

  private static final String ERROR_MARKER = "error";
  private static final String WARNING_MARKER = "warning";
  private static final String INFO_MARKER = "info";
  private static final String END_LINE_HIGHLIGHT_MARKER = "EOLError";

  static class ExpectedHighlightingSet {
    public final String marker;
    private final boolean endOfLine;
    final boolean enabled;
    Set<HighlightInfo> infos;
    HighlightInfoType defaultErrorType;
    HighlightInfo.Severity severity;

    public ExpectedHighlightingSet(String marker, HighlightInfoType defaultErrorType,HighlightInfo.Severity severity, boolean endOfLine, boolean enabled) {
      this.marker = marker;
      this.endOfLine = endOfLine;
      this.enabled = enabled;
      infos = new com.intellij.util.containers.HashSet<HighlightInfo>();
      this.defaultErrorType = defaultErrorType;
      this.severity = severity;
    }
  }
  Map<String,ExpectedHighlightingSet> highlightingTypes;

  public ExpectedHighlightingData(Document document,boolean checkWarnings, boolean checkInfos) {
    highlightingTypes = new com.intellij.util.containers.HashMap<String,ExpectedHighlightingSet>();
    highlightingTypes.put(ERROR_MARKER, new ExpectedHighlightingSet(ERROR_MARKER, HighlightInfoType.ERROR, HighlightInfo.ERROR, false, true));
    highlightingTypes.put(WARNING_MARKER, new ExpectedHighlightingSet(WARNING_MARKER, HighlightInfoType.UNUSED_SYMBOL, HighlightInfo.WARNING, false, checkWarnings));
    highlightingTypes.put(INFO_MARKER, new ExpectedHighlightingSet(INFO_MARKER, HighlightInfoType.TODO, HighlightInfo.INFORMATION, false, checkInfos));
    highlightingTypes.put(END_LINE_HIGHLIGHT_MARKER, new ExpectedHighlightingSet(END_LINE_HIGHLIGHT_MARKER, HighlightInfoType.ERROR, HighlightInfo.ERROR, true, true));
    extractExpectedHighlightsSet(document);
  }

  /**
   * remove highlights (bounded with <marker>...</marker>) from test case file
   * @param document
   */
  private void extractExpectedHighlightsSet(Document document) {
    String text = document.getText();

    String typesRegex = "";
    for (Iterator<String> iterator = highlightingTypes.keySet().iterator(); iterator.hasNext();) {
      String marker = iterator.next();
      typesRegex += (typesRegex.length()==0 ? "" : "|") + "(?:"+marker+")";
    }

    // er...
    // any code then <marker> (with optional descr="...") then any code then </marker> then any code
    String pat = ".*?(<(" + typesRegex + ")(?: descr=\\\"((?:[^\\\"\\\\]|\\\\\\\")*)\\\")?(?: type=\\\"([0-9A-Z_]+)\\\")?(/)?>)(.*)";
                 //"(.+?)</" + marker + ">).*";
    Pattern p = Pattern.compile(pat, Pattern.DOTALL);
    for (; ;) {
      Matcher m = p.matcher(text);
      if (m.matches()) {
        int startOffset = m.start(1);
        String marker = m.group(2);
        final ExpectedHighlightingSet expectedHighlightingSet = highlightingTypes.get(marker);

        String descr = m.group(3);
        if (descr == null) {
          // no descr means any string by default
          descr = "*";
        }
        else if (descr.equals("null")) {
          // explicit "null" descr
          descr = null;
        }

        String typeString = m.group(4);
        String closeTagMarker = m.group(5);
        String rest = m.group(6);

        String content;
        int endOffset;
        if (closeTagMarker == null) {
          Pattern pat2 = Pattern.compile("(.*?)</" + marker + ">(.*)", Pattern.DOTALL);
          final Matcher matcher2 = pat2.matcher(rest);
          LOG.assertTrue(matcher2.matches());
          content = matcher2.group(1);
          endOffset = m.start(6) + matcher2.start(2);
        }
        else {
          // <XXX/>
          content = "";
          endOffset = m.start(6);
        }

        document.replaceString(startOffset, endOffset, content);

        final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(expectedHighlightingSet.defaultErrorType, startOffset, startOffset + content.length(), descr);

        HighlightInfoType type = null;

        if (typeString != null) {
          Field[] fields = HighlightInfoType.class.getFields();
          for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            try {
              if (field.getName().equals(typeString)) type = (HighlightInfoType)field.get(null);
            }
            catch (Exception e) {
            }
          }

          if (type == null) LOG.assertTrue(false,"Wrong highlight type: " + typeString);
        }

        highlightInfo.type = type;
        highlightInfo.isAfterEndOfLine = expectedHighlightingSet.endOfLine;
        LOG.assertTrue(expectedHighlightingSet.enabled);
        expectedHighlightingSet.infos.add(highlightInfo);
      } else {
        break;
      }
      text = document.getText();
    }
  }

  void checkResult(HighlightInfo[] infos, String text) {
    for (int i = 0; i < infos.length; i++) {
      HighlightInfo info = infos[i];
      if (!expectedInfosContainsInfo(this, info)) {
        final int startOffset = info.startOffset;
        final int endOffset = info.endOffset;
        String s = text.substring(startOffset, endOffset);
        String desc = info.description;

        int y1 = StringUtil.offsetToLineNumber(text, startOffset);
        int y2 = StringUtil.offsetToLineNumber(text, endOffset);
        int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
        int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

        Assert.assertTrue("Extra text fragment highlighted " +
            "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
            "(" + (x2 + 1) + ", " + (y2 + 1) + ")" +
            " :'" +
            s +
            "'" + (desc == null ? "" : " (" + desc + ")"),
            false);
      }
    }
    final Collection<ExpectedHighlightingSet> expectedHighlights = highlightingTypes.values();
    for (Iterator<ExpectedHighlightingSet> iterator = expectedHighlights.iterator();
         iterator.hasNext();) {
      ExpectedHighlightingSet highlightingSet = iterator.next();

      final Set<HighlightInfo> expInfos = highlightingSet.infos;
      for (Iterator<HighlightInfo> iterator1 = expInfos.iterator(); iterator1.hasNext();) {
        HighlightInfo expectedInfo = iterator1.next();
        if (!infosContainsExpectedInfo(infos, expectedInfo) && highlightingSet.enabled) {
          final int startOffset = expectedInfo.startOffset;
          final int endOffset = expectedInfo.endOffset;
          String s = text.substring(startOffset, endOffset);
          String desc = expectedInfo.description;

          int y1 = StringUtil.offsetToLineNumber(text, startOffset);
          int y2 = StringUtil.offsetToLineNumber(text, endOffset);
          int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
          int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

          Assert.assertTrue("Text fragment was not highlighted " +
                     "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                     "(" + (x2 + 1) + ", " + (y2 + 1) + ")" +
                     " :'" +
                     s +
                     "'" + (desc == null ? "" : " (" + desc + ")"),
                     false);
        }
      }
    }

  }

  private static boolean infosContainsExpectedInfo(HighlightInfo[] infos, HighlightInfo expectedInfo) {
    for (int i = 0; i < infos.length; i++) {
      HighlightInfo info = infos[i];
      if (infoEquals(expectedInfo, info)) {
        return true;
      }
    }
    return false;
  }

  private static boolean expectedInfosContainsInfo(ExpectedHighlightingData expectedHighlightsSet, HighlightInfo info) {
    final Collection<ExpectedHighlightingSet> expectedHighlights = expectedHighlightsSet.highlightingTypes.values();
    for (Iterator<ExpectedHighlightingSet> iterator = expectedHighlights.iterator(); iterator.hasNext();) {
      ExpectedHighlightingSet highlightingSet = iterator.next();
      if (highlightingSet.severity == info.getSeverity() && !highlightingSet.enabled) return true;
      final Set<HighlightInfo> infos = highlightingSet.infos;
      for (Iterator<HighlightInfo> iterator1 = infos.iterator(); iterator1.hasNext();) {
        HighlightInfo expectedInfo = iterator1.next();
        if (infoEquals(expectedInfo, info)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean infoEquals(HighlightInfo expectedInfo, HighlightInfo info) {
    if (expectedInfo == info) return true;
    return
      info.getSeverity() == expectedInfo.getSeverity() &&
      info.startOffset + (info.isAfterEndOfLine ? 1 : 0) == expectedInfo.startOffset &&
      info.endOffset == expectedInfo.endOffset &&
      info.isAfterEndOfLine == expectedInfo.isAfterEndOfLine &&
      (expectedInfo.type == null || info.type == expectedInfo.type) &&

      (Comparing.strEqual("*",expectedInfo.description) ? true :
      expectedInfo.description == null || info.description == null ? info.description == expectedInfo.description :
        Comparing.strEqual(info.description,expectedInfo.description));
  }
}