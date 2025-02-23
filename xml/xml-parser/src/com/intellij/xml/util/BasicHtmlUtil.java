// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class BasicHtmlUtil {
  public static final @NonNls String HTML5_DATA_ATTR_PREFIX = "data-";

  public static final @NlsSafe String SCRIPT_TAG_NAME = "script";
  public static final @NlsSafe String STYLE_TAG_NAME = "style";
  public static final @NlsSafe String TEMPLATE_TAG_NAME = "template";
  public static final @NlsSafe String TEXTAREA_TAG_NAME = "textarea";
  public static final @NlsSafe String TITLE_TAG_NAME = "title";
  public static final @NlsSafe String SLOT_TAG_NAME = "slot";

  public static final @NlsSafe String STYLE_ATTRIBUTE_NAME = STYLE_TAG_NAME;
  public static final @NlsSafe String SRC_ATTRIBUTE_NAME = "src";
  public static final @NlsSafe String ID_ATTRIBUTE_NAME = "id";
  public static final @NlsSafe String CLASS_ATTRIBUTE_NAME = "class";
  public static final @NlsSafe String TYPE_ATTRIBUTE_NAME = "type";
  public static final @NlsSafe String LANGUAGE_ATTRIBUTE_NAME = "language";
  public static final @NlsSafe String LANG_ATTRIBUTE_NAME = "lang";

  public static final @NonNls String MATH_ML_NAMESPACE = "http://www.w3.org/1998/Math/MathML";
  public static final @NonNls String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

  public static final String[] RFC2616_HEADERS = {"Accept", "Accept-Charset", "Accept-Encoding", "Accept-Language",
    "Accept-Ranges", "Age", "Allow", "Authorization", "Cache-Control", "Connection", "Content-Encoding", "Content-Language",
    "Content-Length", "Content-Location", "Content-MD5", "Content-Range", "Content-Type", "Date", "ETag", "Expect", "Expires", "From",
    "Host", "If-Match", "If-Modified-Since", "If-None-Match", "If-Range", "If-Unmodified-Since", "Last-Modified", "Location",
    "Max-Forwards", "Pragma", "Proxy-Authenticate", "Proxy-Authorization", "Range", "Referer", "Refresh", "Retry-After", "Server", "TE",
    "Trailer", "Transfer-Encoding", "Upgrade", "User-Agent", "Vary", "Via", "Warning", "WWW-Authenticate"};
  private static final String HTML_TAG_REGEXP = "\\s*</?\\w+\\s*(\\w+\\s*=.*)?>.*";
  private static final Pattern HTML_TAG_PATTERN = Pattern.compile(HTML_TAG_REGEXP);

  private BasicHtmlUtil() { }

  public static final Set<String> EMPTY_TAGS_MAP = Set.of(
    "area", "base", "basefont", "br", "col", "embed", "frame", "hr", "meta", "img", "input", "isindex", "link", "param", "source", "track",
    "wbr"
  );

  private static final Set<String> OPTIONAL_END_TAGS_MAP = Set.of(
    //"html",
    "head",
    //"body",
    "caption", "colgroup", "dd", "dt", "embed", "li", "noembed", "optgroup", "option", "p", "rt", "rp", "tbody", "td", "tfoot", "th",
    "thead", "tr"
  );

  private static final Set<String> BLOCK_TAGS_MAP =
    Set.of("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dir", "menu", "pre",
           "dl", "div", "center", "noscript", "noframes", "blockquote", "form", "isindex", "hr", "table", "fieldset", "address",
           // nonexplicitly specified
           "map",
           // flow elements
           "body", "object", "applet", "ins", "del", "dd", "li", "button", "th", "td", "iframe", "comment");

  // flow elements are block or inline, so they should not close <p> for example
  private static final Set<String> POSSIBLY_INLINE_TAGS_MAP =
    Set.of("a", "abbr", "acronym", "applet", "b", "basefont", "bdo", "big", "br", "button",
           "cite", "code", "del", "dfn", "em", "font", "i", "iframe", "img", "input", "ins",
           "kbd", "label", "map", "object", "q", "s", "samp", "select", "small", "span", "strike",
           "strong", "sub", "sup", "textarea", "tt", "u", "var");

  private static final Set<String> INLINE_ELEMENTS_CONTAINER_MAP = Set.of("p", "h1", "h2", "h3", "h4", "h5", "h6", "pre");

  private static final Set<String> HTML5_TAGS_SET = Set.of("article", "aside", "audio", "canvas", "command", "datalist",
                                                           "details", "embed", "figcaption", "figure", "footer", "header",
                                                           "keygen", "mark", "meter", "nav", "output", "progress", "rp", "rt",
                                                           "ruby", "section", "source", "summary", "time", "video", "wbr",
                                                           "main"
  );

  private static final Set<String> P_AUTO_CLOSE_CLOSING_TAGS =
    Set.of("abbr", "acronym", "address", "applet", "area", "article", "aside", "b", "base", "basefont", "bdi", "bdo", "big",
           "blockquote", "body", "br", "button", "canvas", "caption", "center", "cite", "code", "col", "colgroup", "data", "datalist", "dd",
           "details", "dfn", "dialog", "dir", "div", "dl", "dt", "em", "embed", "fieldset", "figcaption", "figure", "font", "footer",
           "form", "frame", "frameset", "head", "header", "hgroup", "h1", "hr", "html", "i", "iframe", "img", "input", "kbd",
           "keygen", "label", "legend", "li", "link", "main", "mark", "menu", "menuitem", "meta", "meter", "nav", "noframes",
           "object", "ol", "optgroup", "option", "output", "p", "param", "picture", "pre", "progress", "q", "rp", "rt", "ruby",
           "s", "samp", "script", "section", "select", "small", "source", "span", "strike", "strong", "style", "sub", "summary", "sup",
           "svg", "table", "tbody", "td", "template", "textarea", "tfoot", "th", "thead", "time", "title", "tr", "track", "tt", "u", "ul",
           "var", "wbr"
    );

  private static final Map<String, Set<String>> AUTO_CLOSE_BY_OPENING_TAG = new HashMap<>();

  static {
    AUTO_CLOSE_BY_OPENING_TAG.put("colgroup", Set.of("colgroup", "tbody", "tfoot", "thead"));
    AUTO_CLOSE_BY_OPENING_TAG.put("dd", Set.of("dd", "dt"));
    AUTO_CLOSE_BY_OPENING_TAG.put("dt", Set.of("dd", "dt"));
    AUTO_CLOSE_BY_OPENING_TAG.put("head", Set.of("body"));
    AUTO_CLOSE_BY_OPENING_TAG.put("li", Set.of("li"));
    AUTO_CLOSE_BY_OPENING_TAG.put("optgroup", Set.of("optgroup"));
    AUTO_CLOSE_BY_OPENING_TAG.put("option", Set.of("optgroup", "option"));
    AUTO_CLOSE_BY_OPENING_TAG.put("p", Set.of("address", "article", "aside", "blockquote", "center", "details", "div", "dl", "fieldset",
                                              "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6", "header",
                                              "hgroup", "hr", "main", "menu", "nav", "ol", "p", "pre", "search", "section", "table", "ul"));
    AUTO_CLOSE_BY_OPENING_TAG.put("rp", Set.of("rp", "rt"));
    AUTO_CLOSE_BY_OPENING_TAG.put("rt", Set.of("rp", "rt"));
    AUTO_CLOSE_BY_OPENING_TAG.put("tbody", Set.of("tbody", "tfoot"));
    AUTO_CLOSE_BY_OPENING_TAG.put("td", Set.of("td", "th"));
    AUTO_CLOSE_BY_OPENING_TAG.put("th", Set.of("td", "th"));
    AUTO_CLOSE_BY_OPENING_TAG.put("thead", Set.of("tbody", "tfoot"));
    AUTO_CLOSE_BY_OPENING_TAG.put("tr", Set.of("tr"));
  }

  public static boolean isSingleHtmlTag(String tagName, boolean caseSensitive) {
    return EMPTY_TAGS_MAP.contains(caseSensitive ? tagName : StringUtil.toLowerCase(tagName));
  }

  public static boolean isTagWithOptionalEnd(@NotNull String tagName, boolean caseSensitive) {
    return OPTIONAL_END_TAGS_MAP.contains(caseSensitive ? tagName : StringUtil.toLowerCase(tagName));
  }

  public static @NotNull ThreeState canOpeningTagAutoClose(@NotNull String tagToClose,
                                                           @NotNull String openingTag,
                                                           boolean caseSensitive) {
    var normalizedTagToClose = caseSensitive ? tagToClose : StringUtil.toLowerCase(tagToClose);
    var normalizedOpeningTag = caseSensitive ? openingTag : StringUtil.toLowerCase(openingTag);
    if (!isTagWithOptionalEnd(normalizedTagToClose, true)) {
      return ThreeState.NO;
    }
    final Set<String> closingTags = AUTO_CLOSE_BY_OPENING_TAG.get(normalizedTagToClose);
    if (closingTags != null && closingTags.contains(normalizedOpeningTag)) {
      return ThreeState.YES;
    }
    return ThreeState.UNSURE;
  }

  public static boolean canClosingTagAutoClose(@NotNull String tagToClose,
                                               @NotNull String closingTag,
                                               boolean caseSensitive) {
    var normalizedTagToClose = caseSensitive ? tagToClose : StringUtil.toLowerCase(tagToClose);
    var normalizedClosingTag = caseSensitive ? closingTag : StringUtil.toLowerCase(closingTag);
    if (!isTagWithOptionalEnd(normalizedTagToClose, true)) return false;
    if (normalizedTagToClose.equals("p")) {
      return P_AUTO_CLOSE_CLOSING_TAGS.contains(normalizedClosingTag);
    }
    return true;
  }

  public static boolean isHtmlBlockTag(String tagName, boolean caseSensitive) {
    return BLOCK_TAGS_MAP.contains(caseSensitive ? tagName : StringUtil.toLowerCase(tagName));
  }

  public static boolean isPossiblyInlineTag(@NotNull String tagName) {
    return POSSIBLY_INLINE_TAGS_MAP.contains(tagName);
  }

  public static boolean isInlineTagContainer(String tagName, boolean caseSensitive) {
    return INLINE_ELEMENTS_CONTAINER_MAP.contains(caseSensitive ? tagName : StringUtil.toLowerCase(tagName));
  }

  public static boolean isHtml5Tag(@NotNull String tagName) {
    return HTML5_TAGS_SET.contains(tagName);
  }

  public static boolean isCustomHtml5Attribute(String attributeName) {
    return attributeName.startsWith(HTML5_DATA_ATTR_PREFIX);
  }

  /**
   * Checks if the specified string starts with an HTML tag, and if it does, it returns the tag name.
   *
   * @param line the string to check if it starts with an HTML tag
   * @return if the input starts with an HTML tag, it returns the tag name, otherwise {@code null}
   */
  public static String getStartTag(@NotNull String line) {
    if (startsWithTag(line)) {
      int tagStart = line.indexOf("<");
      if (tagStart >= 0) {
        tagStart++;
        for (int i = tagStart; i < line.length(); i++) {
          char ch = line.charAt(i);
          if (!(Character.isAlphabetic(ch) || (i > tagStart && (Character.isDigit(ch) || ch == '-')))) {
            return line.substring(tagStart, i);
          }
        }
      }
    }
    return null;
  }

  public static boolean startsWithTag(@NotNull String line) {
    if (line.trim().startsWith("<")) {
      return HTML_TAG_PATTERN.matcher(line).matches();
    }
    return false;
  }

  public static boolean isTagWithoutAttributes(@NonNls String tagName) {
    return "br".equalsIgnoreCase(tagName);
  }

  public static boolean hasHtml(@NotNull PsiFile file) {
    return isHtmlFile(file) || file.getViewProvider() instanceof TemplateLanguageFileViewProvider;
  }

  public static boolean hasHtmlPrefix(@NotNull String url) {
    return url.startsWith("http://") ||
           url.startsWith("https://") ||
           url.startsWith("//") || //Protocol-relative URL
           url.startsWith("ftp://");
  }

  public static boolean isHtmlFile(@NotNull PsiElement element) {
    var language = element.getLanguage();
    return language.isKindOf(HTMLLanguage.INSTANCE) || language.isKindOf(XHTMLLanguage.INSTANCE);
  }

  public static @NotNull Iterable<String> splitClassNames(@Nullable String classAttributeValue) {
    // comma is useduse as separator because class name cannot contain comma but it can be part of JSF classes attributes
    return classAttributeValue != null ? StringUtil.tokenize(classAttributeValue, " \t,") : Collections.emptyList();
  }
}
