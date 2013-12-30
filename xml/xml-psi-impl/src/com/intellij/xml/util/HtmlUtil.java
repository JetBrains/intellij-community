/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.html.RelaxedHtmlNSDescriptor;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.impl.source.parsing.xml.HtmlBuilderDriver;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.Html5SchemaProvider;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.documentation.HtmlDescriptorsTable;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author Maxim.Mossienko
 */
public class HtmlUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.HtmlUtil");

  @NonNls private static final String JSFC = "jsfc";
  @NonNls private static final String CHARSET = "charset";
  @NonNls private static final String CHARSET_PREFIX = CHARSET+"=";
  @NonNls private static final String HTML5_DATA_ATTR_PREFIX = "data-";

  public static final String[] CONTENT_TYPES =
    {"application/activemessage", "application/andrew-inset", "application/applefile", "application/atomicmail", "application/dca-rft",
      "application/dec-dx", "application/mac-binhex40"
      , "application/mac-compactpro", "application/macwriteii", "application/msword", "application/news-message-id",
      "application/news-transmission", "application/octet-stream"
      , "application/oda", "application/pdf", "application/postscript", "application/powerpoint", "application/remote-printing",
      "application/rtf"
      , "application/slate", "application/wita", "application/wordperfect5.1", "application/x-bcpio", "application/x-cdlink",
      "application/x-compress"
      , "application/x-cpio", "application/x-csh", "application/x-director", "application/x-dvi", "application/x-gtar", "application/x-gzip"
      , "application/x-hdf", "application/x-httpd-cgi", "application/x-koan", "application/x-latex", "application/x-mif",
      "application/x-netcdf"
      , "application/x-sh", "application/x-shar", "application/x-stuffit", "application/x-sv4cpio", "application/x-sv4crc",
      "application/x-tar"
      , "application/x-tcl", "application/x-tex", "application/x-texinfo", "application/x-troff", "application/x-troff-man",
      "application/x-troff-me"
      , "application/x-troff-ms", "application/x-ustar", "application/x-wais-source", "application/zip", "audio/basic", "audio/mpeg"
      , "audio/x-aiff", "audio/x-pn-realaudio", "audio/x-pn-realaudio-plugin", "audio/x-realaudio", "audio/x-wav", "chemical/x-pdb"
      , "image/gif", "image/ief", "image/jpeg", "image/png", "image/tiff", "image/x-cmu-raster"
      , "image/x-portable-anymap", "image/x-portable-bitmap", "image/x-portable-graymap", "image/x-portable-pixmap", "image/x-rgb",
      "image/x-xbitmap"
      , "image/x-xpixmap", "image/x-xwindowdump", "message/external-body", "message/news", "message/partial", "message/rfc822"
      , "multipart/alternative", "multipart/appledouble", "multipart/digest", "multipart/mixed", "multipart/parallel", "text/html"
      , "text/plain", "text/richtext", "text/tab-separated-values", "text/x-setext", "text/x-sgml", "video/mpeg"
      , "video/quicktime", "video/x-msvideo", "video/x-sgi-movie", "x-conference/x-cooltalk", "x-world/x-vrml"};

  @NonNls public static final String MATH_ML_NAMESPACE = "http://www.w3.org/1998/Math/MathML";
  @NonNls public static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";

  private HtmlUtil() {
  }

  @NonNls private static final String[] EMPTY_TAGS = {
    "base", "hr", "meta", "link", "frame", "br", "basefont", "param", "img", "area", "input", "isindex", "col", /*html 5*/ "source", "wbr"
  };
  private static final Set<String> EMPTY_TAGS_MAP = new THashSet<String>();
  @NonNls private static final String[] OPTIONAL_END_TAGS = {
    //"html",
    "head",
    //"body",
    "p", "li", "dd", "dt", "thead", "tfoot", "tbody", "colgroup", "tr", "th", "td", "option", "embed", "noembed"
  };
  private static final Set<String> OPTIONAL_END_TAGS_MAP = new THashSet<String>();

  @NonNls private static final String[] BLOCK_TAGS = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dir", "menu", "pre",
    "dl", "div", "center", "noscript", "noframes", "blockquote", "form", "isindex", "hr", "table", "fieldset", "address",
    // nonexplicitly specified
    "map",
    // flow elements
    "body", "object", "applet", "ins", "del", "dd", "li", "button", "th", "td", "iframe", "comment", "nobr"
  };

  // flow elements are block or inline, so they should not close <p> for example
  @NonNls private static final String[] POSSIBLY_INLINE_TAGS =
    {"a", "abbr", "acronym", "applet", "b", "basefont", "bdo", "big", "br", "button",
      "cite", "code", "del", "dfn", "em", "font", "i", "iframe", "img", "input", "ins",
      "kbd", "label", "map", "object", "q", "s", "samp", "select", "small", "span", "strike",
      "strong", "sub", "sup", "textarea", "tt", "u", "var"};

  private static final Set<String> BLOCK_TAGS_MAP = new THashSet<String>();

  @NonNls private static final String[] INLINE_ELEMENTS_CONTAINER = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "dt"};
  private static final Set<String> INLINE_ELEMENTS_CONTAINER_MAP = new THashSet<String>();

  @NonNls private static final String[] EMPTY_ATTRS =
    {"nowrap", "compact", "disabled", "readonly", "selected", "multiple", "nohref", "ismap", "declare", "noshade", "checked"};
  private static final Set<String> EMPTY_ATTRS_MAP = new THashSet<String>();

  private static final Set<String> POSSIBLY_INLINE_TAGS_MAP = new THashSet<String>();

  @NonNls private static final String[] HTML5_TAGS = {
    "article", "aside", "audio", "canvas", "command", "datalist", "details", "embed", "figcaption", "figure", "footer", "header", "hgroup",
    "keygen", "mark", "meter", "nav", "output", "progress", "rp", "rt", "ruby", "section", "source", "summary", "time", "video", "wbr",
    "main"
  };
  private static final Set<String> HTML5_TAGS_SET = new THashSet<String>();

  static {
    ContainerUtil.addAll(EMPTY_TAGS_MAP, EMPTY_TAGS);
    ContainerUtil.addAll(EMPTY_ATTRS_MAP, EMPTY_ATTRS);
    ContainerUtil.addAll(OPTIONAL_END_TAGS_MAP, OPTIONAL_END_TAGS);
    ContainerUtil.addAll(BLOCK_TAGS_MAP, BLOCK_TAGS);
    ContainerUtil.addAll(INLINE_ELEMENTS_CONTAINER_MAP, INLINE_ELEMENTS_CONTAINER);
    ContainerUtil.addAll(POSSIBLY_INLINE_TAGS_MAP, POSSIBLY_INLINE_TAGS);
    ContainerUtil.addAll(HTML5_TAGS_SET, HTML5_TAGS);
  }

  public static boolean isSingleHtmlTag(String tagName) {
    return EMPTY_TAGS_MAP.contains(tagName.toLowerCase());
  }

  public static boolean isSingleHtmlTagL(String tagName) {
    return EMPTY_TAGS_MAP.contains(tagName);
  }

  public static boolean isOptionalEndForHtmlTag(String tagName) {
    return OPTIONAL_END_TAGS_MAP.contains(tagName.toLowerCase());
  }

  public static boolean isOptionalEndForHtmlTagL(String tagName) {
    return OPTIONAL_END_TAGS_MAP.contains(tagName);
  }

  public static boolean isSingleHtmlAttribute(String attrName) {
    return EMPTY_ATTRS_MAP.contains(attrName.toLowerCase());
  }

  public static boolean isHtmlBlockTag(String tagName) {
    return BLOCK_TAGS_MAP.contains(tagName.toLowerCase());
  }

  public static boolean isPossiblyInlineTag(String tagName) {
    return POSSIBLY_INLINE_TAGS_MAP.contains(tagName);
  }

  public static boolean isHtmlBlockTagL(String tagName) {
    return BLOCK_TAGS_MAP.contains(tagName);
  }

  public static boolean isInlineTagContainer(String tagName) {
    return INLINE_ELEMENTS_CONTAINER_MAP.contains(tagName.toLowerCase());
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
      }
    }
  }

  @Nullable
  public static XmlDocument getRealXmlDocument(@Nullable XmlDocument doc) {
    return HtmlPsiUtil.getRealXmlDocument(doc);
  }

  public static String[] getHtmlTagNames() {
    return HtmlDescriptorsTable.getHtmlTagNames();
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

  public static XmlElementDescriptor[] getCustomTagDescriptors(XmlElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.TAG_SHORT_NAME);
    if (entitiesString == null) return XmlElementDescriptor.EMPTY_ARRAY;

    StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
    XmlElementDescriptor[] descriptors = new XmlElementDescriptor[tokenizer.countTokens()];
    int index = 0;

    while (tokenizer.hasMoreElements()) {
      final String tagName = tokenizer.nextToken();
      if (tagName.length() == 0) continue;

      descriptors[index++] = new XmlElementDescriptorImpl(context instanceof XmlTag ? (XmlTag)context : null) {
        @Override
        public String getName(PsiElement context) {
          return tagName;
        }

        @Override
        public String getDefaultName() {
          return tagName;
        }

        @Override
        public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
          return true;
        }
      };
    }

    return descriptors;
  }

  @Nullable
  public static String getEntitiesString(XmlElement context, String inspectionName) {
    if (context == null) return null;
    PsiFile containingFile = context.getContainingFile().getOriginalFile();

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getInspectionProfile();
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
    final XmlElementDescriptor descriptor = tag.getDescriptor();
    return descriptor != null && descriptor.getNSDescriptor() instanceof RelaxedHtmlNSDescriptor &&
           tag.getLanguage() != XHTMLLanguage.INSTANCE;
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

  private static class TerminateException extends RuntimeException {
    private static final TerminateException INSTANCE = new TerminateException();
  }

  public static Charset detectCharsetFromMetaTag(@NotNull String content) {
    // check for <meta http-equiv="charset=CharsetName" > or <meta charset="CharsetName"> and return Charset
    // because we will lightly parse and explicit charset isn't used very often do quick check for applicability
    int charPrefix = content.indexOf(CHARSET);
    do {
      if (charPrefix == -1) return null;
      int charsetPrefixEnd = charPrefix + CHARSET.length();
      while (charsetPrefixEnd < content.length() && Character.isWhitespace(content.charAt(charsetPrefixEnd))) ++charsetPrefixEnd;
      if (charsetPrefixEnd < content.length() && content.charAt(charsetPrefixEnd) == '=') break;
      charPrefix = content.indexOf(CHARSET, charsetPrefixEnd);
    } while(true);

    final Ref<String> charsetNameRef = new Ref<String>();
    try {
      new HtmlBuilderDriver(content).build(new XmlBuilder() {
        @NonNls final Set<String> inTag = new THashSet<String>();
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
            String charsetName = null;
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

  public static boolean hasHtmlPrefix(@NotNull String url) {
    return url.startsWith("http://") ||
           url.startsWith("https://") ||
           url.startsWith("//") || //Protocol-relative URL
           url.startsWith("ftp://");
  }

  public static boolean isHtmlFile(@NotNull PsiElement element) {
    Language language = element.getLanguage();
    return language == HTMLLanguage.INSTANCE || language == XHTMLLanguage.INSTANCE;
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
}
