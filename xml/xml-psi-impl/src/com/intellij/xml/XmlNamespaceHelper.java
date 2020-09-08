// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author yole
 */
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

  @Nullable
  public String getNamespacePrefix(PsiElement element) {
    if (element instanceof XmlAttribute) {
      XmlAttribute attribute = (XmlAttribute)element;
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

  public abstract void insertNamespaceDeclaration(@NotNull final XmlFile file,
                                                  @Nullable final Editor editor,
                                                  @NonNls @NotNull final Set<String> possibleNamespaces,
                                                  @NonNls @Nullable final String nsPrefix,
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

  @NotNull
  public abstract Set<String> guessUnboundNamespaces(@NotNull PsiElement element, final XmlFile file);

  @NotNull
  public abstract Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context);

  public String getNamespaceAlias(@NotNull final XmlFile file) {
    return XmlPsiBundle.message("xml.terms.namespace.alias");
  }
}
