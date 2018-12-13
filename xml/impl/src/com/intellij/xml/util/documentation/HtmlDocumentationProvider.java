/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.intellij.codeInsight.documentation.DocumentationManager.ORIGINAL_ELEMENT_KEY;

/**
 * @author maxim
 */
public class HtmlDocumentationProvider implements DocumentationProvider, ExternalDocumentationProvider {
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
    String result = getUrlForHtml(element, originalElement, PsiTreeUtil.getParentOfType(originalElement, XmlTag.class, false));
    DocumentationProvider styleProvider = getStyleProvider();
    if (result == null && styleProvider != null) {
      return styleProvider.getUrlFor(element, originalElement);
    }

    return result != null ? Collections.singletonList(result) : null;
  }

  @Nullable
  @Override
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls) {
    final XmlTag tag = element instanceof XmlElement ?
                       ReadAction.compute(() -> PsiTreeUtil.getParentOfType(element, XmlTag.class, false)) :
                       null;
    final SmartPsiElementPointer pointer = element.getUserData(ORIGINAL_ELEMENT_KEY);
    PsiElement originalElement = pointer != null ?
                                 ReadAction.compute((ThrowableComputable<PsiElement, RuntimeException>)pointer::getElement) :
                                 element;
    final EntityDescriptor descriptor = ReadAction.compute(() -> findDocumentationDescriptor(originalElement, tag));
    for (String url : docUrls) {
      if (url.contains("#attr-")) return null;
    }

    String mdn = MdnDocumentationUtil.fetchExternalDocumentation(docUrls, () -> null);
    if (mdn != null) {
      String name = descriptor != null ? descriptor.getName() : ReadAction.compute(() -> SymbolPresentationUtil.getSymbolPresentableText(element));
      return generateJavaDoc(descriptor, name, originalElement, mdn);
    }
    return null;
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return false;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(PsiElement element) {}

  public static String getUrlForHtml(PsiElement element, PsiElement originalElement, XmlTag context) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element, context);
    String mdnUrl = MdnDocumentationUtil.getMdnUrl(getCompatData(descriptor, originalElement));
    if (mdnUrl != null) return mdnUrl;

    if (descriptor instanceof HtmlAttributeDescriptor && context != null) {
      return "https://developer.mozilla.org/docs/Web/HTML/Element/" + context.getName() + "#attr-" + descriptor.getName();
    }

    return descriptor != null ? descriptor.getHelpRef() : null;
  }

  private static EntityDescriptor findDocumentationDescriptor(PsiElement element, XmlTag context) {
    boolean isTag = true;
    PsiElement nameElement = null;
    String key = null;

    if (element instanceof FakePsiElement) {
      element = element.getNavigationElement();
    }

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
      if (context != null) {
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
    String result = generateDocForHtml(element, tag, originalElement);

    DocumentationProvider styleProvider = getStyleProvider();
    if (result == null && styleProvider !=null) {
      result = styleProvider.generateDoc(element, originalElement);
    }

    if (result == null && ourScriptProvider !=null) {
      result = ourScriptProvider.generateDoc(element, originalElement);
    }

    if (result == null && element instanceof XmlAttributeValue) {
      result = generateDocForHtml(element.getParent(), tag, originalElement);
    }

    return result;
  }

  protected String generateDocForHtml(PsiElement element, XmlTag context, PsiElement originalElement) {
    final EntityDescriptor descriptor = findDocumentationDescriptor(element,context);

    if (descriptor != null) {
      String description = descriptor.getDescription();
      if (!description.endsWith(".")) description += ".";
      return generateJavaDoc(descriptor, descriptor.getName(), originalElement, description);
    }
    if (element instanceof XmlEntityDecl) {
      final XmlEntityDecl entityDecl = (XmlEntityDecl)element;

      return new XmlDocumentationProvider().findDocRightAfterElement(element, entityDecl.getName());
    }
    return null;
  }

  private static String generateJavaDoc(EntityDescriptor descriptor,
                                        String name,
                                        PsiElement element,
                                        String description) {
    StringBuilder buf = new StringBuilder();

    buf.append(DocumentationMarkup.DEFINITION_START).append(name).append(DocumentationMarkup.DEFINITION_END);
    buf.append(DocumentationMarkup.CONTENT_START);
    buf.append(StringUtil.capitalize(description));
    buf.append(DocumentationMarkup.CONTENT_END);

    Map data = ReadAction.compute(() -> getCompatData(descriptor, element));
    String compatibilityData = MdnDocumentationUtil.getFormattedCompatibilityData(data);

    boolean deprecated = MdnDocumentationUtil.isDeprecated(data);
    if (deprecated || !compatibilityData.isEmpty()) {
      buf.append(DocumentationMarkup.SECTIONS_START);
    }
    if (deprecated) {
      buf.append(DocumentationMarkup.SECTION_HEADER_START).append("Deprecated");
      buf.append(DocumentationMarkup.SECTION_END);
    }
    if (!compatibilityData.isEmpty()) {
      buf.append(DocumentationMarkup.SECTION_HEADER_START).append("Supported by:");
      buf.append(DocumentationMarkup.SECTION_SEPARATOR).append(compatibilityData);
      buf.append(DocumentationMarkup.SECTION_END);
    }
    if (deprecated || !compatibilityData.isEmpty()) {
      buf.append(DocumentationMarkup.SECTIONS_END);
    }

    return buf.toString();
  }

  @Nullable
  private static Map getCompatData(EntityDescriptor descriptor, @Nullable PsiElement element) {
    XmlAttribute attribute = ReadAction.compute(() -> PsiTreeUtil.getParentOfType(element, XmlAttribute.class, true));
    if (attribute != null) {
      return HtmlCompatibilityData.getAttributeData(attribute.getParent(), attribute.getName());
    } else if (element != null && element.getParent() instanceof XmlTag) {
      return HtmlCompatibilityData.getTagData((XmlTag)element.getParent());
    }
    if (descriptor instanceof HtmlTagDescriptor) {
      return HtmlCompatibilityData.getTagData(descriptor.getName());
    }
    return null;
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
