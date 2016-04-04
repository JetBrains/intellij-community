/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.DocumentationUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.Locale;

/**
 * @author maxim
 */
public class HtmlDocumentationProvider implements DocumentationProvider {
  private DocumentationProvider myStyleProvider = null;
  private final boolean myUseStyleProvider;
  private static DocumentationProvider ourScriptProvider;

  @NonNls public static final String ELEMENT_ELEMENT_NAME = "element";
  @NonNls public static final String NBSP = ":&nbsp;";
  @NonNls public static final String BR = "<br>";

  public HtmlDocumentationProvider() {
    this(true);
  }

  public HtmlDocumentationProvider(boolean useStyleProvider) {
    myUseStyleProvider = useStyleProvider;
  }

  @Override
  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof SchemaPrefix) {
      return ((SchemaPrefix)element).getQuickNavigateInfo();
    }
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    String result = getUrlForHtml(element, PsiTreeUtil.getParentOfType(originalElement, XmlTag.class, false));
    DocumentationProvider styleProvider = getStyleProvider();
    if (result == null && styleProvider != null) {
      return styleProvider.getUrlFor(element, originalElement);
    }

    return result != null ? Collections.singletonList(result) : null;
  }

  public static String getUrlForHtml(PsiElement element, XmlTag context) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element, context);

    if (descriptor!=null) {
      return descriptor.getHelpRef();
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
    } else if (element instanceof XmlElement) {
      nameElement = element;
      isTag = !(element.getParent() instanceof XmlAttribute);
    } else {
      nameElement = element;
      if (context == null) {
        isTag = false;
      }
      else {
        String text = element.getText();
        isTag = text != null && text.startsWith(context.getName());
      }
    }

    if (nameElement!=null) {
      key = nameElement.getText();
    }

    key = StringUtil.notNullize(key).toLowerCase(Locale.US);

    int dotIndex = key.indexOf('.');
    if (dotIndex > 0) {
      key = key.substring(0, dotIndex);
    }

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

  @Override
  public String generateDoc(PsiElement element, PsiElement originalElement) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(originalElement, XmlTag.class, false);
    String result = generateDocForHtml(element, false, tag, originalElement);

    DocumentationProvider styleProvider = getStyleProvider();
    if (result == null && styleProvider !=null) {
      result = styleProvider.generateDoc(element, originalElement);
    }

    if (result == null && ourScriptProvider !=null) {
      result = ourScriptProvider.generateDoc(element, originalElement);
    }

    if (result == null && element instanceof XmlAttributeValue) {
      result = generateDocForHtml(element.getParent(), false, tag, originalElement);
    }

    return result;
  }

  protected String generateDocForHtml(PsiElement element, boolean omitHtmlSpecifics, XmlTag context, PsiElement originalElement) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element,context);

    if (descriptor!=null) {
      return generateJavaDoc(descriptor, omitHtmlSpecifics, originalElement);
    }
    if (element instanceof XmlEntityDecl) {
      final XmlEntityDecl entityDecl = (XmlEntityDecl)element;

      return new XmlDocumentationProvider().findDocRightAfterElement(element, entityDecl.getName());
    }
    return null;
  }

  private static String generateJavaDoc(EntityDescriptor descriptor, boolean omitHtmlSpecifics, PsiElement element) {
    StringBuilder buf = new StringBuilder();
    final boolean isTag = descriptor instanceof HtmlTagDescriptor;

    if (isTag) {
      DocumentationUtil.formatEntityName(XmlBundle.message("xml.javadoc.tag.name.message"),descriptor.getName(),buf);
    } else {
      DocumentationUtil.formatEntityName(XmlBundle.message("xml.javadoc.attribute.name.message"),descriptor.getName(),buf);
    }

    buf.append(XmlBundle.message("xml.javadoc.description.message")).append(NBSP).append(descriptor.getDescription()).append(BR);

    if (isTag) {
      final HtmlTagDescriptor tagDescriptor = (HtmlTagDescriptor)descriptor;

      if (!omitHtmlSpecifics) {
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
    boolean deprecated = dtdId == EntityDescriptor.LOOSE_DTD;
    if (deprecated) {
      buf.append(XmlBundle.message("xml.javadoc.deprecated.message", true)).append(BR);
    }

    if (dtdId == EntityDescriptor.LOOSE_DTD) {
      buf.append(XmlBundle.message("xml.javadoc.defined.in.loose.dtd.message"));
    }
    else if (dtdId == EntityDescriptor.FRAME_DTD) {
      buf.append(XmlBundle.message("xml.javadoc.defined.in.frameset.dtd.message"));
    }
    else {
      buf.append(XmlBundle.message("xml.javadoc.defined.in.any.dtd.message"));
    }

    if (!isTag) {
      ColorSampleLookupValue.addColorPreviewAndCodeToLookup(element,buf);
    }

    if (element != null) {
      buf.append(XmlDocumentationProvider.generateHtmlAdditionalDocTemplate(element));
    }

    return buf.toString();
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    PsiElement result = createNavigationElementHTML(psiManager, object.toString(),element);

    DocumentationProvider styleProvider = getStyleProvider();
    if (result== null && styleProvider !=null) {
      result = styleProvider.getDocumentationElementForLookupItem(psiManager, object, element);
    }
    if (result== null && ourScriptProvider !=null) {
      result = ourScriptProvider.getDocumentationElementForLookupItem(psiManager, object, element);
    }
    if (result == null && object instanceof String && element != null) {
      result = XmlDocumentationProvider.findDeclWithName((String)object, element);
    }
    return result;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    PsiElement result = createNavigationElementHTML(psiManager, link, context);

    DocumentationProvider styleProvider = getStyleProvider();
    if (result== null && styleProvider !=null) {
      result = styleProvider.getDocumentationElementForLink(psiManager, link, context);
    }
    if (result== null && ourScriptProvider != null && !DumbService.isDumb(psiManager.getProject())) {
      result = ourScriptProvider.getDocumentationElementForLink(psiManager, link,context);
    }
    return result;
  }

  public PsiElement createNavigationElementHTML(PsiManager psiManager, String text, PsiElement context) {
    String key = text.toLowerCase(Locale.US);
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

  public static void registerScriptDocumentationProvider(final DocumentationProvider provider) {
    ourScriptProvider = provider;
  }
  
  @Nullable
  private DocumentationProvider getStyleProvider() {
    if (!myUseStyleProvider) return null;
    if (myStyleProvider == null) {
      Language cssLanguage = Language.findLanguageByID("CSS");
      if (cssLanguage != null) {
        myStyleProvider = LanguageDocumentation.INSTANCE.forLanguage(cssLanguage);
      }
    }
    return myStyleProvider;
  }
}
