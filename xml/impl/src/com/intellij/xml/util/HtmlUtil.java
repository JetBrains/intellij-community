package com.intellij.xml.util;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection;
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.parsing.xml.HtmlBuilderDriver;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author Maxim.Mossienko
 */
public class HtmlUtil {
  @NonNls private static final String JSFC = "jsfc";
  @NonNls private static final String CHARSET_PREFIX = "charset=";

  private HtmlUtil() {}
  @NonNls private static final String[] EMPTY_TAGS = { 
    "base","hr","meta","link","frame","br","basefont","param","img","area","input","isindex","col"
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
    EMPTY_TAGS_MAP.addAll(Arrays.asList(EMPTY_TAGS));
    EMPTY_ATTRS_MAP.addAll(Arrays.asList(EMPTY_ATTRS));
    OPTIONAL_END_TAGS_MAP.addAll(Arrays.asList(OPTIONAL_END_TAGS));
    BLOCK_TAGS_MAP.addAll(Arrays.asList(BLOCK_TAGS));
    INLINE_ELEMENTS_CONTAINER_MAP.addAll(Arrays.asList(INLINE_ELEMENTS_CONTAINER));
    POSSIBLY_INLINE_TAGS_MAP.addAll(Arrays.asList(POSSIBLY_INLINE_TAGS));
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

    if (PsiUtil.isInJspFile(containingFile)) {
      final JspFile jspFile = PsiUtil.getJspFile(containingFile);
      
      if (jspFile != null) { // it may be for some reason
        final PsiFile baseLanguageRoot = jspFile.getBaseLanguageRoot();
        final PsiElement[] children = baseLanguageRoot.getChildren();

        for (PsiElement child : children) {
          if (child instanceof XmlDocument) {
            doc = (XmlDocument)child;
            break;
          }
        }
      }
    }
    return doc;
  }

  public static String[] getHtmlTagNames() {
    return HtmlDescriptorsTable.getHtmlTagNames();
  }

  public static XmlAttributeDescriptor[] getCustomAttributeDescriptors(XmlElement context) {
    String entitiesString = getEntitiesString(context, XmlEntitiesInspection.UNKNOWN_ATTRIBUTE);
    if (entitiesString == null) return XmlAttributeDescriptor.EMPTY;

    StringTokenizer tokenizer = new StringTokenizer(entitiesString,",");
    XmlAttributeDescriptor[] descriptors = new XmlAttributeDescriptor[tokenizer.countTokens()];
    int index = 0;

    while(tokenizer.hasMoreElements()) {
      final String customName = tokenizer.nextToken();
      if (customName.length() == 0) continue;

      descriptors[index++] = new XmlAttributeDescriptorImpl() {
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
    PsiFile containingFile = context.getContainingFile();
    if (containingFile.getOriginalFile() != null) {
      containingFile = containingFile.getOriginalFile();
    }

    assert containingFile != null;

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getInspectionProfile(containingFile);

    switch(type) {
      case XmlEntitiesInspection.UNKNOWN_TAG:
        LocalInspectionToolWrapper wrapper = (LocalInspectionToolWrapper) profile.getInspectionTool(HtmlUnknownTagInspection.TAG_SHORT_NAME);
        HtmlUnknownTagInspection unknownTagInspection = wrapper != null ? (HtmlUnknownTagInspection) wrapper.getTool() : null;
        if (unknownTagInspection != null) {
          return unknownTagInspection.getAdditionalEntries();
        }
        break;
      case XmlEntitiesInspection.UNKNOWN_ATTRIBUTE:
        LocalInspectionToolWrapper wrapper1 = (LocalInspectionToolWrapper) profile.getInspectionTool(HtmlUnknownAttributeInspection.ATTRIBUTE_SHORT_NAME);
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
    } else if (declarationTag.getNSDescriptor(XmlUtil.JSF_HTML_URI, true) != null &&
               declarationTag.getNSDescriptor(XmlUtil.XHTML_URI, true) != null && !XmlUtil.JSP_URI.equals(declarationTag.getNamespace())
              ) {
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

  private static class TerminateException extends RuntimeException {
    private static final TerminateException INSTANCE = new TerminateException();
  }
  public static Charset detectCharsetFromMetaHttpEquiv(@NotNull String content) {
    final Ref<String> charsetNameRef = new Ref<String>();
    try {
      new HtmlBuilderDriver(content).build(new XmlBuilder() {
        @NonNls final Bag inTag = new HashBag();
        boolean metHttpEquiv = false;
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
          if ("head".equals(name)) {
            terminate();  
          }
          inTag.remove(name);
          metHttpEquiv = false;
        }

        public void attribute(final CharSequence localName, final CharSequence v, final int startoffset, final int endoffset) {
          @NonNls final String name = localName.toString().toLowerCase();
          if (inTag.contains("meta")) {
            @NonNls String value = v.toString().toLowerCase();
            if (name.equals("http-equiv")) {
              metHttpEquiv |= value.equals("content-type");
            }
            if (metHttpEquiv && name.equals("content")) {
              int start = value.indexOf(CHARSET_PREFIX);
              if (start == -1) return;
              start += CHARSET_PREFIX.length();
              int end = value.indexOf(';', start);
              if (end == -1) end = value.length();
              String charsetName = value.substring(start, end);
              charsetNameRef.set(charsetName);
              terminate();
            }
          }
        }

        public void textElement(final CharSequence display, final CharSequence physical, final int startoffset, final int endoffset) {

        }

        public void entityRef(final CharSequence ref, final int startOffset, final int endOffset) {

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

}