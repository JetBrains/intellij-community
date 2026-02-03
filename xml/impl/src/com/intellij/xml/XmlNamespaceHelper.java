// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.psi.XmlPsiBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


public abstract class XmlNamespaceHelper {
  private static final ExtensionPointName<XmlNamespaceHelper> EP_NAME = new ExtensionPointName<>("com.intellij.xml.namespaceHelper");

  public static final XmlNamespaceHelper DEFAULT_EXTENSION = new DefaultXmlNamespaceHelper();

  public static XmlNamespaceHelper getHelper(PsiFile file) {
    for (XmlNamespaceHelper extension : EP_NAME.getExtensionList()) {
      if (extension.isAvailable(file)) {
        return extension;
      }
    }
    return DEFAULT_EXTENSION;
  }

  protected abstract boolean isAvailable(PsiFile file);

  public interface Runner<P, T extends Throwable> {
    void run(P param) throws T;
  }

  public @Nullable String getNamespacePrefix(PsiElement element) {
    if (element instanceof XmlAttribute attribute) {
      String prefix = attribute.getNamespacePrefix();
      if (!StringUtil.isEmpty(prefix)) {
        return prefix;
      }
    }
    final PsiElement tag = element instanceof XmlTag ? element : element.getParent();
    if (tag instanceof XmlTag) {
      return ((XmlTag)tag).getNamespacePrefix();
    } else {
      return null;
    }
  }

  public abstract void insertNamespaceDeclaration(final @NotNull XmlFile file,
                                                  final @Nullable Editor editor,
                                                  final @NonNls @NotNull Set<String> possibleNamespaces,
                                                  final @NonNls @Nullable String nsPrefix,
                                                  @Nullable Runner<String, IncorrectOperationException> runAfter) throws IncorrectOperationException;

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

  public abstract @NotNull Set<String> guessUnboundNamespaces(@NotNull PsiElement element, final XmlFile file);

  public abstract @NotNull Set<String> getNamespacesByTagName(final @NotNull String tagName, final @NotNull XmlFile context);

  public String getNamespaceAlias(final @NotNull XmlFile file) {
    return XmlPsiBundle.message("xml.terms.namespace.alias");
  }
}
