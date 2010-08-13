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
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
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

/**
 * @author Mike
 */
public class XmlFileImpl extends PsiFileImpl implements XmlFile {

  public XmlFileImpl(FileViewProvider viewProvider, IElementType elementType) {
    super(elementType, elementType, viewProvider);
  }

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

  public boolean processElements(PsiElementProcessor processor, PsiElement place){
    final XmlDocument document = getDocument();
    return document == null || document.processElements(processor, place);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlFile(this);
    }
    else {
      visitor.visitFile(this);
    }
  }

  public String toString() {
    return "XmlFile:" + getName();
  }

  private FileType myType = null;
  @NotNull
  public FileType getFileType() {
    if (myType == null) {
      myType = getLanguage().getAssociatedFileType();
      if (myType == null) {
        VirtualFile virtualFile = getOriginalFile().getVirtualFile();
        myType = virtualFile == null ? FileTypeManager.getInstance().getFileTypeByFileName(getName()) : virtualFile.getFileType();
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

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return super.processDeclarations(processor, state, lastParent, place) &&
           (!isWebFileType() || ScriptSupportUtil.processDeclarations(this, processor, state, lastParent, place));

  }

  public GlobalSearchScope getFileResolveScope() {
    return ProjectScope.getAllScope(getProject());
  }
}
