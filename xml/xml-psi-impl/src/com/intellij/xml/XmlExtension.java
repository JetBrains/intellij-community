// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.highlighter.XHtmlFileType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.impl.XmlNsDescriptorUtil;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlExtension {
  public static final ExtensionPointName<XmlExtension> EP_NAME = new ExtensionPointName<>("com.intellij.xml.xmlExtension");

  public static XmlExtension getExtension(final @NotNull PsiFile file) {
    return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(calcExtension(file), PsiModificationTracker.MODIFICATION_COUNT));
  }

  public interface AttributeValuePresentation {
    @NotNull
    String getPrefix();

    @NotNull
    String getPostfix();

    default boolean showAutoPopup() {
      return true;
    }
  }

  private static XmlExtension calcExtension(PsiFile file) {
    for (XmlExtension extension : EP_NAME.getExtensionList()) {
      if (extension.isAvailable(file)) {
        return extension;
      }
    }
    return DefaultXmlExtension.DEFAULT_EXTENSION;
  }

  public static XmlExtension getExtensionByElement(PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile != null) {
      return getExtension(psiFile);
    }
    return null;
  }

  public abstract boolean isAvailable(PsiFile file);

  public static class TagInfo {

    public final String name;
    public final String namespace;

    public TagInfo(String name, String namespace) {
      this.name = name;
      this.namespace = namespace;
    }

    public @Nullable PsiElement getDeclaration() {
      return null;
    }
  }

  public abstract @NotNull List<TagInfo> getAvailableTagNames(final @NotNull XmlFile file, final @NotNull XmlTag context);

  public @Nullable TagNameReference createTagNameReference(final ASTNode nameElement, final boolean startTagFlag) {
    return new TagNameReference(nameElement, startTagFlag);
  }

  public String[] @Nullable [] getNamespacesFromDocument(final XmlDocument parent, boolean declarationsExist) {
    return declarationsExist ? null : XmlUtil.getDefaultNamespaces(parent);
  }

  public boolean canBeDuplicated(XmlAttribute attribute) {
    return false;
  }

  public boolean isRequiredAttributeImplicitlyPresent(XmlTag tag, String attrName) {
    return false;
  }

  public HighlightInfoType getHighlightInfoType(XmlFile file) {
    return HighlightInfoType.ERROR;
  }

  public abstract @Nullable SchemaPrefix getPrefixDeclaration(final XmlTag context, String namespacePrefix);

  public SearchScope getNsPrefixScope(XmlAttribute declaration) {
    return new LocalSearchScope(declaration.getParent());
  }

  public boolean shouldBeHighlightedAsTag(XmlTag tag) {
    return true;
  }

  public @Nullable XmlElementDescriptor getElementDescriptor(XmlTag tag, XmlTag contextTag, final XmlElementDescriptor parentDescriptor) {
    return parentDescriptor.getElementDescriptor(tag, contextTag);
  }

  public @Nullable XmlNSDescriptor getNSDescriptor(final XmlTag element, final String namespace, final boolean strict) {
    return element.getNSDescriptor(namespace, strict);
  }

  public @NotNull XmlNSDescriptor wrapNSDescriptor(@NotNull XmlTag element, @NotNull String namespacePrefix, @NotNull XmlNSDescriptor descriptor) {
    if (element instanceof HtmlTag && !(descriptor instanceof HtmlNSDescriptorImpl)) {
      XmlFile obj = descriptor.getDescriptorFile();
      XmlNSDescriptor result = obj == null ? null : XmlNsDescriptorUtil.getCachedHtmlNsDescriptor(obj, namespacePrefix);
      return result == null ? new HtmlNSDescriptorImpl(descriptor) : result;
    }
    return descriptor;
  }

  public @Nullable XmlTag getParentTagForNamespace(XmlTag tag, XmlNSDescriptor namespace) {
    return tag.getParentTag();
  }

  public @Nullable XmlFile getContainingFile(PsiElement element) {
    if (element == null) {
      return null;
    }
    final PsiFile psiFile = element.getContainingFile();
    return psiFile instanceof XmlFile ? (XmlFile)psiFile : null;
  }

  public XmlNSDescriptor getDescriptorFromDoctype(final XmlFile containingFile, XmlNSDescriptor descr) {
    return descr;
  }

  public boolean hasDynamicComponents(final PsiElement element) {
    return false;
  }

  public boolean isIndirectSyntax(final XmlAttributeDescriptor descriptor) {
    return false;
  }

  public boolean shouldBeInserted(final XmlAttributeDescriptor descriptor) {
    return descriptor.isRequired();
  }

  public boolean shouldCompleteTag(XmlTag context) {
    return true;
  }

  public @NotNull AttributeValuePresentation getAttributeValuePresentation(@Nullable XmlTag tag,
                                                                           @NotNull String attributeName,
                                                                           @NotNull String defaultAttributeQuote) {
    return new AttributeValuePresentation() {
      @Override
      public @NotNull String getPrefix() {
        return defaultAttributeQuote;
      }

      @Override
      public @NotNull String getPostfix() {
        return defaultAttributeQuote;
      }
    };
  }

  public boolean isCustomTagAllowed(final XmlTag tag) {
    return false;
  }

  public boolean useXmlTagInsertHandler() {
    return true;
  }

  public boolean isCollapsibleTag(XmlTag tag) {
    return false;
  }

  public boolean isSelfClosingTagAllowed(@NotNull XmlTag tag) {
    return false;
  }

  public boolean isValidTagNameChar(final char c) {
    return false;
  }

  /**
   * @return list of files containing char entity definitions to be used for completion and resolution within a specified XML file
   */
  public @NotNull List<@NotNull XmlFile> getCharEntitiesDTDs(@NotNull XmlFile file) {
    XmlDocument document = file.getDocument();
    if (HtmlUtil.isHtml5Document(document)) {
      return ContainerUtil.packNullables(XmlUtil.findXmlFile(file, Html5SchemaProvider.getCharsDtdLocation()));
    }
    else if (document != null) {
      final XmlTag rootTag = document.getRootTag();
      if (rootTag != null) {
        final XmlElementDescriptor descriptor = rootTag.getDescriptor();

        if (descriptor != null && !(descriptor instanceof AnyXmlElementDescriptor)) {
          PsiElement element = descriptor.getDeclaration();
          final PsiFile containingFile = element != null ? element.getContainingFile() : null;
          if (containingFile instanceof XmlFile) {
            return Collections.singletonList((XmlFile)containingFile);
          }
        }
      }
      final FileType ft = file.getFileType();
      final String namespace = ft == XHtmlFileType.INSTANCE || ft == StdFileTypes.JSPX ? XmlUtil.XHTML_URI : XmlUtil.HTML_URI;
      final XmlNSDescriptor nsDescriptor = document.getDefaultNSDescriptor(namespace, true);
      if (nsDescriptor != null) {
        return ContainerUtil.packNullables(nsDescriptor.getDescriptorFile());
      }
    }
    return Collections.emptyList();
  }

  public static boolean shouldIgnoreSelfClosingTag(@NotNull XmlTag tag) {
    final XmlExtension extension = getExtensionByElement(tag);
    return extension != null && extension.isSelfClosingTagAllowed(tag);
  }

  public static boolean isCollapsible(XmlTag tag) {
    final XmlExtension extension = getExtensionByElement(tag);
    return extension == null || extension.isCollapsibleTag(tag);
  }
}
