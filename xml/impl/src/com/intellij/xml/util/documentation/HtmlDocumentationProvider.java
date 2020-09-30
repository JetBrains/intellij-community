// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util.documentation;

import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.impl.source.html.dtd.HtmlAttributeDescriptorImpl;
import com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.codeInsight.documentation.DocumentationManager.ORIGINAL_ELEMENT_KEY;

/**
 * @author maxim
 */
public class HtmlDocumentationProvider implements DocumentationProvider, ExternalDocumentationProvider {
  public static final ExtensionPointName<DocumentationProvider> SCRIPT_PROVIDER_EP_NAME = ExtensionPointName.create("com.intellij.html.scriptDocumentationProvider");
  public static final String ATTR_PREFIX = "#attr-";

  private final boolean myUseStyleProvider;

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
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls, boolean onHover) {
    final XmlTag tag = element instanceof XmlElement ?
                       ReadAction.compute(() -> PsiTreeUtil.getParentOfType(element, XmlTag.class, false)) :
                       null;
    final SmartPsiElementPointer<?> pointer = element.getUserData(ORIGINAL_ELEMENT_KEY);
    PsiElement originalElement = pointer != null ?
                                 ReadAction.compute((ThrowableComputable<PsiElement, RuntimeException>)pointer::getElement) :
                                 element;
    if (originalElement != null && !(originalElement instanceof XmlElement)) {
      return null;
    }
    final DocEntity entity = ReadAction.compute(() -> findDocumentationEntity(element, tag));
    for (String url : docUrls) {
      if (url.contains(ATTR_PREFIX)) return null;
    }

    String mdnDoc = MdnDocumentationUtil.fetchExternalDocumentation(docUrls, () -> null);
    if (mdnDoc != null) {
      String name = entity != null ? entity.name : ReadAction.compute(() -> SymbolPresentationUtil.getSymbolPresentableText(element));
      Map mdnCompatData = ReadAction.compute(() -> getCompatData(entity, originalElement));
      return MdnDocumentationUtil.buildDoc(name, mdnDoc, mdnCompatData);
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
    final DocEntity entity = findDocumentationEntity(element, context);
    String mdnUrl = MdnDocumentationUtil.getMdnUrl(getCompatData(entity, originalElement));
    if (mdnUrl != null) return mdnUrl;

    EntityDescriptor descriptor = findDocumentationDescriptor(entity, context);
    if (descriptor instanceof HtmlAttributeDescriptor && context != null) {
      return "https://developer.mozilla.org/docs/Web/HTML/Element/" + context.getName() + ATTR_PREFIX + descriptor.getName();
    }

    return descriptor != null ? descriptor.getHelpRef() : null;
  }

  @NotNull
  private static DocEntity findDocumentationEntity(PsiElement element, XmlTag context) {
    boolean isTag = true;
    PsiElement nameElement = null;
    String key = null;

    if (element instanceof FakePsiElement) {
      nameElement = element.getNavigationElement();
      PsiElement parent = nameElement == null ? null : nameElement.getParent();
      isTag = parent != null && parent.getText().startsWith("element");
    } else if (element instanceof XmlElementDecl) {
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

    key = StringUtil.toLowerCase(StringUtil.notNullize(key));

    int dotIndex = key.indexOf('.');
    if (dotIndex > 0) {
      key = key.substring(0, dotIndex);
    }
    return new DocEntity(key, isTag);
  }

  private static EntityDescriptor findDocumentationDescriptor(DocEntity entity, XmlTag context) {
    if (entity.isTag) {
      return HtmlDescriptorsTable.getTagDescriptor(entity.name);
    } else {
      return getDescriptor(entity.name, context);
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

    if (result == null) {
      DocumentationProvider scriptProvider = getScriptDocumentationProvider();
      if (scriptProvider != null) {
        result = scriptProvider.generateDoc(element, originalElement);
      }
    }

    if (result == null && element instanceof XmlAttributeValue) {
      result = generateDocForHtml(element.getParent(), tag, originalElement);
    }

    return result;
  }

  protected String generateDocForHtml(PsiElement element, XmlTag context, PsiElement originalElement) {
    final DocEntity entity = findDocumentationEntity(element, context);
    final EntityDescriptor descriptor = findDocumentationDescriptor(entity, context);

    if (descriptor != null) {
      String description = descriptor.getDescription();
      if (!description.endsWith(".")) description += ".";

      Map mdnData = ReadAction.compute(() -> getCompatData(entity, originalElement));
      return MdnDocumentationUtil.buildDoc(descriptor.getName(), description, mdnData);
    }
    if (element instanceof XmlEntityDecl) {
      final XmlEntityDecl entityDecl = (XmlEntityDecl)element;

      return new XmlDocumentationProvider().findDocRightAfterElement(element, entityDecl.getName());
    }
    return null;
  }

  @Nullable
  private static Map getCompatData(DocEntity entity, @Nullable PsiElement element) {
    if (element != null && LookupManager.getInstance(element.getProject()).getActiveLookup() == null) {
      XmlAttribute attribute = ReadAction.compute(() -> PsiTreeUtil.getParentOfType(element, XmlAttribute.class, true));
      if (attribute != null) {
        XmlAttributeDescriptor descriptor = attribute.getDescriptor();
        String name = descriptor instanceof HtmlAttributeDescriptorImpl && !((HtmlAttributeDescriptorImpl)descriptor).isCaseSensitive() ?
                      StringUtil.toLowerCase(attribute.getName()) : attribute.getName();
        return HtmlCompatibilityData.getAttributeData(attribute.getParent(), name);
      }
      else if (element.getParent() instanceof XmlTag) {
        return HtmlCompatibilityData.getTagData((XmlTag)element.getParent());
      }
    }
    if (entity.isTag) {
      return HtmlCompatibilityData.getTagData("", entity.name);
    } else {
      XmlTag tag = ReadAction.compute(() -> PsiTreeUtil.getParentOfType(element, XmlTag.class, true));
      if (tag != null) {
        XmlElementDescriptor descriptor = tag.getDescriptor();
        String name = descriptor instanceof HtmlElementDescriptorImpl && !((HtmlElementDescriptorImpl)descriptor).isCaseSensitive() ?
                      StringUtil.toLowerCase(entity.name) : entity.name;
        return HtmlCompatibilityData.getAttributeData(tag, name);
      }
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
    if (result == null) {
      DocumentationProvider scriptProvider = getScriptDocumentationProvider();
      if (scriptProvider != null) {
        result = scriptProvider.getDocumentationElementForLookupItem(psiManager, object, element);
      }
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
    DocumentationProvider provider = getScriptDocumentationProvider();
    if (result == null && provider != null && !DumbService.isDumb(psiManager.getProject())) {
      result = provider.getDocumentationElementForLink(psiManager, link, context);
    }
    return result;
  }

  public PsiElement createNavigationElementHTML(PsiManager psiManager, String text, PsiElement context) {
    String key = StringUtil.toLowerCase(text);
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

  @Nullable
  private static DocumentationProvider getScriptDocumentationProvider() {
    return ContainerUtil.getFirstItem(SCRIPT_PROVIDER_EP_NAME.getExtensionList());
  }

  @Nullable
  private DocumentationProvider getStyleProvider() {
    if (!myUseStyleProvider) return null;
    Language cssLanguage = Language.findLanguageByID("CSS");
    if (cssLanguage != null) {
      return LanguageDocumentation.INSTANCE.forLanguage(cssLanguage);
    }
    return null;
  }

  private static final class DocEntity {
    String name;
    boolean isTag;

    private DocEntity(String name, boolean isTag) {
      this.name = name;
      this.isTag = isTag;
    }
  }
}
