// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.html.ScriptSupportUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class XmlFileImpl extends PsiFileImpl implements XmlFile {

  public XmlFileImpl(FileViewProvider viewProvider, IElementType elementType) {
    super(elementType, elementType, viewProvider);
  }

  @Override
  public XmlDocument getDocument() {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof XmlDocument) return (XmlDocument)child;
      child = child.getNextSibling();
    }

    return null;
  }

  @Override
  public XmlTag getRootTag() {
    XmlDocument document = getDocument();
    return document == null ? null : document.getRootTag();
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place){
    final XmlDocument document = getDocument();
    return document == null || document.processElements(processor, place);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlFile(this);
    }
    else {
      visitor.visitFile(this);
    }
  }

  @Override
  public String toString() {
    return "XmlFile:" + getName();
  }

  private FileType myType = null;
  @Override
  public @NotNull FileType getFileType() {
    if (myType == null) {
      myType = getLanguage().getAssociatedFileType();
      if (myType == null) {
        VirtualFile virtualFile = getOriginalFile().getVirtualFile();
        myType = virtualFile == null ? FileTypeRegistry.getInstance().getFileTypeByFileName(getName()) : virtualFile.getFileType();
      }
    }
    return myType;
  }

  @Override
  public void clearCaches() {
    super.clearCaches();

    if (isWebFileType()) {
      ScriptSupportUtil.clearCaches(this);
    }
  }

  private boolean isWebFileType() {
    return getLanguage() == XHTMLLanguage.INSTANCE || getLanguage() == HTMLLanguage.INSTANCE;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return super.processDeclarations(processor, state, lastParent, place) &&
           (!isWebFileType() || ScriptSupportUtil.processDeclarations(this, processor, state, lastParent, place));

  }

  @Override
  public @NotNull GlobalSearchScope getFileResolveScope() {
    return ProjectScope.getAllScope(getProject());
  }

  @Override
  public boolean ignoreReferencedElementAccessibility() {
    return true;
  }
}
