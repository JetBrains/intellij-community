package com.intellij.xml.util;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.documentation.HtmlDescriptorsTable;
import com.intellij.psi.xml.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.html.HtmlTag;
import java.util.*;
import gnu.trove.THashSet;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Sep 18, 2004
 * Time: 6:59:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlUtil {
  private HtmlUtil() {}
  private static final String EMPTY_TAGS[] = { "base","hr","meta","link","frame","br","basefont","param","img","area","input","isindex","col" };
  private static final Set<String> EMPTY_TAGS_MAP = new THashSet<String>();
  private static final String OPTIONAL_END_TAGS[] = {
    //"html",
    "head",
    //"body",
    "p", "li", "dd", "dt", "thead", "tfoot", "tbody", "colgroup", "tr", "th", "td", "option"
  };
  private static final Set<String> OPTIONAL_END_TAGS_MAP = new THashSet<String>();

  private static final String BLOCK_TAGS[] = { "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dir", "menu", "pre",
   "dl", "div", "center", "noscript", "noframes", "blockquote", "form", "isindex", "hr", "table", "fieldset", "address",
   // nonexplicitly specified
   "map",
   // flow elements
   "body", "object", "applet", "ins", "del", "dd", "li", "button", "th", "td", "iframe"
  };
  private static final Set<String> BLOCK_TAGS_MAP = new THashSet<String>();

  private static final String INLINE_ELEMENTS_CONTAINER[] = { "p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "dt" };
  private static final Set<String> INLINE_ELEMENTS_CONTAINER_MAP = new THashSet<String>();

  private static final String EMPTY_ATTRS[] = { "nowrap", "compact", "disabled", "readonly", "selected", "multiple", "nohref", "ismap", "declare", "noshade" };
  private static final Set<String> EMPTY_ATTRS_MAP = new THashSet<String>();

  static {
    for(int i=0;i<EMPTY_TAGS.length;++i) {
      EMPTY_TAGS_MAP.add(EMPTY_TAGS[i]);
    }
    for(int i=0;i<EMPTY_ATTRS.length;++i) {
      EMPTY_ATTRS_MAP.add(EMPTY_ATTRS[i]);
    }
    for (int i = 0; i < OPTIONAL_END_TAGS.length; i++) {
      String optionalEndTag = OPTIONAL_END_TAGS[i];
      OPTIONAL_END_TAGS_MAP.add(optionalEndTag);
    }

    for (int i = 0; i < BLOCK_TAGS.length; i++) {
      String blockTag = BLOCK_TAGS[i];
      BLOCK_TAGS_MAP.add(blockTag);
    }

    for (int i = 0; i < INLINE_ELEMENTS_CONTAINER.length; i++) {
      String blockTag = INLINE_ELEMENTS_CONTAINER[i];
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

    if (isOptionalEndForHtmlTag(name)) {
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

  public static String[] getHtmlTagNames() {
    return HtmlDescriptorsTable.getHtmlTagNames();
  }
}