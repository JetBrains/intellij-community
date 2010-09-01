/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection;
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection;
import com.intellij.html.index.Html5CustomAttributesIndex;
import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.impl.source.parsing.xml.HtmlBuilderDriver;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.templateLanguages.TemplateLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.documentation.HtmlDescriptorsTable;
import gnu.trove.THashSet;
import org.apache.commons.collections.Bag;
import org.apache.commons.collections.bag.HashBag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author Maxim.Mossienko
 */
public class HtmlUtil {
  @NonNls private static final String JSFC = "jsfc";
  @NonNls private static final String CHARSET_PREFIX = "charset=";
  public static final String HTML5_DATA_ATTR_PREFIX = "data-";

  private HtmlUtil() {}
  @NonNls private static final String[] EMPTY_TAGS = {
    "base","hr","meta","link","frame","br","basefont","param","img","area","input","isindex","col", /*html 5*/ "source"
  };
  private static final Set<String> EMPTY_TAGS_MAP = new THashSet<String>();
  @NonNls private static final String[] OPTIONAL_END_TAGS = {
    //"html",
    "head",
    //"body",
    "p", "li", "dd", "dt", "thead", "tfoot", "tbody", "colgroup", "tr", "th", "td", "option", "embed", "noembed"
  };
  private static final Set<String> OPTIONAL_END_TAGS_MAP = new THashSet<String>();

  @NonNls private static final String[] BLOCK_TAGS = { "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dir", "menu", "pre",
   "dl", "div", "center", "noscript", "noframes", "blockquote", "form", "isindex", "hr", "table", "fieldset", "address",
   // nonexplicitly specified
   "map",
   // flow elements
   "body", "object", "applet", "ins", "del", "dd", "li", "button", "th", "td", "iframe","comment","nobr"
  };

  // flow elements are block or inline, so they shuld not close <p> for example
  @NonNls private static final String[] POSSIBLY_INLINE_TAGS = { "object", "applet", "ins", "del", "button", "nobr" };

  private static final Set<String> BLOCK_TAGS_MAP = new THashSet<String>();

  @NonNls private static final String[] INLINE_ELEMENTS_CONTAINER = { "p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "dt" };
  private static final Set<String> INLINE_ELEMENTS_CONTAINER_MAP = new THashSet<String>();

  @NonNls private static final String[] EMPTY_ATTRS = { "nowrap", "compact", "disabled", "readonly", "selected", "multiple", "nohref", "ismap", "declare", "noshade", "checked" };
  private static final Set<String> EMPTY_ATTRS_MAP = new THashSet<String>();

  private static final Set<String> POSSIBLY_INLINE_TAGS_MAP = new THashSet<String>();

  static {
    ContainerUtil.addAll(EMPTY_TAGS_MAP, EMPTY_TAGS);
    ContainerUtil.addAll(EMPTY_ATTRS_MAP, EMPTY_ATTRS);
    ContainerUtil.addAll(OPTIONAL_END_TAGS_MAP, OPTIONAL_END_TAGS);
    ContainerUtil.addAll(BLOCK_TAGS_MAP, BLOCK_TAGS);
    ContainerUtil.addAll(INLINE_ELEMENTS_CONTAINER_MAP, INLINE_ELEMENTS_CONTAINER);
    ContainerUtil.addAll(POSSIBLY_INLINE_TAGS_MAP, POSSIBLY_INLINE_TAGS);
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

      if (parent!=null) {
        // we need grand parent since completion already uses parent's descriptor
        parent = parent.getParent();
      }

      if (parent instanceof HtmlTag) {
        final XmlElementDescriptor parentDescriptor = ((HtmlTag)parent).getDescriptor();

        if (parentDescriptor!=descriptor && parentDescriptor!=null) {
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
    if (doc == null) return null;
    final PsiFile containingFile = doc.getContainingFile();

    final PsiFile templateFile = TemplateLanguageUtil.getTemplateFile(containingFile);
    if (templateFile instanceof XmlFile) {
      return ((XmlFile)templateFile).getDocument();
    }
    return doc;
  }

  public static String[] getHtmlTagNames() {
    return HtmlDescriptorsTable.getHtmlTagNames();
  }

  public static XmlAttributeDescriptor[] getCustomAttributeDescriptors(XmlElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.UNKNOWN_ATTRIBUTE);
    boolean html5Context = isHtml5Context(context);
    if (entitiesString == null && !html5Context) return XmlAttributeDescriptor.EMPTY;

    final List<String> customAttrNames;
    if (entitiesString != null) {
      StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
      customAttrNames = new ArrayList<String>(tokenizer.countTokens());

      while (tokenizer.hasMoreElements()) {
        final String customName = tokenizer.nextToken();
        if (customName.length() > 0) {
          customAttrNames.add(customName);
        }
      }
    }
    else {
      customAttrNames = new ArrayList<String>();
    }

    if (context != null && html5Context) {
      final String currentAttrName = context instanceof XmlAttribute ? ((XmlAttribute)context).getName() : "";
      FileBasedIndex.getInstance().processAllKeys(Html5CustomAttributesIndex.ID, new Processor<String>() {
        @Override
        public boolean process(String s) {
          if (!currentAttrName.startsWith(s)) {
            customAttrNames.add(s);
          }
          return true;
        }
      }, context.getProject());
    }

    XmlAttributeDescriptor[] descriptors = new XmlAttributeDescriptor[customAttrNames.size()];

    for (int i = 0, n = customAttrNames.size(); i < n; i++) {
      final String customName = customAttrNames.get(i);
      descriptors[i] = new XmlAttributeDescriptorImpl() {
        public String getName(PsiElement context) {
          return customName;
        }

        public String getName() {
          return customName;
        }
      };
    }

    return descriptors;
  }

  public static XmlElementDescriptor[] getCustomTagDescriptors(XmlElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.UNKNOWN_TAG);
    if (entitiesString == null) return XmlElementDescriptor.EMPTY_ARRAY;

    StringTokenizer tokenizer = new StringTokenizer(entitiesString, ",");
    XmlElementDescriptor[] descriptors = new XmlElementDescriptor[tokenizer.countTokens()];
    int index = 0;

    while(tokenizer.hasMoreElements()) {
      final String tagName = tokenizer.nextToken();
      if (tagName.length() == 0) continue;

      descriptors[index++] = new XmlElementDescriptorImpl(context instanceof XmlTag ? (XmlTag)context:null) {
        public String getName(PsiElement context) {
          return tagName;
        }

        public String getDefaultName() {
          return tagName;
        }

        public boolean allowElementsFromNamespace(final String namespace, final XmlTag context) {
          return true;
        }
      };
    }

    return descriptors;
  }

  @Nullable
  public static String getEntitiesString(XmlElement context, int type) {
    if (context == null) return null;
    PsiFile containingFile = context.getContainingFile().getOriginalFile();

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getInspectionProfile();

    switch(type) {
      case XmlEntitiesInspection.UNKNOWN_TAG:
        LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper) profile.getInspectionTool(HtmlUnknownTagInspection.TAG_SHORT_NAME,
                                                                                                    containingFile);
        HtmlUnknownTagInspection unknownTagInspection = wrapper != null ? (HtmlUnknownTagInspection) wrapper.getTool() : null;
        if (unknownTagInspection != null) {
          return unknownTagInspection.getAdditionalEntries();
        }
        break;
      case XmlEntitiesInspection.UNKNOWN_ATTRIBUTE:
        LocalInspectionToolWrapper wrapper1 = (LocalInspectionToolWrapper) profile.getInspectionTool(HtmlUnknownAttributeInspection.ATTRIBUTE_SHORT_NAME,
                                                                                                     containingFile);
        HtmlUnknownAttributeInspection unknownAttributeInspection = wrapper1 != null ? (HtmlUnknownAttributeInspection) wrapper1.getTool() : null;
        if (unknownAttributeInspection != null) {
          return unknownAttributeInspection.getAdditionalEntries();
        }
        break;
    }

    return null;
  }

  public static XmlAttributeDescriptor[] appendHtmlSpecificAttributeCompletions(final XmlTag declarationTag,
                                                                            XmlAttributeDescriptor[] descriptors,
                                                                            final XmlAttributeImpl context) {
    if (declarationTag instanceof HtmlTag) {
      descriptors = ArrayUtil.mergeArrays(
        descriptors,
        getCustomAttributeDescriptors(context),
        XmlAttributeDescriptor.class
      );
      return descriptors;
    }

    if (declarationTag.getPrefixByNamespace(XmlUtil.JSF_HTML_URI) != null &&
        declarationTag.getNSDescriptor(XmlUtil.XHTML_URI, true) != null &&
        !XmlUtil.JSP_URI.equals(declarationTag.getNamespace())) {

      descriptors = ArrayUtil.append(
        descriptors,
        new XmlAttributeDescriptorImpl() {
          public String getName(PsiElement context) {
            return JSFC;
          }

          public String getName() {
            return JSFC;
          }
        }
      );
    }
    return descriptors;
  }

  public static boolean isHtml5Document(XmlDocument doc) {
    if (!(doc instanceof HtmlDocumentImpl)) {
      return false;
    }
    XmlProlog prolog = doc.getProlog();
    if (prolog == null) {
      return false;
    }
    XmlDoctype doctype = prolog.getDoctype();
    return doctype != null && doctype.getDtdUri() == null && doctype.getPublicId() == null;
  }

  public static boolean isHtml5Context(XmlElement context) {
    XmlDocument doc = PsiTreeUtil.getParentOfType(context, XmlDocument.class);
    return isHtml5Document(doc);
  }

  private static class TerminateException extends RuntimeException {
    private static final TerminateException INSTANCE = new TerminateException();
  }
  public static Charset detectCharsetFromMetaHttpEquiv(@NotNull String content) {
    final Ref<String> charsetNameRef = new Ref<String>();
    try {
      new HtmlBuilderDriver(content).build(new XmlBuilder() {
        @NonNls final Bag inTag = new HashBag();
        boolean metHttpEquiv = false;

        public void doctype(@Nullable final CharSequence publicId, @Nullable final CharSequence systemId, final int startOffset, final int endOffset) {
        }

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

        public void endTag(final CharSequence localName, final String namespace, final int startoffset, final int endoffset) {
          @NonNls final String name = localName.toString().toLowerCase();
          if ("meta".equals(name) && metHttpEquiv && contentAttributeValue != null) {
            int start = contentAttributeValue.indexOf(CHARSET_PREFIX);
            if (start == -1) return;
            start += CHARSET_PREFIX.length();
            int end = contentAttributeValue.indexOf(';', start);
            if (end == -1) end = contentAttributeValue.length();
            String charsetName = contentAttributeValue.substring(start, end);
            charsetNameRef.set(charsetName);
            terminate();
          }
          if ("head".equals(name)) {
            terminate();
          }
          inTag.remove(name);
          metHttpEquiv = false;
          contentAttributeValue = null;
        }

        private String contentAttributeValue;
        public void attribute(final CharSequence localName, final CharSequence v, final int startoffset, final int endoffset) {
          @NonNls final String name = localName.toString().toLowerCase();
          if (inTag.contains("meta")) {
            @NonNls String value = v.toString().toLowerCase();
            if (name.equals("http-equiv")) {
              metHttpEquiv |= value.equals("content-type");
            }
            if (name.equals("content")) {
              contentAttributeValue = value;
            }
          }
        }

        public void textElement(final CharSequence display, final CharSequence physical, final int startoffset, final int endoffset) {
        }

        public void entityRef(final CharSequence ref, final int startOffset, final int endOffset) {
        }

        public void error(String message, int startOffset, int endOffset) {
        }
      });
    }
    catch (TerminateException e) {
     //ignore
    }
    catch (Exception e) {
      // some weird things can happen, like unbalanaced tree
    }

    String name = charsetNameRef.get();
    return CharsetToolkit.forName(name);
  }

  public static boolean isTagWithoutAttributes(@NonNls String tagName) {
    return tagName != null && "br".equalsIgnoreCase(tagName);
  }

  public static boolean hasHtml(PsiFile file) {
    if (isHtmlFile(file)) return true;
    if (file.getViewProvider() instanceof TemplateLanguageFileViewProvider) return true;
    return false;
  }

  private static boolean isHtmlFile(final PsiFile file) {
    final Language language = file.getLanguage();
    if (language == HTMLLanguage.INSTANCE || language == XHTMLLanguage.INSTANCE) {
      return true;
    }
    return false;
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
      else {
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
    }
    return false;
  }

  public static boolean isHtmlTagContainingFile(final Editor editor, final PsiFile file) {
    if (editor == null || file == null || !(file instanceof XmlFile)) {
      return false;
    }
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    return isHtmlTagContainingFile(element);
  }
}
