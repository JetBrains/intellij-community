// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.html.HtmlCompatibleFile;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.XmlTypedHandlersAdditionalSupport;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.impl.source.parsing.xml.HtmlBuilderDriver;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import com.intellij.xml.*;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import org.jetbrains.annotations.*;

import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Maxim.Mossienko
 */
public final class HtmlUtil {
  private static final Logger LOG = Logger.getInstance(HtmlUtil.class);

  private static final @NonNls String JSFC = "jsfc";
  private static final @NonNls String CHARSET = "charset";
  private static final @NonNls String CHARSET_PREFIX = CHARSET + "=";
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

  private HtmlUtil() {
  }

  private static final Set<String> EMPTY_TAGS_MAP = Set.of(
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

  public static boolean isSingleHtmlTag(@NotNull XmlTag tag, boolean toLowerCase) {
    final String name = tag.getName();
    boolean result = EMPTY_TAGS_MAP.contains(!toLowerCase || tag.isCaseSensitive()
                                             ? name : StringUtil.toLowerCase(name));
    return result && !XmlCustomElementDescriptor.isCustomElement(tag);
  }

  public static boolean isSingleHtmlTag(String tagName, boolean caseSensitive) {
    return EMPTY_TAGS_MAP.contains(caseSensitive ? tagName : StringUtil.toLowerCase(tagName));
  }

  /**
   * @deprecated Unclear distinction whether tag should be case-sensitive. Use {@link #isSingleHtmlTag(String, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public static boolean isSingleHtmlTag(String tagName) {
    return isSingleHtmlTag(tagName, false);
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

  /**
   * @deprecated Unclear distinction whether tag should be case-sensitive. Use {@link #isHtmlBlockTag(String, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public static boolean isHtmlBlockTag(String tagName) {
    return isHtmlBlockTag(tagName, false);
  }

  public static boolean isPossiblyInlineTag(@NotNull String tagName) {
    return POSSIBLY_INLINE_TAGS_MAP.contains(tagName);
  }

  public static boolean isInlineTagContainer(String tagName, boolean caseSensitive) {
    return INLINE_ELEMENTS_CONTAINER_MAP.contains(caseSensitive ? tagName : StringUtil.toLowerCase(tagName));
  }

  public static void addHtmlSpecificCompletions(final XmlElementDescriptor descriptor,
                                                final XmlTag element,
                                                final List<? super XmlElementDescriptor> variants) {
    // add html block completions for tags with optional ends!
    String name = descriptor.getName(element);

    if (name != null && isTagWithOptionalEnd(name, false)) {
      PsiElement parent = element.getParent();

      if (parent instanceof XmlTag && XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parent.getNode()) != null) {
        return;
      }

      if (parent != null) {
        // we need grand parent since completion already uses parent's descriptor
        parent = parent.getParent();
      }

      if (parent instanceof HtmlTag) {
        final XmlElementDescriptor parentDescriptor = ((HtmlTag)parent).getDescriptor();

        if (parentDescriptor != descriptor && parentDescriptor != null) {
          for (final XmlElementDescriptor elementsDescriptor : parentDescriptor.getElementsDescriptors((XmlTag)parent)) {
            if (isHtmlBlockTag(elementsDescriptor.getName(), false)) {
              variants.add(elementsDescriptor);
            }
          }
        }
      }
      else if (parent instanceof HtmlDocumentImpl) {
        final XmlNSDescriptor nsDescriptor = descriptor.getNSDescriptor();
        if (nsDescriptor != null) {
          for (XmlElementDescriptor elementDescriptor : nsDescriptor.getRootElementsDescriptors((XmlDocument)parent)) {
            if (isHtmlBlockTag(elementDescriptor.getName(), false) && !variants.contains(elementDescriptor)) {
              variants.add(elementDescriptor);
            }
          }
        }
      }
    }
  }

  public static @Nullable XmlDocument getRealXmlDocument(@Nullable XmlDocument doc) {
    return HtmlPsiUtil.getRealXmlDocument(doc);
  }

  public static boolean isShortNotationOfBooleanAttributePreferred() {
    return Registry.is("html.prefer.short.notation.of.boolean.attributes", true);
  }

  @TestOnly
  public static void setShortNotationOfBooleanAttributeIsPreferred(boolean value, Disposable parent) {
    final boolean oldValue = isShortNotationOfBooleanAttributePreferred();
    final RegistryValue registryValue = Registry.get("html.prefer.short.notation.of.boolean.attributes");
    registryValue.setValue(value);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        registryValue.setValue(oldValue);
      }
    });
  }

  public static boolean isBooleanAttribute(@NotNull XmlAttributeDescriptor descriptor, @Nullable PsiElement context) {
    if (descriptor.isEnumerated()) {
      final String[] values = descriptor.getEnumeratedValues();
      if (values == null) {
        return false;
      }
      if (values.length == 2) {
        return values[0].isEmpty() && values[1].equals(descriptor.getName())
               || values[1].isEmpty() && values[0].equals(descriptor.getName());
      }
      else if (values.length == 1) {
        return descriptor.getName().equals(values[0]);
      }
    }
    return context != null && isCustomBooleanAttribute(descriptor.getName(), context);
  }

  public static boolean isCustomBooleanAttribute(@NotNull String attributeName, @NotNull PsiElement context) {
    final String entitiesString = getEntitiesString(context, XmlEntitiesInspection.BOOLEAN_ATTRIBUTE_SHORT_NAME);
    if (entitiesString != null) {
      StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
      while (tokenizer.hasMoreElements()) {
        if (tokenizer.nextToken().equalsIgnoreCase(attributeName)) {
          return true;
        }
      }
    }
    return false;
  }

  public static XmlAttributeDescriptor[] getCustomAttributeDescriptors(PsiElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.ATTRIBUTE_SHORT_NAME);
    if (entitiesString == null) return XmlAttributeDescriptor.EMPTY;

    StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
    XmlAttributeDescriptor[] descriptors = new XmlAttributeDescriptor[tokenizer.countTokens()];
    int index = 0;

    while (tokenizer.hasMoreElements()) {
      final String customName = tokenizer.nextToken();
      if (customName.isEmpty()) continue;

      descriptors[index++] = new XmlAttributeDescriptorImpl() {
        @Override
        public String getName(PsiElement context) {
          return customName;
        }

        @Override
        public String getName() {
          return customName;
        }
      };
    }

    return descriptors;
  }

  public static XmlElementDescriptor[] getCustomTagDescriptors(@Nullable PsiElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.TAG_SHORT_NAME);
    if (entitiesString == null) return XmlElementDescriptor.EMPTY_ARRAY;

    StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
    XmlElementDescriptor[] descriptors = new XmlElementDescriptor[tokenizer.countTokens()];
    int index = 0;

    while (tokenizer.hasMoreElements()) {
      final String tagName = tokenizer.nextToken();
      if (tagName.isEmpty()) continue;

      descriptors[index++] = new CustomXmlTagDescriptor(tagName);
    }

    return descriptors;
  }

  public static @Nullable String getEntitiesString(@Nullable PsiElement context, @NotNull String inspectionName) {
    if (context == null) return null;
    PsiFile containingFile = context.getContainingFile().getOriginalFile();

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getCurrentProfile();
    XmlEntitiesInspection inspection = (XmlEntitiesInspection)profile.getUnwrappedTool(inspectionName, containingFile);
    if (inspection != null) {
      return inspection.getAdditionalEntries();
    }
    return null;
  }

  public static XmlAttributeDescriptor[] appendHtmlSpecificAttributeCompletions(final XmlTag declarationTag,
                                                                                XmlAttributeDescriptor[] descriptors,
                                                                                final XmlAttribute context) {
    if (declarationTag instanceof HtmlTag) {
      descriptors = ArrayUtil.mergeArrays(
        descriptors,
        getCustomAttributeDescriptors(context)
      );
      return descriptors;
    }


    boolean isJsfHtmlNamespace = false;
    for (String jsfHtmlUri : XmlUtil.JSF_HTML_URIS) {
      if (declarationTag.getPrefixByNamespace(jsfHtmlUri) != null) {
        isJsfHtmlNamespace = true;
        break;
      }
    }

    if (isJsfHtmlNamespace && declarationTag.getNSDescriptor(XmlUtil.XHTML_URI, true) != null &&
        !XmlUtil.JSP_URI.equals(declarationTag.getNamespace())) {

      descriptors = ArrayUtil.append(
        descriptors,
        new XmlAttributeDescriptorImpl() {
          @Override
          public String getName(PsiElement context) {
            return JSFC;
          }

          @Override
          public String getName() {
            return JSFC;
          }
        },
        XmlAttributeDescriptor.class
      );
    }
    return descriptors;
  }

  public static boolean isHtml5Document(XmlDocument doc) {
    if (doc == null) {
      return false;
    }
    XmlProlog prolog = doc.getProlog();
    XmlDoctype doctype = prolog != null ? prolog.getDoctype() : null;

    final PsiFile htmlFile = doc.getContainingFile();

    final String htmlFileFullName;
    if (htmlFile != null) {
      final VirtualFile vFile = htmlFile.getVirtualFile();
      htmlFileFullName = vFile == null ? htmlFile.getName() : vFile.getPath();
    }
    else {
      htmlFileFullName = "unknown";
    }

    if (doctype == null) {
      LOG.debug("DOCTYPE for " + htmlFileFullName + " is null");
      return isHtmlTagContainingFile(doc) && Html5SchemaProvider.getHtml5SchemaLocation()
        .equals(ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(doc.getProject()));
    }

    final boolean html5Doctype = isHtml5Doctype(doctype);
    final String doctypeDescription = "text: " + doctype.getText() +
                                      ", dtdUri: " + doctype.getDtdUri() +
                                      ", publicId: " + doctype.getPublicId() +
                                      ", markupDecl: " + doctype.getMarkupDecl();
    LOG.debug("DOCTYPE for " + htmlFileFullName + "; " + doctypeDescription + "; HTML5: " + html5Doctype);
    return html5Doctype;
  }

  public static boolean isHtml5Doctype(XmlDoctype doctype) {
    return doctype.getDtdUri() == null && doctype.getPublicId() == null && doctype.getMarkupDecl() == null;
  }

  public static boolean isHtml5Context(XmlElement context) {
    XmlDocument doc = PsiTreeUtil.getParentOfType(context, XmlDocument.class);
    if (doc == null && context != null) {
      return Html5SchemaProvider.getHtml5SchemaLocation()
        .equals(ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(context.getProject()));
    }
    return isHtml5Document(doc);
  }

  public static boolean isHtmlTag(@NotNull XmlTag tag) {
    if (!tag.getLanguage().isKindOf(HTMLLanguage.INSTANCE)) return false;

    XmlDocument doc = PsiTreeUtil.getParentOfType(tag, XmlDocument.class);

    String doctype = null;
    if (doc != null) {
      doctype = XmlUtil.getDtdUri(doc);
    }
    doctype = doctype == null ? ExternalResourceManagerEx.getInstanceEx().getDefaultHtmlDoctype(tag.getProject()) : doctype;
    return XmlUtil.XHTML4_SCHEMA_LOCATION.equals(doctype) ||
           !StringUtil.containsIgnoreCase(doctype, "xhtml");
  }

  public static boolean hasNonHtml5Doctype(XmlElement context) {
    XmlDocument doc = PsiTreeUtil.getParentOfType(context, XmlDocument.class);
    if (doc == null) {
      return false;
    }
    XmlProlog prolog = doc.getProlog();
    XmlDoctype doctype = prolog != null ? prolog.getDoctype() : null;
    return doctype != null && !isHtml5Doctype(doctype);
  }

  public static boolean isHtml5Tag(@NotNull String tagName) {
    return HTML5_TAGS_SET.contains(tagName);
  }

  public static boolean isCustomHtml5Attribute(String attributeName) {
    return attributeName.startsWith(HTML5_DATA_ATTR_PREFIX);
  }

  public static @Nullable String getHrefBase(XmlFile file) {
    final XmlTag root = file.getRootTag();
    final XmlTag head = root != null ? root.findFirstSubTag("head") : null;
    final XmlTag base = head != null ? head.findFirstSubTag("base") : null;
    return base != null ? base.getAttributeValue("href") : null;
  }

  public static boolean isOwnHtmlAttribute(XmlAttributeDescriptor descriptor) {
    // common html attributes are defined mostly in common.rnc, core-scripting.rnc, etc
    // while own tag attributes are defined in meta.rnc
    final PsiElement declaration = descriptor.getDeclaration();
    final PsiFile file = declaration != null ? declaration.getContainingFile() : null;
    final String name = file != null ? file.getName() : null;
    return "meta.rnc".equals(name) || "web-forms.rnc".equals(name)
           || "embed.rnc".equals(name) || "tables.rnc".equals(name)
           || "media.rnc".equals(name);
  }

  public static boolean tagHasHtml5Schema(@NotNull XmlTag context) {
    XmlElementDescriptor descriptor = context.getDescriptor();
    XmlNSDescriptor nsDescriptor = descriptor != null ? descriptor.getNSDescriptor() : null;
    return isHtml5Schema(nsDescriptor);
  }

  public static boolean isHtml5Schema(@Nullable XmlNSDescriptor nsDescriptor) {
    XmlFile descriptorFile = nsDescriptor != null ? nsDescriptor.getDescriptorFile() : null;
    String descriptorPath = descriptorFile != null ? descriptorFile.getVirtualFile().getPath() : null;
    return Objects.equals(Html5SchemaProvider.getHtml5SchemaLocation(), descriptorPath) ||
           Objects.equals(Html5SchemaProvider.getXhtml5SchemaLocation(), descriptorPath);
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
        for (int i = tagStart; i < line.length(); i ++) {
          char ch = line.charAt(i);
          if (!(Character.isAlphabetic(ch) || (i > tagStart && (Character.isDigit(ch) || ch == '-' )))) {
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

  private static class TerminateException extends RuntimeException {
    private static final TerminateException INSTANCE = new TerminateException();
  }

  public static Charset detectCharsetFromMetaTag(@NotNull CharSequence content) {
    // check for <meta http-equiv="charset=CharsetName" > or <meta charset="CharsetName"> and return Charset
    // because we will lightly parse and explicit charset isn't used very often do quick check for applicability
    int charPrefix = StringUtil.indexOf(content, CHARSET);
    do {
      if (charPrefix == -1) return null;
      int charsetPrefixEnd = charPrefix + CHARSET.length();
      while (charsetPrefixEnd < content.length() && Character.isWhitespace(content.charAt(charsetPrefixEnd))) ++charsetPrefixEnd;
      if (charsetPrefixEnd < content.length() && content.charAt(charsetPrefixEnd) == '=') break;
      charPrefix = StringUtil.indexOf(content, CHARSET, charsetPrefixEnd);
    }
    while (true);

    if (content.length() > charPrefix + 200) {
      String name = tryFetchCharsetFromFileContent(content.subSequence(0, charPrefix + 200));
      if (name != null) {
        return CharsetToolkit.forName(name);
      }
    }
    String name = tryFetchCharsetFromFileContent(content);
    return CharsetToolkit.forName(name);
  }

  private static String tryFetchCharsetFromFileContent(@NotNull CharSequence content) {
    final Ref<String> charsetNameRef = new Ref<>();
    try {
      new HtmlBuilderDriver(content).build(new XmlBuilder() {
        final @NonNls Set<String> inTag = new HashSet<>();
        boolean metHttpEquiv;
        boolean metHtml5Charset;

        @Override
        public void doctype(final @Nullable CharSequence publicId,
                            final @Nullable CharSequence systemId,
                            final int startOffset,
                            final int endOffset) {
        }

        @Override
        public ProcessingOrder startTag(final CharSequence localName, final String namespace, final int startOffset, final int endOffset,
                                        final int headerEndOffset) {
          @NonNls String name = StringUtil.toLowerCase(localName.toString());
          inTag.add(name);
          if (!inTag.contains("head") && !"html".equals(name)) terminate();
          return ProcessingOrder.TAGS_AND_ATTRIBUTES;
        }

        private static void terminate() {
          throw TerminateException.INSTANCE;
        }

        @Override
        public void endTag(final CharSequence localName, final String namespace, final int startoffset, final int endoffset) {
          final @NonNls String name = StringUtil.toLowerCase(localName.toString());
          if ("meta".equals(name) && (metHttpEquiv || metHtml5Charset) && contentAttributeValue != null) {
            String charsetName;
            if (metHttpEquiv) {
              int start = contentAttributeValue.indexOf(CHARSET_PREFIX);
              if (start == -1) return;
              start += CHARSET_PREFIX.length();
              int end = contentAttributeValue.indexOf(';', start);
              if (end == -1) end = contentAttributeValue.length();
              charsetName = contentAttributeValue.substring(start, end);
            }
            else /*if (metHttml5Charset) */ {
              charsetName = StringUtil.unquoteString(contentAttributeValue);
            }
            charsetNameRef.set(charsetName);
            terminate();
          }
          if ("head".equals(name)) {
            terminate();
          }
          inTag.remove(name);
          metHttpEquiv = false;
          metHtml5Charset = false;
          contentAttributeValue = null;
        }

        private String contentAttributeValue;

        @Override
        public void attribute(final CharSequence localName, final CharSequence v, final int startoffset, final int endoffset) {
          final @NonNls String name = StringUtil.toLowerCase(localName.toString());
          if (inTag.contains("meta")) {
            @NonNls String value = StringUtil.toLowerCase(v.toString());
            if (name.equals("http-equiv")) {
              metHttpEquiv |= value.equals("content-type");
            }
            else if (name.equals(CHARSET)) {
              metHtml5Charset = true;
              contentAttributeValue = value;
            }
            if (name.equals("content")) {
              contentAttributeValue = value;
            }
          }
        }

        @Override
        public void textElement(final CharSequence display, final CharSequence physical, final int startoffset, final int endoffset) {
        }

        @Override
        public void entityRef(final CharSequence ref, final int startOffset, final int endOffset) {
        }

        @Override
        public void error(@NotNull String message, int startOffset, int endOffset) {
        }
      });
    }
    catch (TerminateException ignored) {
      //ignore
    }
    catch (Exception ignored) {
      // some weird things can happen, like unbalanaced tree
    }

    return charsetNameRef.get();
  }

  public static boolean isTagWithoutAttributes(@NonNls String tagName) {
    return "br".equalsIgnoreCase(tagName);
  }

  public static boolean hasHtml(@NotNull PsiFile file) {
    return isHtmlFile(file) || file.getViewProvider() instanceof TemplateLanguageFileViewProvider;
  }

  public static boolean supportsXmlTypedHandlers(@NotNull PsiFile file) {
    return XmlTypedHandlersAdditionalSupport.supportsTypedHandlers(file);
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

  public static boolean isHtmlFile(@NotNull VirtualFile file) {
    var registry = FileTypeRegistry.getInstance();
    return registry.isFileOfType(file, HtmlFileType.INSTANCE) || registry.isFileOfType(file, XHtmlFileType.INSTANCE);
  }

  public static FileType @NotNull [] getHtmlFileTypes() {
    return new FileType[]{HtmlFileType.INSTANCE, XHtmlFileType.INSTANCE};
  }

  public static boolean isHtmlTagContainingFile(PsiElement element) {
    if (element == null) {
      return false;
    }
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      if (containingFile instanceof HtmlCompatibleFile) {
        return true;
      }
      final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
      if (tag instanceof HtmlTag) {
        return true;
      }
      final XmlDocument document = PsiTreeUtil.getParentOfType(element, XmlDocument.class, false);
      if (document instanceof HtmlDocumentImpl) {
        return true;
      }
      final FileViewProvider provider = containingFile.getViewProvider();
      Language language;
      if (provider instanceof TemplateLanguageFileViewProvider) {
        language = ((TemplateLanguageFileViewProvider)provider).getTemplateDataLanguage();
      }
      else {
        language = provider.getBaseLanguage();
      }

      return language == XHTMLLanguage.INSTANCE;
    }
    return false;
  }

  public static boolean isScriptTag(@Nullable XmlTag tag) {
    return tag != null && tag.getLocalName().equalsIgnoreCase(SCRIPT_TAG_NAME);
  }

  public static class CustomXmlTagDescriptor extends XmlElementDescriptorImpl {
    private final String myTagName;

    CustomXmlTagDescriptor(String tagName) {
      super(null);
      myTagName = tagName;
    }

    @Override
    public String getName(PsiElement context) {
      return myTagName;
    }

    @Override
    public String getDefaultName() {
      return myTagName;
    }

    @Override
    public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
      return true;
    }
  }

  public static @NotNull Iterable<String> splitClassNames(@Nullable String classAttributeValue) {
    // comma is useduse as separator because class name cannot contain comma but it can be part of JSF classes attributes
    return classAttributeValue != null ? StringUtil.tokenize(classAttributeValue, " \t,") : Collections.emptyList();
  }

  @Contract("!null -> !null")
  public static @NlsSafe String getTagPresentation(@Nullable XmlTag tag) {
    if (tag == null) return null;
    StringBuilder builder = new StringBuilder(tag.getLocalName());
    String idValue = getAttributeValue(tag, ID_ATTRIBUTE_NAME);
    if (idValue != null) {
      builder.append('#').append(idValue);
    }
    String classValue = getAttributeValue(tag, CLASS_ATTRIBUTE_NAME);
    if (classValue != null) {
      for (String className : splitClassNames(classValue)) {
        builder.append('.').append(className);
      }
    }
    return builder.toString();
  }

  private static @Nullable String getAttributeValue(@NotNull XmlTag tag, @NotNull String attrName) {
    XmlAttribute classAttribute = getAttributeByName(tag, attrName);
    if (classAttribute != null && !containsOuterLanguageElements(classAttribute)) {
      String value = classAttribute.getValue();
      if (!StringUtil.isEmptyOrSpaces(value)) return value;
    }
    return null;
  }

  private static @Nullable XmlAttribute getAttributeByName(@NotNull XmlTag tag, @NotNull String name) {
    PsiElement child = tag.getFirstChild();
    while (child != null) {
      if (child instanceof XmlAttribute) {
        PsiElement nameElement = child.getFirstChild();
        if (nameElement != null &&
            nameElement.getNode().getElementType() == XmlTokenType.XML_NAME &&
            name.equalsIgnoreCase(nameElement.getText())) {
          return (XmlAttribute)child;
        }
      }
      child = child.getNextSibling();
    }
    return null;
  }

  private static boolean containsOuterLanguageElements(@NotNull PsiElement element) {
    PsiElement child = element.getFirstChild();
    while (child != null) {
      if (child instanceof CompositeElement) {
        return containsOuterLanguageElements(child);
      }
      if (child instanceof OuterLanguageElement) {
        return true;
      }
      child = child.getNextSibling();
    }
    return false;
  }

  public static List<XmlAttributeValue> getIncludedPathsElements(final @NotNull XmlFile file) {
    final List<XmlAttributeValue> result = new ArrayList<>();
    file.acceptChildren(new XmlRecursiveElementWalkingVisitor() {
      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        XmlAttribute attribute = null;
        if ("link".equalsIgnoreCase(tag.getName())) {
          attribute = tag.getAttribute("href");
        }
        else if ("script".equalsIgnoreCase(tag.getName()) || "img".equalsIgnoreCase(tag.getName())) {
          attribute = tag.getAttribute("src");
        }
        if (attribute != null) result.add(attribute.getValueElement());
        super.visitXmlTag(tag);
      }

      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element.getLanguage() instanceof XMLLanguage) {
          super.visitElement(element);
        }
      }
    });
    return result.isEmpty() ? Collections.emptyList() : result;
  }
}
