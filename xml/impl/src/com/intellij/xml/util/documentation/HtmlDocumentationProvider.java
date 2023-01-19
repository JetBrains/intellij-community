// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util.documentation;

import com.intellij.documentation.mdn.MdnSymbolDocumentation;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.documentation.mdn.MdnDocumentationKt.getHtmlMdnDocumentation;
import static com.intellij.util.ObjectUtils.doIfNotNull;

/**
 * @author maxim
 */
public class HtmlDocumentationProvider implements DocumentationProvider {
  public static final ExtensionPointName<DocumentationProvider> SCRIPT_PROVIDER_EP_NAME =
    ExtensionPointName.create("com.intellij.html.scriptDocumentationProvider");

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
  public @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof SchemaPrefix) {
      return ((SchemaPrefix)element).getQuickNavigateInfo();
    }
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    String result = getUrlForHtml(element, originalElement);
    DocumentationProvider styleProvider = getStyleProvider();
    if (result == null && styleProvider != null) {
      return styleProvider.getUrlFor(element, originalElement);
    }

    return result != null ? Collections.singletonList(result) : null;
  }

  @Override
  public @Nls String generateDoc(PsiElement element, PsiElement originalElement) {
    String result = generateDocForHtml(element, originalElement);
    if (result != null) return result;
    return generateDocFromStyleOrScript(element, originalElement);
  }

  @Override
  public PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
    if (object instanceof PsiElement) {
      MdnSymbolDocumentation documentation = getDocumentation((PsiElement)object, element);
      if (documentation != null) {
        return (PsiElement)object;
      }
    }

    PsiElement result = doIfNotNull(findDescriptor(psiManager, object.toString(), element), PsiMetaData::getDeclaration);

    DocumentationProvider styleProvider = getStyleProvider();
    if (result == null && styleProvider != null) {
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
  public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                            @NotNull PsiFile file,
                                                            @Nullable PsiElement contextElement, int targetOffset) {
    if (contextElement instanceof XmlElement) return null;
    DocumentationProvider styleProvider = getStyleProvider();
    PsiElement result = null;
    if (styleProvider != null) {
      result = styleProvider.getCustomDocumentationElement(editor, file, contextElement, targetOffset);
    }
    if (result == null) {
      DocumentationProvider scriptProvider = getScriptDocumentationProvider();
      if (scriptProvider != null) {
        result = scriptProvider.getCustomDocumentationElement(editor, file, contextElement, targetOffset);
      }
    }
    return result;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    PsiElement result = doIfNotNull(findDescriptor(psiManager, link, context), PsiMetaData::getDeclaration);

    DocumentationProvider styleProvider = getStyleProvider();
    if (result == null && styleProvider != null) {
      result = styleProvider.getDocumentationElementForLink(psiManager, link, context);
    }
    DocumentationProvider provider = getScriptDocumentationProvider();
    if (result == null && provider != null && !DumbService.isDumb(psiManager.getProject())) {
      result = provider.getDocumentationElementForLink(psiManager, link, context);
    }
    return result;
  }

  @Nls
  private String generateDocFromStyleOrScript(PsiElement element, PsiElement originalElement) {
    DocumentationProvider styleProvider = getStyleProvider();
    if (styleProvider != null) {
      String result = styleProvider.generateDoc(element, originalElement);
      if (result != null) return result;
    }

    DocumentationProvider scriptProvider = getScriptDocumentationProvider();
    if (scriptProvider != null) {
      String result = scriptProvider.generateDoc(element, originalElement);
      if (result != null) return result;
    }

    return null;
  }

  private String getUrlForHtml(PsiElement element, PsiElement originalElement) {
    return doIfNotNull(getDocumentation(element, originalElement), MdnSymbolDocumentation::getUrl);
  }

  private MdnSymbolDocumentation getDocumentation(PsiElement element, PsiElement originalElement) {
    XmlTag tagContext = findTagContext(originalElement);
    if (tagContext != null && !(tagContext instanceof HtmlTag)) return null;
    MdnSymbolDocumentation result = getHtmlMdnDocumentation(element, tagContext);
    if (result == null && tagContext == null) {
      PsiElement declaration =
        doIfNotNull(findDescriptor(element.getManager(), element.getText(), originalElement), PsiMetaData::getDeclaration);
      if (declaration != null) {
        result = getHtmlMdnDocumentation(declaration, null);
      }
    }
    return result;
  }

  private static HtmlAttributeDescriptor getDescriptor(String name, XmlTag context) {
    HtmlAttributeDescriptor attributeDescriptor = HtmlDescriptorsTable.getAttributeDescriptor(name);
    if (attributeDescriptor instanceof CompositeAttributeTagDescriptor) {
      return ((CompositeAttributeTagDescriptor)attributeDescriptor).findHtmlAttributeInContext(context);
    }

    return attributeDescriptor;
  }

  @Nls
  private String generateDocForHtml(PsiElement element, PsiElement originalElement) {
    MdnSymbolDocumentation documentation = getDocumentation(element, originalElement);
    if (documentation != null) {
      return documentation.getDocumentation(true, null);
    }

    if (element instanceof XmlEntityDecl) {
      final XmlEntityDecl entityDecl = (XmlEntityDecl)element;
      return new XmlDocumentationProvider().findDocRightAfterElement(element, entityDecl.getName());
    }
    return null;
  }

  private PsiMetaData findDescriptor(PsiManager psiManager, String text, PsiElement context) {
    if (context != null
        && (context.getNode() == null
            || context.getNode().getElementType() == XmlTokenType.XML_END_TAG_START
            || context.getParent() instanceof XmlText)) {
      return null;
    }
    String key = StringUtil.toLowerCase(text);
    final HtmlTagDescriptor descriptor = HtmlDescriptorsTable.getTagDescriptor(key);

    if (descriptor != null && !isAttributeContext(context)) {
      try {
        final XmlTag tagFromText =
          XmlElementFactory.getInstance(psiManager.getProject()).createTagFromText("<" + key + " xmlns=\"" + XmlUtil.XHTML_URI + "\"/>");
        return tagFromText.getDescriptor();
      }
      catch (IncorrectOperationException ignore) {
      }
    }
    else {
      XmlTag tagContext = findTagContext(context);
      HtmlAttributeDescriptor myAttributeDescriptor = getDescriptor(key, tagContext);

      if (myAttributeDescriptor != null && tagContext != null) {
        XmlElementDescriptor tagDescriptor = tagContext.getDescriptor();
        return tagDescriptor != null ? tagDescriptor.getAttributeDescriptor(text, tagContext) : null;
      }
    }
    return null;
  }

  protected boolean isAttributeContext(PsiElement context) {
    if (context instanceof XmlAttribute
        || (context instanceof XmlToken && ((XmlToken)context).getTokenType() == XmlTokenType.XML_TAG_END)) {
      return true;
    }

    if (context instanceof PsiWhiteSpace) {
      PsiElement prevSibling = context.getPrevSibling();
      if (prevSibling instanceof XmlAttribute) {
        return true;
      }
    }

    return false;
  }

  protected XmlTag findTagContext(PsiElement context) {
    if (context instanceof PsiWhiteSpace) {
      PsiElement prevSibling = context.getPrevSibling();
      if (prevSibling instanceof XmlTag) {
        return (XmlTag)prevSibling;
      }
    }

    return PsiTreeUtil.getParentOfType(context, XmlTag.class, false);
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
}
