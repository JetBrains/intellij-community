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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlExtension {

  private static final ExtensionPointName<XmlExtension> EP_NAME = new ExtensionPointName<XmlExtension>("com.intellij.xml.xmlExtension");

  public static final XmlExtension DEFAULT_EXTENSION = new DefaultXmlExtension();

  public static XmlExtension getExtension(PsiFile file) {
    for (XmlExtension extension : Extensions.getExtensions(EP_NAME)) {
      if (extension.isAvailable(file)) {
        return extension;
      }
    }
    return DEFAULT_EXTENSION;
  }

  @Nullable
  public static XmlExtension getExtensionByElement(PsiElement element) {
    final PsiFile psiFile = element.getContainingFile();
    if (psiFile != null) {
      return getExtension(psiFile);
    }
    return null;
  }

  public abstract boolean isAvailable(PsiFile file);

  @NotNull
  public abstract List<Pair<String,String>> getAvailableTagNames(@NotNull final XmlFile file, @NotNull final XmlTag context);
  @NotNull
  public abstract Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context);

  @NotNull
  public abstract Set<String> guessUnboundNamespaces(@NotNull PsiElement element, final XmlFile file);

  public TagNameReference createTagNameReference(final ASTNode nameElement, final boolean startTagFlag) {
    return new TagNameReference(nameElement, startTagFlag);
  }

  @Nullable
  public String[][] getNamespacesFromDocument(final XmlDocument parent, boolean declarationsExist) {
    return declarationsExist ? null : XmlUtil.getDefaultNamespaces(parent);
  }

  public interface Runner<P, T extends Throwable> {
    void run(P param) throws T;
  }

  public abstract void insertNamespaceDeclaration(@NotNull final XmlFile file,
                                                    @NotNull final Editor editor,
                                                    @NonNls @NotNull final Set<String> possibleNamespaces,
                                                    @NonNls @Nullable final String nsPrefix,
                                                    @Nullable Runner<String, IncorrectOperationException> runAfter) throws IncorrectOperationException;

  @Nullable
  public String getNamespacePrefix(PsiElement element) {
    final PsiElement tag = element instanceof XmlTag ? element : element.getParent();
    if (tag instanceof XmlTag) {
      return ((XmlTag)tag).getNamespacePrefix();
    } else {
      return null;
    }
  }

  public boolean qualifyWithPrefix(final String namespacePrefix, final PsiElement element, final Document document) throws
                                                                                                                 IncorrectOperationException {
    final PsiElement tag = element instanceof XmlTag ? element : element.getParent();
    if (tag instanceof XmlTag) {
      final String prefix = ((XmlTag)tag).getNamespacePrefix();
      if (!prefix.equals(namespacePrefix)) {
        final String name = namespacePrefix + ":" + ((XmlTag)tag).getLocalName();
        ((XmlTag)tag).setName(name);
      }
      return true;
    }
    return false;
  }

  public String getNamespaceAlias(@NotNull final XmlFile file) {
    return XmlBundle.message("namespace.alias");
  }

  public void createAddAttributeFix(@NotNull final XmlAttribute attribute, final HighlightInfo highlightInfo) {
    final XmlTag tag = attribute.getParent();
    String namespace = attribute.getNamespace();

    if(StringUtil.isEmptyOrSpaces(namespace)) namespace = tag.getNamespace();

    final XmlNSDescriptor nsDescriptor = tag.getNSDescriptor(namespace, true);
    if (nsDescriptor instanceof XmlUndefinedElementFixProvider) {
      final IntentionAction[] actions = ((XmlUndefinedElementFixProvider)nsDescriptor).createFixes(attribute);
      for (IntentionAction action : actions) {
        QuickFixAction.registerQuickFixAction(highlightInfo, action);
      }
    }
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
  public abstract XmlAttribute getPrefixDeclaration(final XmlTag context, String namespacePrefix);

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

  public boolean isCustomTagAllowed(final XmlTag tag) {
    return false;
  }
}
