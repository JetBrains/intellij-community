package com.intellij.xml.util.documentation;

import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.ColorSampleLookupValue;
import com.intellij.xml.util.XmlUtil;
import com.intellij.jsp.impl.TldAttributeDescriptor;
import com.intellij.jsp.impl.TldDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2004
 * Time: 23:55:08
 * To change this template use File | Settings | File Templates.
 */
public class HtmlDocumentationProvider implements DocumentationProvider {
  private static String ourBaseHtmlExtDocUrl;
  private static DocumentationProvider ourStyleProvider;
  private static DocumentationProvider ourScriptProvider;

  @NonNls public static final String ELEMENT_ELEMENT_NAME = "element";
  @NonNls public static final String NBSP = ":&nbsp;";
  @NonNls public static final String BR = "<br>";

  public static void registerStyleDocumentationProvider(DocumentationProvider documentationProvider) {
    ourStyleProvider = documentationProvider;
  }

  @Nullable
  public String getQuickNavigateInfo(PsiElement element) {
    return null; 
  }

  public String getUrlFor(PsiElement element, PsiElement originalElement) {
    String result = getUrlForHtml(element, PsiTreeUtil.getParentOfType(originalElement,XmlTag.class,false));

    if (result == null && ourStyleProvider !=null) {
      result = ourStyleProvider.getUrlFor(element, originalElement);
    }

    return result;
  }

  public String getUrlForHtml(PsiElement element, XmlTag context) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element, context);

    if (descriptor!=null) {
      return ourBaseHtmlExtDocUrl + descriptor.getHelpRef();
    } else {
      return null;
    }
  }

  private EntityDescriptor findDocumentationDescriptor(PsiElement element, XmlTag context) {
    boolean isTag = true;
    PsiElement nameElement = null;
    String key = null;

    if (element instanceof XmlElementDecl) {
      nameElement = ((XmlElementDecl)element).getNameElement();
    } else if (element instanceof XmlAttributeDecl) {
      nameElement = ((XmlAttributeDecl)element).getNameElement();
      isTag = false;
    } else if (element instanceof XmlTag) {
      final XmlTag xmlTag = ((XmlTag)element);
      final PsiMetaDataBase metaData = xmlTag.getMetaData();
      key = (metaData!=null)?metaData.getName():null;
      isTag = xmlTag.getLocalName().equals(ELEMENT_ELEMENT_NAME);

      if (metaData instanceof TldAttributeDescriptor ||
          metaData instanceof TldDescriptor
         ) {
        key = null;
      }
    } else if (element.getParent() instanceof XmlAttributeValue) {
      isTag = false;
      key = ((XmlAttribute)element.getParent().getParent()).getName();
    } else if (element instanceof XmlAttributeValue) {
      isTag = false;
      final XmlAttribute xmlAttribute = (XmlAttribute)element.getParent();

      if (xmlAttribute.getDescriptor() instanceof TldAttributeDescriptor) {
        key = null;
      } else {
        key = xmlAttribute.getName();
      }
    } else if (element instanceof XmlAttribute) {
      final XmlAttribute xmlAttribute = (XmlAttribute)element;
      isTag = false;

      if (xmlAttribute.getDescriptor() instanceof TldAttributeDescriptor) {
        key = null;
      } else {
        key = xmlAttribute.getName();
      }
    } else {
      nameElement = element;
      isTag = !(element.getParent() instanceof XmlAttribute);
    }

    if (nameElement!=null) {
      key = nameElement.getText();
    }

    key = (key != null)?key.toLowerCase():"";

    if (isTag) {
      return HtmlDescriptorsTable.getTagDescriptor(key);
    } else {
      return getDescriptor(key, context);
    }
  }

  HtmlAttributeDescriptor getDescriptor(String name, XmlTag context) {

    HtmlAttributeDescriptor attributeDescriptor = HtmlDescriptorsTable.getAttributeDescriptor(name);
    if (attributeDescriptor instanceof CompositeAttributeTagDescriptor) {
      return ((CompositeAttributeTagDescriptor)attributeDescriptor).findHtmlAttributeInContext(context);
    }

    return attributeDescriptor;
  }

  public String generateDoc(PsiElement element, PsiElement originalElement) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class, false);
    String result = generateDocForHtml(element, false, tag, originalElement);

    if (result == null && ourStyleProvider !=null) {
      result = ourStyleProvider.generateDoc(element, originalElement);
    }
    
    if (result == null && ourScriptProvider !=null) {
      result = ourScriptProvider.generateDoc(element, originalElement);
    }
    
    if (result == null && element instanceof XmlAttributeValue) {
      result = generateDocForHtml(element.getParent(), false, tag, originalElement);
    }

    return result;
  }

  public String generateDocForHtml(PsiElement element) {
    return generateDocForHtml(element,true, null, null);
  }

  protected String generateDocForHtml(PsiElement element, boolean ommitHtmlSpecifics, XmlTag context, PsiElement originalElement) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element,context);

    if (descriptor!=null) {
      return generateJavaDoc(descriptor, ommitHtmlSpecifics, originalElement);
    }
    if (element instanceof XmlEntityDecl) {
      final XmlEntityDecl entityDecl = (XmlEntityDecl)element;

      return XmlDocumentationProvider.findDocRightAfterElement(element, entityDecl.getName());
    }
    return null;
  }

  private static String generateJavaDoc(EntityDescriptor descriptor, boolean ommitHtmlSpecifics, PsiElement element) {
    StringBuilder buf = new StringBuilder();
    final boolean istag = descriptor instanceof HtmlTagDescriptor;
    
    if (istag) {
      JavaDocUtil.formatEntityName(XmlBundle.message("xml.javadoc.tag.name.message"),descriptor.getName(),buf);
    } else {
      JavaDocUtil.formatEntityName(XmlBundle.message("xml.javadoc.attribute.name.message"),descriptor.getName(),buf);
    }

    buf.append(XmlBundle.message("xml.javadoc.description.message")).append(NBSP).append(descriptor.getDescription()).append(BR);

    if (istag) {
      final HtmlTagDescriptor tagDescriptor = (HtmlTagDescriptor)descriptor;

      if (!ommitHtmlSpecifics) {
        boolean hasStartTag = tagDescriptor.isHasStartTag();
        if (!hasStartTag) {
          buf.append(XmlBundle.message("xml.javadoc.start.tag.could.be.omitted.message")).append(BR);
        }
        if (!tagDescriptor.isEmpty() && !tagDescriptor.isHasEndTag()) {
          buf.append(XmlBundle.message("xml.javadoc.end.tag.could.be.omitted.message")).append(BR);
        }
      }

      if (tagDescriptor.isEmpty()) {
        buf.append(XmlBundle.message("xml.javadoc.is.empty.message")).append(BR);
      }
    } else {
      final HtmlAttributeDescriptor attributeDescriptor = (HtmlAttributeDescriptor)descriptor;

      buf.append(XmlBundle.message("xml.javadoc.attr.type.message", attributeDescriptor.getType())).append(BR);
      if (!attributeDescriptor.isHasDefaultValue())
        buf.append(XmlBundle.message("xml.javadoc.attr.default.required.message")).append(BR);
    }

    char dtdId = descriptor.getDtd();
    boolean deprecated = dtdId == HtmlTagDescriptor.LOOSE_DTD;
    if (deprecated) {
      buf.append(XmlBundle.message("xml.javadoc.deprecated.message", deprecated)).append(BR);
    }

    if (dtdId == HtmlTagDescriptor.LOOSE_DTD) {
      buf.append(XmlBundle.message("xml.javadoc.defined.in.loose.dtd.message"));
    }
    else if (dtdId == HtmlTagDescriptor.FRAME_DTD) {
      buf.append(XmlBundle.message("xml.javadoc.defined.in.frameset.dtd.message"));
    }
    else {
      buf.append(XmlBundle.message("xml.javadoc.defined.in.any.dtd.message"));
    }

    buf.append(BR);

    if (!istag) {
      ColorSampleLookupValue.addColorPreviewAndCodeToLookup(element,buf);
    }
    
    return buf.toString();
  }

  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    PsiElement result = createNavigationElementHTML(psiManager, object.toString(),element);

    if (result== null && ourStyleProvider !=null) {
      result = ourStyleProvider.getDocumentationElementForLookupItem(psiManager, object, element);
    }
    if (result== null && ourScriptProvider !=null) {
      result = ourScriptProvider.getDocumentationElementForLookupItem(psiManager, object, element);
    }
    if (result == null && object instanceof String) {
      result = XmlDocumentationProvider.findEntityDeclWithName((String)object, element);
    }
    return result;
  }

  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    PsiElement result = createNavigationElementHTML(psiManager, link, context);

    if (result== null && ourStyleProvider !=null) {
      result = ourStyleProvider.getDocumentationElementForLink(psiManager, link,context);
    }
    if (result== null && ourScriptProvider !=null) {
      result = ourScriptProvider.getDocumentationElementForLink(psiManager, link,context);
    }
    return result;
  }

  public PsiElement createNavigationElementHTML(PsiManager psiManager, String text, PsiElement context) {
    String key = text.toLowerCase();
    final HtmlTagDescriptor descriptor = HtmlDescriptorsTable.getTagDescriptor(key);

    if (descriptor != null && !isAttributeContext(context) ) {
      try {
        final XmlTag tagFromText = psiManager.getElementFactory().createTagFromText("<"+ key + " xmlns=\"" + XmlUtil.XHTML_URI + "\"/>");
        final XmlElementDescriptor tagDescriptor = tagFromText.getDescriptor();
        return tagDescriptor != null ? tagDescriptor.getDeclaration() : null;
      }
      catch(IncorrectOperationException ex) {}
    }
    else {
      XmlTag tagContext = findTagContext(context);
      HtmlAttributeDescriptor myAttributeDescriptor = getDescriptor(key,tagContext);

      if (myAttributeDescriptor != null && tagContext != null) {
        XmlElementDescriptor tagDescriptor = tagContext.getDescriptor();
        XmlAttributeDescriptor attributeDescriptor = tagDescriptor != null ? tagDescriptor.getAttributeDescriptor(text, tagContext): null;

        return (attributeDescriptor != null)?attributeDescriptor.getDeclaration():null;
      }
    }
    return null;
  }

  protected boolean isAttributeContext(PsiElement context) {
    if(context instanceof XmlAttribute) return true;

    if (context instanceof PsiWhiteSpace) {
      PsiElement prevSibling = context.getPrevSibling();
      if (prevSibling instanceof XmlAttribute)
        return true;
    }

    return false;
  }

  protected XmlTag findTagContext(PsiElement context) {
    if (context instanceof PsiWhiteSpace) {
      PsiElement prevSibling = context.getPrevSibling();
      if (prevSibling instanceof XmlTag)
        return (XmlTag)prevSibling;
    }

    return PsiTreeUtil.getParentOfType(context,XmlTag.class,false);
  }

  public static void setBaseHtmlExtDocUrl(String baseHtmlExtDocUrl) {
    HtmlDocumentationProvider.ourBaseHtmlExtDocUrl = baseHtmlExtDocUrl;
  }

  public static void registerScriptDocumentationProvider(final DocumentationProvider provider) {
    ourScriptProvider = provider;
  }
}
