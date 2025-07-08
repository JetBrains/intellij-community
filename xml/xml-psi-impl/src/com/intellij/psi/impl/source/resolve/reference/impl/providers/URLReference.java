// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.XmlResolveReferenceSupport;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.xml.psi.XmlPsiBundle;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
*/
public class URLReference implements PsiReference, EmptyResolveMessageProvider {
  public static final @NonNls String TARGET_NAMESPACE_ATTR_NAME = "targetNamespace";

  private final PsiElement myElement;
  private final TextRange myRange;
  private final boolean mySoft;
  private boolean myIncorrectResourceMapped;

  public URLReference(PsiElement element) {
    this(element, null, false);
  }

  public URLReference(PsiElement element, @Nullable TextRange range, boolean soft) {
    myElement = element;
    myRange = range;
    mySoft = soft;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return myRange != null ? myRange : ElementManipulators.getValueTextRange(myElement);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return ApplicationManager.getApplication().getService(XmlResolveReferenceSupport.class).resolveReference(this);
  }

  @Override
  public @NotNull String getCanonicalText() {
    final String text = myElement.getText();
    if (text.length() > 1) {
      return myRange == null ? text.substring(1, text.length() - 1) : myRange.substring(text);
    }

    return "";
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    final TextRange textRangeInElement = getRangeInElement();
    final PsiElement elementToChange = myElement.findElementAt(textRangeInElement.getStartOffset());
    assert elementToChange != null;
    final int offsetFromStart = myElement.getTextRange().getStartOffset() + textRangeInElement.getStartOffset() - elementToChange.getTextOffset();

    ElementManipulators.handleContentChange(elementToChange, new TextRange(offsetFromStart, offsetFromStart + textRangeInElement.getLength()),newElementName);
    return myElement;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    assert element instanceof PsiFile;

    if (!XmlUtil.isUrlText(getCanonicalText(), element.getProject())) {
      // TODO: this should work!
      final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
      assert virtualFile != null;
      handleElementRename(VfsUtilCore.fixIDEAUrl(virtualFile.getPresentableUrl()));
    }
    return myElement;
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myElement.getManager().areElementsEquivalent(resolve(),element);
  }

  public boolean isSchemaLocation() { return false; }

  @Override
  public boolean isSoft() {
    return mySoft;
  }

  @Override
  public @NotNull String getUnresolvedMessagePattern() {
    return XmlPsiBundle.message(myIncorrectResourceMapped ? "xml.inspections.registered.resource.is.not.recognized"
                                                          : "xml.inspections.uri.is.not.registered");
  }

  public static void processWsdlSchemas(final XmlTag rootTag, Processor<? super XmlTag> processor) {
    if ("definitions".equals(rootTag.getLocalName())) {
      final String nsPrefix = rootTag.getNamespacePrefix();
      final String types = nsPrefix.isEmpty() ? "types" : nsPrefix  + ":types";
      final XmlTag subTag = rootTag.findFirstSubTag(types);

      if (subTag != null) {
        for (int i = 0; i < XmlUtil.SCHEMA_URIS.length; i++) {
          final XmlTag[] tags = subTag.findSubTags("schema", XmlUtil.SCHEMA_URIS[i]);
          for (XmlTag t : tags) {
            if (!processor.process(t)) return;
          }
        }
      }
    }
  }

  @ApiStatus.Internal
  public void setIncorrectResourceMapped(boolean incorrectResourceMapped) {
    myIncorrectResourceMapped = incorrectResourceMapped;
  }
}
