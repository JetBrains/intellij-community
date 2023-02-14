// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation.docstrings;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class EpydocString extends TagBasedDocString {

  public static String[] RTYPE_TAGS = new String[] { "rtype", "returntype" };
  public static String[] KEYWORD_ARGUMENT_TAGS = new String[] { "keyword", "kwarg", "kwparam" };

  public static String[] ALL_TAGS = new String[] {
    "@param", "@type", "@return", "@rtype", "@keyword", "@raise", "@ivar", "@cvar", "@var", "@group", "@sort", "@note", "@attention",
    "@bug", "@warning", "@version", "@todo", "@deprecated", "@since", "@status", "@change", "@permission", "@requires",
    "@precondition", "@postcondition", "@invariant", "@author", "@organization", "@copyright", "@license", "@contact", "@summary", "@see"
  };


  public static String[] ADDITIONAL = new String[] {
    "group", "sort", "note", "attention",
    "bug", "warning", "version", "todo", "deprecated", "since", "status", "change", "permission", "requires",
    "precondition", "postcondition", "invariant", "author", "organization", "copyright", "license", "contact", "summary", "see"
  };

  public EpydocString(@NotNull Substring docstringText) {
    super(docstringText, "@");
  }

  @NotNull
  @Override
  public String getDescription() {
    final String html = inlineMarkupToHTML(myDescription);
    assert html != null;
    return html;
  }

  @NotNull
  @Override
  public List<String> getKeywordArguments() {
    return toUniqueStrings(getKeywordArgumentSubstrings());
  }

  @Override
  @Nullable
  public String getReturnType() {
    return removeInlineMarkup(getReturnTypeSubstring());
  }

  @Override
  public String getReturnDescription() {
    return inlineMarkupToHTML(getTagValue(RETURN_TAGS));
  }

  @Override
  @Nullable
  public String getParamType(@Nullable String paramName) {
    return removeInlineMarkup(getParamTypeSubstring(paramName));
  }

  @Override
  @Nullable
  public String getParamDescription(@Nullable String paramName) {
    if (paramName == null) {
      return null;
    }
    Substring value = getTagValue(PARAM_TAGS, paramName);
    if (value == null) {
      value = getTagValue(PARAM_TAGS, "*" + paramName);
    }
    if (value == null) {
      value = getTagValue(PARAM_TAGS, "**" + paramName);
    }
    return inlineMarkupToHTML(value);
  }

  @Nullable
  @Override
  public String getKeywordArgumentDescription(@Nullable String paramName) {
    if (paramName == null) {
      return null;
    }
    return inlineMarkupToHTML(getTagValue(KEYWORD_ARGUMENT_TAGS, paramName));
  }

  @NotNull
  @Override
  public List<String> getRaisedExceptions() {
    return toUniqueStrings(getTagArguments(RAISES_TAGS));
  }

  @Override
  public String getRaisedExceptionDescription(@Nullable String exceptionName) {
    if (exceptionName == null) {
      return null;
    }
    return removeInlineMarkup(getTagValue(RAISES_TAGS, exceptionName));
  }

  @Override
  public String getAttributeDescription() {
    final Substring value = getTagValue(VARIABLE_TAGS);
    return convertInlineMarkup(value != null ? value.toString() : null, true);
  }

  @Nullable
  public static String removeInlineMarkup(@Nullable String s) {
    return convertInlineMarkup(s, false);
  }

  @Nullable
  private static String removeInlineMarkup(@Nullable Substring s) {
    return convertInlineMarkup(s != null ? s.concatTrimmedLines(" ") : null, false);
  }

  @Nullable
  private static String convertInlineMarkup(@Nullable String s, boolean toHTML) {
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

    protected void appendMarkup(char markupChar, @NotNull String markupContent) {
      appendWithMarkup(markupContent);
    }

    public String result() {
      return myResult.toString();
    }
  }

  private static class HTMLConverter extends MarkupConverter {
    @Override
    protected void appendText(String text) {
      myResult.append(joinLines(XmlStringUtil.escapeString(text, false), true));
    }

    @Override
    protected void appendMarkup(char markupChar, @NotNull String markupContent) {
      if (markupChar == 'U') {
        appendLink(markupContent);
        return;
      }
      switch (markupChar) {
        case 'I' -> appendTagPair(markupContent, "i");
        case 'B' -> appendTagPair(markupContent, "b");
        case 'C' -> appendTagPair(markupContent, "code");
        default -> myResult.append(StringUtil.escapeXmlEntities(markupContent));
      }
    }

    private void appendTagPair(String markupContent, final String tagName) {
      myResult.append("<").append(tagName).append(">");
      appendWithMarkup(markupContent);
      myResult.append("</").append(tagName).append(">");
    }

    private void appendLink(@NotNull String markupContent) {
      String linkText = StringUtil.escapeXmlEntities(markupContent);
      String linkUrl = linkText;
      int pos = markupContent.indexOf('<');
      if (pos >= 0 && markupContent.endsWith(">")) {
        linkText = StringUtil.escapeXmlEntities(markupContent.substring(0, pos).trim());
        linkUrl = joinLines(StringUtil.escapeXmlEntities(markupContent.substring(pos + 1, markupContent.length() - 1)), false);
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
  public static String inlineMarkupToHTML(@Nullable String s) {
    return convertInlineMarkup(s, true);
  }

  @Nullable
  private static String inlineMarkupToHTML(@Nullable Substring s) {
    return s != null ? inlineMarkupToHTML(s.concatTrimmedLines(" ")) : null;
  }

  @Override
  public List<String> getAdditionalTags() {
    List<String> list = new ArrayList<>();
    for (String tagName : ADDITIONAL) {
      final Map<Substring, Substring> map = myArgTagValues.get(tagName);
      if (map != null) {
        list.add(tagName);
      }
    }
    return list;
  }

  @NotNull
  @Override
  public List<Substring> getKeywordArgumentSubstrings() {
    return getTagArguments(KEYWORD_ARGUMENT_TAGS);
  }

  @Override
  public Substring getReturnTypeSubstring() {
    return getTagValue(RTYPE_TAGS);
  }

  @Override
  public Substring getParamTypeSubstring(@Nullable String paramName) {
    return paramName == null ? getTagValue("type") : getTagValue("type", paramName);
  }

  @Nullable
  @Override
  public String getAttributeDescription(@Nullable String attrName) {
    return attrName != null ? inlineMarkupToHTML(getTagValue(VARIABLE_TAGS, attrName)) : null;
  }
}
