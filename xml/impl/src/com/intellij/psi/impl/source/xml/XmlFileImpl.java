package com.intellij.psi.impl.source.xml;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.html.ScriptSupportUtil;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlFileImpl extends PsiFileImpl implements XmlFile, XmlElementType {
  public XmlFileImpl(FileViewProvider viewProvider) {
    super(getElementType(viewProvider), getElementType(viewProvider), viewProvider);
  }

  public XmlFileImpl(FileViewProvider viewProvider, IElementType elementType, IElementType contentElementType) {
    super(elementType, contentElementType, viewProvider);
  }

  private static IElementType getElementType(final FileViewProvider fileViewProvider) {
    final Language dataLanguage = fileViewProvider.getBaseLanguage();

    if (dataLanguage == StdLanguages.XML) return XML_FILE;
    if (dataLanguage == StdLanguages.XHTML) return XHTML_FILE;
    if (dataLanguage == StdLanguages.HTML) return HTML_FILE;
    if (dataLanguage == StdLanguages.DTD) return DTD_FILE;
    return XML_FILE;
  }

  public XmlDocument getDocument() {
    CompositeElement treeElement = calcTreeElement();
    ChameleonTransforming.transformChildren(treeElement);
    return (XmlDocument)treeElement.findChildByRoleAsPsiElement(XmlChildRole.XML_DOCUMENT);
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
      PsiFile originalFile = getOriginalFile();
      if (originalFile != null) {
        myType = originalFile.getFileType();
      }
      else {
        VirtualFile virtualFile = getVirtualFile();
        myType = virtualFile == null ? FileTypeManager.getInstance().getFileTypeByFileName(getName()) : virtualFile.getFileType();
      }
    }
    return myType;
  }

  public void subtreeChanged() {
    super.subtreeChanged();

    if (isWebFileType()) {
      ScriptSupportUtil.clearCaches(this);
    }
  }

  private boolean isWebFileType() {
    return getLanguage() == StdLanguages.XHTML || getLanguage() == StdLanguages.HTML;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return super.processDeclarations(processor, state, lastParent, place) &&
           (!isWebFileType() || ScriptSupportUtil.processDeclarations(this, processor, state, lastParent, place));

  }

  public GlobalSearchScope getFileResolveScope() {
    return ProjectScope.getAllScope(getProject());
  }
}
