package com.intellij.xml.util.documentation;

import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.xml.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 24.12.2004
 * Time: 23:55:08
 * To change this template use File | Settings | File Templates.
 */
public class HtmlDocumentationProvider implements JavaDocManager.DocumentationProvider {
  private static String baseHtmlExtDocUrl;
  private static JavaDocManager.DocumentationProvider styleProvider;
  protected Project myProject;

  public HtmlDocumentationProvider(Project project) {
    myProject = project;
  }

  public void registerStyleDocumentationProvider(JavaDocManager.DocumentationProvider documentationProvider) {
    styleProvider = documentationProvider;
  }

  public String getUrlFor(PsiElement element, PsiElement originalElement) {
    String result = getUrlForHtml(element, PsiTreeUtil.getParentOfType(originalElement,XmlTag.class,false));

    if (result == null && styleProvider!=null) {
      result = styleProvider.getUrlFor(element, originalElement);
    }

    return result;
  }

  public String getUrlForHtml(PsiElement element, XmlTag context) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element, context);

    if (descriptor!=null) {
      return baseHtmlExtDocUrl + descriptor.getHelpRef();
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
      // TODO: set schema
    } else if (element.getParent() instanceof XmlAttributeValue) {
      isTag = false;
      key = ((XmlAttribute)element.getParent().getParent()).getName();
    } else {
      nameElement = element;
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
    String result = generateDocForHtml(element, false, PsiTreeUtil.getParentOfType(originalElement,XmlTag.class,false));

    if (result == null && styleProvider!=null) {
      result = styleProvider.generateDoc(element, originalElement);
    }

    return result;
  }

  public String generateDocForHtml(PsiElement element) {
    return generateDocForHtml(element,true, null);
  }

  protected String generateDocForHtml(PsiElement element, boolean ommitHtmlSpecifics, XmlTag context) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element,context);

    if (descriptor!=null) {
      return generateJavaDoc(descriptor, ommitHtmlSpecifics);
    }
    return null;
  }

  private String generateJavaDoc(EntityDescriptor descriptor, boolean ommitHtmlSpecifics) {
    StringBuffer buf = new StringBuffer();

    String entityType;

    if (descriptor instanceof HtmlTagDescriptor) {
      entityType = "Tag";
    } else {
      entityType = "Attribute";
    }

    JavaDocUtil.formatEntityName(entityType + " name",descriptor.getName(),buf);

    buf.append("Description  :&nbsp;").append(descriptor.getDescription()).append("<br>");

    if (descriptor instanceof HtmlTagDescriptor) {
      final HtmlTagDescriptor tagDescriptor = (HtmlTagDescriptor)descriptor;

      if (!ommitHtmlSpecifics) {
        boolean hasStartTag = tagDescriptor.isHasStartTag();
        if (!hasStartTag) {
          buf.append("Start tag    :&nbsp;").append("could be ommitted").append("<br>");
        }
        if (!tagDescriptor.isEmpty() && !tagDescriptor.isHasEndTag()) {
          buf.append("End tag      :&nbsp;").append("could be ommitted").append("<br>");
        }
      }

      if (tagDescriptor.isEmpty()) {
        buf.append("Is empty     :&nbsp;").append("true").append("<br>");
      }
    } else {
      final HtmlAttributeDescriptor attributeDescriptor = (HtmlAttributeDescriptor)descriptor;

      buf.append(  "Attr type    :&nbsp;").append(attributeDescriptor.getType()).append("<br>");
      if (!attributeDescriptor.isHasDefaultValue())
        buf.append("Attr default :&nbsp;").append("required").append("<br>");
    }

    char dtdId = descriptor.getDtd();
    boolean deprecated = dtdId == HtmlTagDescriptor.LOOSE_DTD;
    if (deprecated) {
      buf.append("Deprecated   :&nbsp;").append(deprecated).append("<br>");
    }

    String dtd = (dtdId == HtmlTagDescriptor.LOOSE_DTD)? "loose": (dtdId == HtmlTagDescriptor.FRAME_DTD)?"frameset":"any";
    dtd += " dtd";
    buf.append("Defined in   :&nbsp;").append(dtd).append("<br>");

    return buf.toString();
  }

  public PsiElement getDocumentationElementForLookupItem(Object object, PsiElement element) {
    PsiElement result = createNavigationElementHTML(object.toString(),element);

    if (result== null && styleProvider!=null) {
      result = styleProvider.getDocumentationElementForLookupItem(object, element);
    }
    return result;
  }

  public PsiElement getDocumentationElementForLink(String link, PsiElement context) {
    PsiElement result = createNavigationElementHTML(link, context);

    if (result== null && styleProvider!=null) {
      result = styleProvider.getDocumentationElementForLink(link,context);
    }
    return result;
  }

  public PsiElement createNavigationElementHTML(String text, PsiElement context) {
    String key = text.toLowerCase();
    final HtmlTagDescriptor descriptor = HtmlDescriptorsTable.getTagDescriptor(key);

    if (descriptor!=null && !isAttributeContext(context) ) {
      PsiManager manager = PsiManager.getInstance(myProject);
      final XmlNSDescriptor nsDescriptor = manager.getJspElementFactory().getXHTMLDescriptor();
      if (nsDescriptor!=null) {
        try {
          final XmlTag tagFromText = manager.getElementFactory().createTagFromText("<"+ key + " xmlns=\"" + XmlUtil.XHTML_URI + "\"/>");

          if(tagFromText != null){
            final XmlElementDescriptor elementDescriptor = nsDescriptor.getElementDescriptor(tagFromText);
            return elementDescriptor.getDeclaration();
          }
        } catch(Exception ex) {}
      }
    } else {
      XmlTag tagContext = findTagContext(context);
      HtmlAttributeDescriptor myAttributeDescriptor = getDescriptor(key,tagContext);
      if (myAttributeDescriptor!=null && tagContext!=null) {
        XmlElementDescriptor tagDescriptor = tagContext.getDescriptor();
        XmlAttributeDescriptor attributeDescriptor = tagDescriptor.getAttributeDescriptor(text);
        return attributeDescriptor.getDeclaration();
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
    HtmlDocumentationProvider.baseHtmlExtDocUrl = baseHtmlExtDocUrl;
  }
}
