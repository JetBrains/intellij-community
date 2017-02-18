/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.util;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
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
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.impl.source.html.dtd.HtmlAttributeDescriptorImpl;
import com.intellij.psi.impl.source.parsing.xml.HtmlBuilderDriver;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.documentation.MimeTypeDictionary;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.*;

import java.nio.charset.Charset;
import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public class HtmlUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.HtmlUtil");

  @NonNls private static final String JSFC = "jsfc";
  @NonNls private static final String CHARSET = "charset";
  @NonNls private static final String CHARSET_PREFIX = CHARSET+"=";
  @NonNls private static final String HTML5_DATA_ATTR_PREFIX = "data-";

  public static final String SCRIPT_TAG_NAME = "script";
  public static final String STYLE_TAG_NAME = "style";

  public static final String STYLE_ATTRIBUTE_NAME = STYLE_TAG_NAME;
  public static final String ID_ATTRIBUTE_NAME = "id";
  public static final String CLASS_ATTRIBUTE_NAME = "class";

  public static final String[] CONTENT_TYPES = ArrayUtil.toStringArray(MimeTypeDictionary.getContentTypes());

  @NonNls public static final String MATH_ML_NAMESPACE = "http://www.w3.org/1998/Math/MathML";
  @NonNls public static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

  public static final String[] RFC2616_HEADERS = new String[]{"Accept", "Accept-Charset", "Accept-Encoding", "Accept-Language",
    "Accept-Ranges", "Age", "Allow", "Authorization", "Cache-Control", "Connection", "Content-Encoding", "Content-Language",
    "Content-Length", "Content-Location", "Content-MD5", "Content-Range", "Content-Type", "Date", "ETag", "Expect", "Expires", "From",
    "Host", "If-Match", "If-Modified-Since", "If-None-Match", "If-Range", "If-Unmodified-Since", "Last-Modified", "Location",
    "Max-Forwards", "Pragma", "Proxy-Authenticate", "Proxy-Authorization", "Range", "Referer", "Refresh", "Retry-After", "Server", "TE",
    "Trailer", "Transfer-Encoding", "Upgrade", "User-Agent", "Vary", "Via", "Warning", "WWW-Authenticate"};

  private HtmlUtil() {
  }

  private static final Set<String> EMPTY_TAGS_MAP = new THashSet<>();
  @NonNls private static final String[] OPTIONAL_END_TAGS = {
    //"html",
    "head",
    //"body",
    "p", "li", "dd", "dt", "thead", "tfoot", "tbody", "colgroup", "tr", "th", "td", "option", "embed", "noembed"
  };
  private static final Set<String> OPTIONAL_END_TAGS_MAP = new THashSet<>();

  @NonNls private static final String[] BLOCK_TAGS = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dir", "menu", "pre",
    "dl", "div", "center", "noscript", "noframes", "blockquote", "form", "isindex", "hr", "table", "fieldset", "address",
    // nonexplicitly specified
    "map",
    // flow elements
    "body", "object", "applet", "ins", "del", "dd", "li", "button", "th", "td", "iframe", "comment"
  };

  // flow elements are block or inline, so they should not close <p> for example
  @NonNls private static final String[] POSSIBLY_INLINE_TAGS =
    {"a", "abbr", "acronym", "applet", "b", "basefont", "bdo", "big", "br", "button",
      "cite", "code", "del", "dfn", "em", "font", "i", "iframe", "img", "input", "ins",
      "kbd", "label", "map", "object", "q", "s", "samp", "select", "small", "span", "strike",
      "strong", "sub", "sup", "textarea", "tt", "u", "var"};

  private static final Set<String> BLOCK_TAGS_MAP = new THashSet<>();

  @NonNls private static final String[] INLINE_ELEMENTS_CONTAINER = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "dt"};
  private static final Set<String> INLINE_ELEMENTS_CONTAINER_MAP = new THashSet<>();
  
  private static final Set<String> POSSIBLY_INLINE_TAGS_MAP = new THashSet<>();

  @NonNls private static final String[] HTML5_TAGS = {
    "article", "aside", "audio", "canvas", "command", "datalist", "details", "embed", "figcaption", "figure", "footer", "header",
    "keygen", "mark", "meter", "nav", "output", "progress", "rp", "rt", "ruby", "section", "source", "summary", "time", "video", "wbr",
    "main"
  };
  private static final Set<String> HTML5_TAGS_SET = new THashSet<>();
  private static final Map<String, Set<String>> AUTO_CLOSE_BY_MAP = new THashMap<>();

  static {
    for (HTMLControls.Control control : HTMLControls.getControls()) {
      final String tagName = control.name.toLowerCase(Locale.US);
      if (control.endTag == HTMLControls.TagState.FORBIDDEN) EMPTY_TAGS_MAP.add(tagName);
      AUTO_CLOSE_BY_MAP.put(tagName, new THashSet<>(control.autoClosedBy));
    }
    ContainerUtil.addAll(OPTIONAL_END_TAGS_MAP, OPTIONAL_END_TAGS);
    ContainerUtil.addAll(BLOCK_TAGS_MAP, BLOCK_TAGS);
    ContainerUtil.addAll(INLINE_ELEMENTS_CONTAINER_MAP, INLINE_ELEMENTS_CONTAINER);
    ContainerUtil.addAll(POSSIBLY_INLINE_TAGS_MAP, POSSIBLY_INLINE_TAGS);
    ContainerUtil.addAll(HTML5_TAGS_SET, HTML5_TAGS);
  }

  public static boolean isSingleHtmlTag(String tagName) {
    return EMPTY_TAGS_MAP.contains(tagName.toLowerCase(Locale.US));
  }

  public static boolean isSingleHtmlTagL(String tagName) {
    return EMPTY_TAGS_MAP.contains(tagName);
  }

  public static boolean isOptionalEndForHtmlTag(String tagName) {
    return OPTIONAL_END_TAGS_MAP.contains(tagName.toLowerCase(Locale.US));
  }

  public static boolean isOptionalEndForHtmlTagL(String tagName) {
    return OPTIONAL_END_TAGS_MAP.contains(tagName);
  }

  public static boolean canTerminate(final String childTagName, final String tagName) {
    final Set<String> closingTags = AUTO_CLOSE_BY_MAP.get(tagName);
    return closingTags != null && closingTags.contains(childTagName);
  }

  public static boolean isHtmlBlockTag(String tagName) {
    return BLOCK_TAGS_MAP.contains(tagName.toLowerCase(Locale.US));
  }

  public static boolean isPossiblyInlineTag(String tagName) {
    return POSSIBLY_INLINE_TAGS_MAP.contains(tagName);
  }

  public static boolean isHtmlBlockTagL(String tagName) {
    return BLOCK_TAGS_MAP.contains(tagName);
  }

  public static boolean isInlineTagContainer(String tagName) {
    return INLINE_ELEMENTS_CONTAINER_MAP.contains(tagName.toLowerCase(Locale.US));
  }

  public static boolean isInlineTagContainerL(String tagName) {
    return INLINE_ELEMENTS_CONTAINER_MAP.contains(tagName);
  }

  public static void addHtmlSpecificCompletions(final XmlElementDescriptor descriptor,
                                                final XmlTag element,
                                                final List<XmlElementDescriptor> variants) {
    // add html block completions for tags with optional ends!
    String name = descriptor.getName(element);

    if (name != null && isOptionalEndForHtmlTag(name)) {
      PsiElement parent = element.getParent();

      if (parent != null) {
        // we need grand parent since completion already uses parent's descriptor
        parent = parent.getParent();
      }

      if (parent instanceof HtmlTag) {
        final XmlElementDescriptor parentDescriptor = ((HtmlTag)parent).getDescriptor();

        if (parentDescriptor != descriptor && parentDescriptor != null) {
          for (final XmlElementDescriptor elementsDescriptor : parentDescriptor.getElementsDescriptors((XmlTag)parent)) {
            if (isHtmlBlockTag(elementsDescriptor.getName())) {
              variants.add(elementsDescriptor);
            }
          }
        }
      } else if (parent instanceof HtmlDocumentImpl) {
        final XmlNSDescriptor nsDescriptor = descriptor.getNSDescriptor();
        for (XmlElementDescriptor elementDescriptor : nsDescriptor.getRootElementsDescriptors((XmlDocument)parent)) {
          if (isHtmlBlockTag(elementDescriptor.getName()) && !variants.contains(elementDescriptor)) {
            variants.add(elementDescriptor);
          }
        }
      }
    }
  }

  @Nullable
  public static XmlDocument getRealXmlDocument(@Nullable XmlDocument doc) {
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
    if (descriptor instanceof HtmlAttributeDescriptorImpl && descriptor.isEnumerated()) {
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
  
  public static XmlAttributeDescriptor[] getCustomAttributeDescriptors(XmlElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.ATTRIBUTE_SHORT_NAME);
    if (entitiesString == null) return XmlAttributeDescriptor.EMPTY;

    StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
    XmlAttributeDescriptor[] descriptors = new XmlAttributeDescriptor[tokenizer.countTokens()];
    int index = 0;

    while (tokenizer.hasMoreElements()) {
      final String customName = tokenizer.nextToken();
      if (customName.length() == 0) continue;

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
      if (tagName.length() == 0) continue;

      descriptors[index++] = new CustomXmlTagDescriptor(tagName);
    }

    return descriptors;
  }

  @Nullable
  public static String getEntitiesString(@Nullable PsiElement context, @NotNull String inspectionName) {
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
    if (!isHtmlTagContainingFile(doc)) {
      return false;
    }

    final PsiFile htmlFile = doc.getContainingFile();

    final String htmlFileFullName;
    if (htmlFile != null) {
      final VirtualFile vFile = htmlFile.getVirtualFile();
      if (vFile != null) {
        htmlFileFullName = vFile.getPath();
      }
      else {
        htmlFileFullName = htmlFile.getName();
      }
    }
    else {
      htmlFileFullName = "unknown";
    }

    if (doctype == null) {
      LOG.debug("DOCTYPE for " + htmlFileFullName + " is null");
      return Html5SchemaProvider.getHtml5SchemaLocation()
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
    return isHtml5Document(doc);
  }

  public static boolean isHtmlTag(@NotNull XmlTag tag) {
    if (tag.getLanguage() != HTMLLanguage.INSTANCE) return false;

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

  public static boolean isHtml5Tag(String tagName) {
    return HTML5_TAGS_SET.contains(tagName);
  }

  public static boolean isCustomHtml5Attribute(String attributeName) {
    return attributeName.startsWith(HTML5_DATA_ATTR_PREFIX);
  }

  @Nullable
  public static String getHrefBase(XmlFile file) {
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
    return "meta.rnc".equals(name);
  }

  public static boolean tagHasHtml5Schema(@NotNull XmlTag context) {
    XmlElementDescriptor descriptor = context.getDescriptor();
    if (descriptor != null) {
      XmlNSDescriptor nsDescriptor = descriptor.getNSDescriptor();
      XmlFile descriptorFile = nsDescriptor != null ? nsDescriptor.getDescriptorFile() : null;
      String descriptorPath = descriptorFile != null ? descriptorFile.getVirtualFile().getPath() : null;
      return Comparing.equal(Html5SchemaProvider.getHtml5SchemaLocation(), descriptorPath) ||
             Comparing.equal(Html5SchemaProvider.getXhtml5SchemaLocation(), descriptorPath);
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
      charPrefix = StringUtil.indexOf(content,CHARSET, charsetPrefixEnd);
    } while(true);

    final Ref<String> charsetNameRef = new Ref<>();
    try {
      new HtmlBuilderDriver(content).build(new XmlBuilder() {
        @NonNls final Set<String> inTag = new THashSet<>();
        boolean metHttpEquiv = false;
        boolean metHttml5Charset = false;

        @Override
        public void doctype(@Nullable final CharSequence publicId,
                            @Nullable final CharSequence systemId,
                            final int startOffset,
                            final int endOffset) {
        }

        @Override
        public ProcessingOrder startTag(final CharSequence localName, final String namespace, final int startoffset, final int endoffset,
                                        final int headerEndOffset) {
          @NonNls String name = localName.toString().toLowerCase();
          inTag.add(name);
          if (!inTag.contains("head") && !"html".equals(name)) terminate();
          return ProcessingOrder.TAGS_AND_ATTRIBUTES;
        }

        private void terminate() {
          throw TerminateException.INSTANCE;
        }

        @Override
        public void endTag(final CharSequence localName, final String namespace, final int startoffset, final int endoffset) {
          @NonNls final String name = localName.toString().toLowerCase();
          if ("meta".equals(name) && (metHttpEquiv || metHttml5Charset) && contentAttributeValue != null) {
            String charsetName;
            if (metHttpEquiv) {
              int start = contentAttributeValue.indexOf(CHARSET_PREFIX);
              if (start == -1) return;
              start += CHARSET_PREFIX.length();
              int end = contentAttributeValue.indexOf(';', start);
              if (end == -1) end = contentAttributeValue.length();
              charsetName = contentAttributeValue.substring(start, end);
            } else /*if (metHttml5Charset) */ {
              charsetName = StringUtil.stripQuotesAroundValue(contentAttributeValue);
            }
            charsetNameRef.set(charsetName);
            terminate();
          }
          if ("head".equals(name)) {
            terminate();
          }
          inTag.remove(name);
          metHttpEquiv = false;
          metHttml5Charset = false;
          contentAttributeValue = null;
        }

        private String contentAttributeValue;

        @Override
        public void attribute(final CharSequence localName, final CharSequence v, final int startoffset, final int endoffset) {
          @NonNls final String name = localName.toString().toLowerCase();
          if (inTag.contains("meta")) {
            @NonNls String value = v.toString().toLowerCase();
            if (name.equals("http-equiv")) {
              metHttpEquiv |= value.equals("content-type");
            } else if (name.equals(CHARSET)) {
              metHttml5Charset = true;
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
        public void error(String message, int startOffset, int endOffset) {
        }
      });
    }
    catch (TerminateException ignored) {
      //ignore
    }
    catch (Exception ignored) {
      // some weird things can happen, like unbalanaced tree
    }

    String name = charsetNameRef.get();
    return CharsetToolkit.forName(name);
  }

  public static boolean isTagWithoutAttributes(@NonNls String tagName) {
    return tagName != null && "br".equalsIgnoreCase(tagName);
  }

  public static boolean hasHtml(PsiFile file) {
    return isHtmlFile(file) || file.getViewProvider() instanceof TemplateLanguageFileViewProvider;
  }

  public static boolean supportsXmlTypedHandlers(PsiFile file) {
    Language language = file.getLanguage();
    while (language != null) {
      if ("JavaScript".equals(language.getID())) return true;
      if ("Dart".equals(language.getID())) return true;
      language = language.getBaseLanguage();
    }

    return false;
  }

  public static boolean hasHtmlPrefix(@NotNull String url) {
    return url.startsWith("http://") ||
           url.startsWith("https://") ||
           url.startsWith("//") || //Protocol-relative URL
           url.startsWith("ftp://");
  }

  public static boolean isHtmlFile(@NotNull PsiElement element) {
    Language language = element.getLanguage();
    return language.isKindOf(HTMLLanguage.INSTANCE) || language == XHTMLLanguage.INSTANCE;
  }

  public static boolean isHtmlFile(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    return fileType == HtmlFileType.INSTANCE || fileType == XHtmlFileType.INSTANCE;
  }

  public static boolean isHtmlTagContainingFile(PsiElement element) {
    if (element == null) {
      return false;
    }
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
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

    public CustomXmlTagDescriptor(String tagName) {
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

  @Nullable
  public static Iterable<String> splitClassNames(@Nullable String classAttributeValue) {
    // comma is useduse as separator because class name cannot contain comma but it can be part of JSF classes attributes
    return classAttributeValue != null ? StringUtil.tokenize(classAttributeValue, " \t,") : Collections.emptyList();
  }

  @Contract("!null -> !null")
  public static String getTagPresentation(final @Nullable XmlTag tag) {
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

  @Nullable
  private static String getAttributeValue(@NotNull XmlTag tag, @NotNull String attrName) {
    XmlAttribute classAttribute = getAttributeByName(tag, attrName);
    if (classAttribute != null && !containsOuterLanguageElements(classAttribute)) {
      String value = classAttribute.getValue();
      if (!StringUtil.isEmptyOrSpaces(value)) return value;
    }
    return null;
  }

  @Nullable
  private static XmlAttribute getAttributeByName(@NotNull XmlTag tag, @NotNull String name) {
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
      else if (child instanceof OuterLanguageElement) {
        return true;
      }
      child = child.getNextSibling();
    }
    return false;
  }
}
