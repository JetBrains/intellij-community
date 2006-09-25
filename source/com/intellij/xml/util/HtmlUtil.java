package com.intellij.xml.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlAttributeImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.schema.XmlAttributeDescriptorImpl;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import com.intellij.xml.util.documentation.HtmlDescriptorsTable;
import com.intellij.codeInspection.htmlInspections.XmlEntitiesInspection;
import com.intellij.codeInspection.htmlInspections.HtmlStyleLocalInspection;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Sep 18, 2004
 * Time: 6:59:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlUtil {
  @NonNls private static final String JSFC = "jsfc";

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
  private static final Set<String> BLOCK_TAGS_MAP = new THashSet<String>();

  @NonNls private static final String[] INLINE_ELEMENTS_CONTAINER = { "p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "dt" };
  private static final Set<String> INLINE_ELEMENTS_CONTAINER_MAP = new THashSet<String>();

  @NonNls private static final String[] EMPTY_ATTRS = { "nowrap", "compact", "disabled", "readonly", "selected", "multiple", "nohref", "ismap", "declare", "noshade", "checked" };
  private static final Set<String> EMPTY_ATTRS_MAP = new THashSet<String>();

  static {
    for (String aEMPTY_TAGS : EMPTY_TAGS) {
      EMPTY_TAGS_MAP.add(aEMPTY_TAGS);
    }
    
    for (String aEMPTY_ATTRS : EMPTY_ATTRS) {
      EMPTY_ATTRS_MAP.add(aEMPTY_ATTRS);
    }
    
    for (String optionalEndTag : OPTIONAL_END_TAGS) {
      OPTIONAL_END_TAGS_MAP.add(optionalEndTag);
    }

    for (String blockTag : BLOCK_TAGS) {
      BLOCK_TAGS_MAP.add(blockTag);
    }

    for (String blockTag : INLINE_ELEMENTS_CONTAINER) {
      INLINE_ELEMENTS_CONTAINER_MAP.add(blockTag);
    }
  }

  public static final boolean isSingleHtmlTag(String tagName) {
    return EMPTY_TAGS_MAP.contains(tagName.toLowerCase());
  }

  public static final boolean isOptionalEndForHtmlTag(String tagName) {
    return OPTIONAL_END_TAGS_MAP.contains(tagName.toLowerCase());
  }

  public static boolean isSingleHtmlAttribute(String attrName) {
    return EMPTY_ATTRS_MAP.contains(attrName.toLowerCase());
  }

  public static boolean isHtmlBlockTag(String tagName) {
    return BLOCK_TAGS_MAP.contains(tagName.toLowerCase());
  }

  public static boolean isInlineTagContainer(String tagName) {
    return INLINE_ELEMENTS_CONTAINER_MAP.contains(tagName.toLowerCase());
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
          final XmlElementDescriptor[] elementsDescriptors = parentDescriptor.getElementsDescriptors((XmlTag)parent);
          for (int i = 0; i < elementsDescriptors.length; i++) {
            final XmlElementDescriptor elementsDescriptor = elementsDescriptors[i];

            if (isHtmlBlockTag(elementsDescriptor.getName())) {
              variants.add(elementsDescriptor);
            }
          }
        }
      }
    }
  }

  public static @Nullable XmlDocument getRealXmlDocument(@Nullable XmlDocument doc) {
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

      descriptors[index++] = new XmlElementDescriptorImpl(null) {
        public String getName(PsiElement context) {
          return tagName;
        }
      };
    }

    return descriptors;
  }

  public static @Nullable
  String getEntitiesString(XmlElement context, int type) {
    if (context == null) return null;
    PsiFile containingFile = context.getContainingFile();
    if (containingFile.getOriginalFile() != null) containingFile = containingFile.getOriginalFile();

    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getInspectionProfile(containingFile);
    LocalInspectionToolWrapper localInspectionToolWrapper = (LocalInspectionToolWrapper) profile.getInspectionTool(HtmlStyleLocalInspection.SHORT_NAME);
    HtmlStyleLocalInspection inspection = localInspectionToolWrapper != null ?
      (HtmlStyleLocalInspection) localInspectionToolWrapper.getTool(): null;

    if (inspection != null) {
      return inspection.getAdditionalEntries(type);
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
    } else if (declarationTag.getNSDescriptor(XmlUtil.JSF_HTML_URI, true) != null && declarationTag.getNSDescriptor(XmlUtil.XHTML_URI, true) != null) {
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
}