package com.intellij.xml.util;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.DynamicFileReferenceSet;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JspReferencesProvider;
import com.intellij.psi.impl.source.jsp.jspJava.JspDirective;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.documentation.HtmlDescriptorsTable;
import gnu.trove.THashSet;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Sep 18, 2004
 * Time: 6:59:31 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlUtil {
  private HtmlUtil() {}
  @NonNls private static final String EMPTY_TAGS[] = { "base","hr","meta","link","frame","br","basefont","param","img","area","input","isindex","col" };
  private static final Set<String> EMPTY_TAGS_MAP = new THashSet<String>();
  @NonNls private static final String OPTIONAL_END_TAGS[] = {
    //"html",
    "head",
    //"body",
    "p", "li", "dd", "dt", "thead", "tfoot", "tbody", "colgroup", "tr", "th", "td", "option"
  };
  private static final Set<String> OPTIONAL_END_TAGS_MAP = new THashSet<String>();

  @NonNls private static final String BLOCK_TAGS[] = { "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dir", "menu", "pre",
   "dl", "div", "center", "noscript", "noframes", "blockquote", "form", "isindex", "hr", "table", "fieldset", "address",
   // nonexplicitly specified
   "map",
   // flow elements
   "body", "object", "applet", "ins", "del", "dd", "li", "button", "th", "td", "iframe"
  };
  private static final Set<String> BLOCK_TAGS_MAP = new THashSet<String>();

  @NonNls private static final String INLINE_ELEMENTS_CONTAINER[] = { "p", "h1", "h2", "h3", "h4", "h5", "h6", "pre", "dt" };
  private static final Set<String> INLINE_ELEMENTS_CONTAINER_MAP = new THashSet<String>();

  @NonNls private static final String EMPTY_ATTRS[] = { "nowrap", "compact", "disabled", "readonly", "selected", "multiple", "nohref", "ismap", "declare", "noshade", "checked" };
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

  public static XmlDocument getRealXmlDocument(XmlDocument doc) {
    final PsiFile containingFile = doc.getContainingFile();

    if (containingFile instanceof JspFile) {
      final PsiFile baseLanguageRoot = ((JspFile)containingFile).getBaseLanguageRoot();
      final PsiElement[] children = baseLanguageRoot.getChildren();

      for (int i = 0; i < children.length; i++) {
        PsiElement child = children[i];

        if (child instanceof XmlDocument) {
          doc = (XmlDocument)child;
          break;
        }
      }
    }
    return doc;
  }

  public static class HtmlReferenceProvider implements PsiReferenceProvider {
    private static final Key<PsiReference[]> cachedReferencesKey = Key.create("html.cachedReferences");
    private static final Key<String>         cachedRefsTextKey = Key.create("html.cachedReferences.text");
    @NonNls
    public static final String NAME_ATTR_LOCAL_NAME = "name";
    @NonNls
    public static final String MAILTO_PREFIX = "mailto:";
    @NonNls
    public static final String JAVASCRIPT_PREFIX = "javascript:";

    public ElementFilter getFilter() {
      return new ElementFilter() {
        public boolean isAcceptable(Object _element, PsiElement context) {
          PsiElement element = (PsiElement) _element;
          PsiFile file = element.getContainingFile();

          if (file.getFileType() == StdFileTypes.HTML ||
              file.getFileType() == StdFileTypes.XHTML ||
              file.getFileType() == StdFileTypes.JSPX ||
              file.getFileType() == StdFileTypes.JSP
              ) {
            final PsiElement parent = element.getParent();

            if (parent instanceof XmlAttribute) {
              XmlAttribute xmlAttribute = (XmlAttribute) parent;
              @NonNls final String attrName = xmlAttribute.getName();
              XmlTag tag = xmlAttribute.getParent();
              @NonNls final String tagName = tag.getName();

              return
               ( attrName.equalsIgnoreCase("src") &&
                 (tagName.equalsIgnoreCase("img") ||
                  tagName.equalsIgnoreCase("script") ||
                  tagName.equalsIgnoreCase("frame") ||
                  tagName.equalsIgnoreCase("iframe")
                 )
               ) ||
                 ( attrName.equalsIgnoreCase("href") &&
                   ( tagName.equalsIgnoreCase("a") ||
                     tagName.equalsIgnoreCase("link") ||
                     tagName.equalsIgnoreCase("area")
                   )
                 ) ||
                   ( attrName.equalsIgnoreCase("action") &&
                     tagName.equalsIgnoreCase("form")
                   ) ||
                     attrName.equalsIgnoreCase("background") ||
                     ( attrName.equals(NAME_ATTR_LOCAL_NAME) &&
                       tag.getNamespacePrefix().length() == 0 &&
                       !(tag instanceof JspDirective)
                     );
            }
          }
          return false;
        }

        public boolean isClassAcceptable(Class hintClass) {
          return true;
        }
      };
    }

    public PsiReference[] getReferencesByElement(PsiElement element) {
      PsiReference[] refs = element.getUserData(cachedReferencesKey);
      String originalText = element.getText();

      if (refs != null) {
        String text = element.getUserData(cachedRefsTextKey);
        if (text != null && text.equals(originalText)) return refs;
      }

      final XmlAttribute attribute = (XmlAttribute)element.getParent();
      final String localName = attribute.getLocalName();

      if (//"id".equals(localName) || 
          NAME_ATTR_LOCAL_NAME.equals(localName)) {
        refs = new PsiReference[] { new JspReferencesProvider.SelfReference(element)};
      } else {
        String text = originalText;
        int offset = 0;
        if (text.length() > 0 &&
            (text.charAt(0) == '"' || text.charAt(0) == '\'')
           ) {
          ++offset;
        }

        text = StringUtil.stripQuotesAroundValue(text);
        int ind = text.lastIndexOf('#');
        String anchor = null;
        if (ind != -1) {
          anchor = text.substring(ind+1);
          text = text.substring(0,ind);
        }

        ind = text.lastIndexOf('?');
        if (ind!=-1) text = text.substring(0,ind);

        if (text.length() > 0 && text.indexOf("://") == -1 && !text.startsWith(MAILTO_PREFIX) &&
            !text.startsWith(JAVASCRIPT_PREFIX)
           ) {
          refs = new DynamicFileReferenceSet(text, element, offset, ReferenceType.FILE_TYPE, this, true).getAllReferences();
        } else {
          refs = PsiReference.EMPTY_ARRAY;
        }

        if (anchor != null &&
            (refs.length > 0 || originalText.regionMatches(1+offset,anchor,0,anchor.length()))
            ) {
          PsiReference[] newrefs = new PsiReference[refs.length+1];
          System.arraycopy(refs,0,newrefs,0,refs.length);
          newrefs[refs.length] = new AnchorReference(anchor, refs.length > 0 ? refs[refs.length-1]:null,element);
          refs = newrefs;
        }
      }

      element.putUserData(cachedReferencesKey,refs);
      element.putUserData(cachedRefsTextKey,originalText);

      return refs;
    }

    public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
      return PsiReference.EMPTY_ARRAY;
    }

    public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
      return PsiReference.EMPTY_ARRAY;
    }

    public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {

    }
  }

  public static String[] getHtmlTagNames() {
    return HtmlDescriptorsTable.getHtmlTagNames();
  }

}