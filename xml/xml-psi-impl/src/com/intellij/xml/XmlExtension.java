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
package com.intellij.xml;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlExtension {
  public static final ExtensionPointName<XmlExtension> EP_NAME = new ExtensionPointName<>("com.intellij.xml.xmlExtension");

  public static XmlExtension getExtension(@NotNull final PsiFile file) {
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
    for (XmlExtension extension : Extensions.getExtensions(EP_NAME)) {
      if (extension.isAvailable(file)) {
        return extension;
      }
    }
    return DefaultXmlExtension.DEFAULT_EXTENSION;
  }

  @SuppressWarnings("ConstantConditions")
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

    @Nullable
    public PsiElement getDeclaration() {
      return null;
    }
  }

  @NotNull
  public abstract List<TagInfo> getAvailableTagNames(@NotNull final XmlFile file, @NotNull final XmlTag context);

  @Nullable
  public TagNameReference createTagNameReference(final ASTNode nameElement, final boolean startTagFlag) {
    return new TagNameReference(nameElement, startTagFlag);
  }

  @Nullable
  public String[][] getNamespacesFromDocument(final XmlDocument parent, boolean declarationsExist) {
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

  @Nullable
  public abstract SchemaPrefix getPrefixDeclaration(final XmlTag context, String namespacePrefix);

  public SearchScope getNsPrefixScope(XmlAttribute declaration) {
    return new LocalSearchScope(declaration.getParent());
  }

  public boolean shouldBeHighlightedAsTag(XmlTag tag) {
    return true;
  }

  @Nullable
  public XmlElementDescriptor getElementDescriptor(XmlTag tag, XmlTag contextTag, final XmlElementDescriptor parentDescriptor) {
    return parentDescriptor.getElementDescriptor(tag, contextTag);
  }

  @Nullable
  public XmlNSDescriptor getNSDescriptor(final XmlTag element, final String namespace, final boolean strict) {
    return element.getNSDescriptor(namespace, strict);  
  }

  @Nullable
  public XmlTag getParentTagForNamespace(XmlTag tag, XmlNSDescriptor namespace) {
    return tag.getParentTag();
  }

  @Nullable
  public XmlFile getContainingFile(PsiElement element) {
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

  @NotNull
  public AttributeValuePresentation getAttributeValuePresentation(@Nullable XmlAttributeDescriptor descriptor,
                                                                  @NotNull String defaultAttributeQuote) {
    return new AttributeValuePresentation() {
      @NotNull
      @Override
      public String getPrefix() {
        return defaultAttributeQuote;
      }

      @NotNull
      @Override
      public String getPostfix() {
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
}
