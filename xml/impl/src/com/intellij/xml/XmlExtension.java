package com.intellij.xml;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
  public abstract List<Pair<String,String>> getAvailableTagNames(@NotNull final XmlFile file, @NotNull final XmlTag context);
  @NotNull
  public abstract Set<String> getNamespacesByTagName(@NotNull final String tagName, @NotNull final XmlFile context);

  @NotNull
  public abstract Set<String> guessUnboundNamespaces(@NotNull PsiElement element, final XmlFile file);

  public TagNameReference createTagNameReference(final ASTNode nameElement, final boolean startTagFlag) {
    return new TagNameReference(nameElement, startTagFlag);
  }

  public static interface Runner<P, T extends Throwable> {
    void run(P param) throws T;
  }

  public abstract void insertNamespaceDeclaration(@NotNull final XmlFile file,
                                                    @NotNull final Editor editor, 
                                                    @NotNull final Set<String> possibleNamespaces,
                                                    @Nullable final String nsPrefix,
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

  @Nullable
  public IntentionAction createAddAttributeFix(@NotNull final XmlAttribute attribute) {
    return null;
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

  public abstract boolean isPrefixDeclared(final XmlTag context, String namespacePrefix);

  public boolean shouldBeHighlightedAsTag(XmlTag tag) {
    return true;
  }

  @Nullable
  public XmlElementDescriptor getElementDescriptor(XmlTag tag) {
    final XmlTag parentTag = tag.getParentTag();
    assert parentTag != null;
    final XmlElementDescriptor descriptor = parentTag.getDescriptor();
    assert descriptor != null;
    return descriptor.getElementDescriptor(tag);
  }

  @Nullable
  public XmlNSDescriptor getNSDescriptor(final XmlTag element, final String namespace, final boolean strict) {
    return element.getNSDescriptor(namespace, strict);  
  }

  public XmlTag getParentTagForNamespace(XmlTag tag, String namespace) {
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
}
