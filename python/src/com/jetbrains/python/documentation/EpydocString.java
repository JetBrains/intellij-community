package com.jetbrains.python.documentation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.util.XmlTagUtilBase;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class EpydocString extends StructuredDocString {
  public static String[] RAISES_TAGS = new String[] { "raises", "raise", "except", "exception" };

  public EpydocString(String docstringText) {
    super(docstringText, "@");
  }

  @Override
  public String getDescription() {
    final String html = inlineMarkupToHTML(myDescription);
    assert html != null;
    return html;
  }

  @Override
  @Nullable
  public String getReturnType() {
    String value = getTagValue("rtype");
    return removeInlineMarkup(value);
  }

  @Override
  public String getReturnDescription() {
    return inlineMarkupToHTML(getTagValue("return"));
  }

  @Override
  @Nullable
  public String getParamType(String paramName) {
    String value = getTagValue("type", paramName);
    return removeInlineMarkup(value);
  }

  @Override
  @Nullable
  public String getParamDescription(String paramName) {
    String value = getTagValue("param", paramName);
    if (value == null) {
      value = getTagValue("param", "*" + paramName);
    }
    if (value == null) {
      value = getTagValue("param", "**" + paramName);
    }
    return inlineMarkupToHTML(value);
  }

  @Override
  public List<String> getRaisedExceptions() {
    return getTagArguments(RAISES_TAGS);
  }

  @Override
  public String getRaisedExceptionDescription(String exceptionName) {
    return removeInlineMarkup(getTagValue(RAISES_TAGS, exceptionName));
  }

  @Nullable
  public static String removeInlineMarkup(String s) {
    return convertInlineMarkup(s, false);
  }

  @Nullable
  private static String convertInlineMarkup(String s, boolean toHTML) {
    if (s == null) return null;
    MarkupConverter converter = toHTML ? new HTMLConverter() : new MarkupConverter();
    converter.appendWithMarkup(s);
    return converter.result();
  }

  private static class MarkupConverter {
    protected final StringBuilder myResult = new StringBuilder();

    public void appendWithMarkup(String s) {
      int pos = 0;
      while(true) {
        int bracePos = s.indexOf('{', pos);
        if (bracePos < 1) break;
        char prevChar = s.charAt(bracePos-1);
        if (prevChar >= 'A' && prevChar <= 'Z') {
          appendText(s.substring(pos, bracePos - 1));
          int rbracePos = findMatchingEndBrace(s, bracePos);
          if (rbracePos < 0) {
            pos = bracePos + 1;
            break;
          }
          final String inlineMarkupContent = s.substring(bracePos + 1, rbracePos);
          appendMarkup(prevChar, inlineMarkupContent);
          pos = rbracePos + 1;
        }
        else {
          appendText(s.substring(pos, bracePos + 1));
          pos = bracePos+1;
        }
      }
      appendText(s.substring(pos));
    }

    protected void appendText(String text) {
      myResult.append(text);
    }

    protected void appendMarkup(char markupChar, String markupContent) {
      appendWithMarkup(markupContent);
    }

    public String result() {
      return myResult.toString();
    }
  }

  private static class HTMLConverter extends MarkupConverter {
    @Override
    protected void appendText(String text) {
      myResult.append(joinLines(XmlTagUtilBase.escapeString(text, false), true));
    }

    @Override
    protected void appendMarkup(char markupChar, String markupContent) {
      if (markupChar == 'U') {
        appendLink(markupContent);
        return;
      }
      switch (markupChar) {
        case 'I':
          appendTagPair(markupContent, "i");
          break;
        case 'B':
          appendTagPair(markupContent, "b");
          break;
        case 'C':
          appendTagPair(markupContent, "code");
          break;
        default:
          myResult.append(StringUtil.escapeXml(markupContent));
          break;
      }
    }

    private void appendTagPair(String markupContent, final String tagName) {
      myResult.append("<").append(tagName).append(">");
      appendWithMarkup(markupContent);
      myResult.append("</").append(tagName).append(">");
    }

    private void appendLink(String markupContent) {
      String linkText = StringUtil.escapeXml(markupContent);
      String linkUrl = linkText;
      int pos = markupContent.indexOf('<');
      if (pos >= 0 && markupContent.endsWith(">")) {
        linkText = StringUtil.escapeXml(markupContent.substring(0, pos).trim());
        linkUrl = joinLines(StringUtil.escapeXml(markupContent.substring(pos + 1, markupContent.length() - 1)), false);
      }
      myResult.append("<a href=\"");
      if (!linkUrl.matches("[a-z]+:.+")) {
        myResult.append("http://");
      }
      myResult.append(linkUrl).append("\">").append(linkText).append("</a>");
    }

  }

  private static int findMatchingEndBrace(String s, int bracePos) {
    int braceCount = 1;
    for(int pos=bracePos+1; pos < s.length(); pos++) {
      char c = s.charAt(pos);
      if (c == '{') braceCount++;
      else if (c == '}') {
        braceCount--;
        if (braceCount == 0) return pos;
      }
    }
    return -1;
  }

  private static String joinLines(String s, boolean addSpace) {
    while(true) {
      int lineBreakStart = s.indexOf('\n');
      if (lineBreakStart < 0) break;
      int lineBreakEnd = lineBreakStart+1;
      int blankLines = 0;
      while(lineBreakEnd < s.length() && (s.charAt(lineBreakEnd) == ' ' || s.charAt(lineBreakEnd) == '\n')) {
        if (s.charAt(lineBreakEnd) == '\n') blankLines++;
        lineBreakEnd++;
      }
      if (addSpace) {
        String separator = blankLines > 0 ? "<p>" : " ";
        s = s.substring(0, lineBreakStart) + separator + s.substring(lineBreakEnd);
      }
      else {
        s = s.substring(0, lineBreakStart) + s.substring(lineBreakEnd);
      }
    }
    return s;
  }

  @Nullable
  public static String inlineMarkupToHTML(String s) {
    return convertInlineMarkup(s, true);
  }
}
