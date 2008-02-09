package com.intellij.xml;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlExtension {

  private static final ExtensionPointName<XmlExtension> EP_NAME = new ExtensionPointName<XmlExtension>("com.intellij.xml.xmlExtension");

  protected static final XmlExtension DEFAULT_EXTENSION = new DefaultXmlExtension();

  public static XmlExtension getExtension(XmlFile file) {
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
    if (psiFile instanceof XmlFile) {
      return getExtension((XmlFile)psiFile);
    }
    return null;
  }

  public abstract boolean isAvailable(XmlFile file);

  @NotNull
  public abstract Set<String> getAvailableTagNames(@NotNull final XmlFile file, @NotNull final XmlTag context);
  @NotNull
  public abstract Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context);

  @NotNull
  public abstract Set<String> guessUnboundNamespaces(@NotNull PsiElement element);

  public static interface Runner<P, T extends Throwable> {
    void run(P param) throws T;
  }

  public abstract void insertNamespaceDeclaration(@NotNull final XmlFile file,
                                                    @NotNull final Editor editor, 
                                                    @NotNull final Set<String> possibleNamespaces,
                                                    @Nullable final String nsPrefix,
                                                    @Nullable Runner<String, IncorrectOperationException> runAfter) throws IncorrectOperationException;

  public String getNamespaceAlias(@NotNull final XmlFile file) {
    return XmlBundle.message("namespace.alias");
  }

  @Nullable
  public IntentionAction createAddAttributeFix(@NotNull final XmlAttribute attribute) {
    return null;
  }

  @Nullable
  public IntentionAction createAddTagFix(@NotNull final XmlTag tag) {
    return null;
  }
}
