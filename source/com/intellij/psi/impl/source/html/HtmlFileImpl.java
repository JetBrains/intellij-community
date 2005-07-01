package com.intellij.psi.impl.source.html;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.xml.util.XmlUtil;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 02.11.2004
 * Time: 23:42:00
 * To change this template use File | Settings | File Templates.
 */
public class HtmlFileImpl extends XmlFileImpl {
  public void subtreeChanged() {
    super.subtreeChanged();
    ScriptSupportUtil.clearCaches(this);
  }

  public HtmlFileImpl(Project project, VirtualFile file) {
    super(project, file, XmlElementType.HTML_FILE, XmlElementType.HTML_FILE);
  }

  public HtmlFileImpl(Project project, String name, CharSequence text, FileType fileType) {
    super(project, name, text, fileType, XmlElementType.HTML_FILE);
  }

  public String toString() {
    return "HtmlFile:"+getName();
  }

  public XmlDocument getDocument() {
    CompositeElement treeElement = calcTreeElement();
    ChameleonTransforming.transformChildren(treeElement);
    return (XmlDocument)treeElement.findChildByRoleAsPsiElement(ChildRole.HTML_DOCUMENT);
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    if (!super.processDeclarations(processor, substitutor, lastParent, place)) return false;
    
    return ScriptSupportUtil.processDeclarations(this, processor, substitutor, lastParent, place);
  }
}
