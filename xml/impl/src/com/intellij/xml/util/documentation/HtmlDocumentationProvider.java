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
package com.intellij.xml.util.documentation;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.DocumentationUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.ColorSampleLookupValue;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author maxim
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
    if (element instanceof SchemaPrefix) {
      return ((SchemaPrefix)element).getQuickNavigateInfo();
    }
    return null;
  }

  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    String result = getUrlForHtml(element, PsiTreeUtil.getParentOfType(originalElement,XmlTag.class,false));

    if (result == null && ourStyleProvider !=null) {
      return ourStyleProvider.getUrlFor(element, originalElement);
    }

    return result != null ? Collections.singletonList(result) : null;
  }

  public static String getUrlForHtml(PsiElement element, XmlTag context) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element, context);

    if (descriptor!=null) {
      return ourBaseHtmlExtDocUrl + descriptor.getHelpRef();
    } else {
      return null;
    }
  }

  private static EntityDescriptor findDocumentationDescriptor(PsiElement element, XmlTag context) {
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
      final PsiMetaData metaData = xmlTag.getMetaData();
      key = (metaData!=null)?metaData.getName():null;
      isTag = xmlTag.getLocalName().equals(ELEMENT_ELEMENT_NAME);
    } else if (element.getParent() instanceof XmlAttributeValue) {
      isTag = false;
      key = ((XmlAttribute)element.getParent().getParent()).getName();
    } else if (element instanceof XmlAttributeValue) {
      isTag = false;
      final XmlAttribute xmlAttribute = (XmlAttribute)element.getParent();
      key = xmlAttribute.getName();
    } else if (element instanceof XmlAttribute) {
      final XmlAttribute xmlAttribute = (XmlAttribute)element;
      isTag = false;
      key = xmlAttribute.getName();
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

  private static HtmlAttributeDescriptor getDescriptor(String name, XmlTag context) {

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
      DocumentationUtil.formatEntityName(XmlBundle.message("xml.javadoc.tag.name.message"),descriptor.getName(),buf);
    } else {
      DocumentationUtil.formatEntityName(XmlBundle.message("xml.javadoc.attribute.name.message"),descriptor.getName(),buf);
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

    if (!istag) {
      ColorSampleLookupValue.addColorPreviewAndCodeToLookup(element,buf);
    }

    if (element != null) {
      buf.append(XmlDocumentationProvider.generateHtmlAdditionalDocTemplate(element));
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
    if (result == null && object instanceof String && element != null) {
      result = XmlDocumentationProvider.findDeclWithName((String)object, element);
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
        final XmlTag tagFromText = XmlElementFactory.getInstance(psiManager.getProject()).createTagFromText("<"+ key + " xmlns=\"" + XmlUtil.XHTML_URI + "\"/>");
        final XmlElementDescriptor tagDescriptor = tagFromText.getDescriptor();
        return tagDescriptor != null ? tagDescriptor.getDeclaration() : null;
      }
      catch(IncorrectOperationException ignore) {
      }
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
    ourBaseHtmlExtDocUrl = baseHtmlExtDocUrl;
  }

  static String getBaseHtmlExtDocUrl() {
    return ourBaseHtmlExtDocUrl;
  }

  public static void registerScriptDocumentationProvider(final DocumentationProvider provider) {
    ourScriptProvider = provider;
  }
}
