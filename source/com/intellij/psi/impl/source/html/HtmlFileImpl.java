package com.intellij.psi.impl.source.html;

import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileTypes.FileType;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 02.11.2004
 * Time: 23:42:00
 * To change this template use File | Settings | File Templates.
 */
public class HtmlFileImpl extends XmlFileImpl {
  public HtmlFileImpl(PsiManagerImpl manager, VirtualFile file) {
    super(manager, file, XmlElementType.HTML_FILE, XmlElementType.HTML_FILE_TEXT);
  }

  public HtmlFileImpl(PsiManagerImpl manager, String name, char[] text, int startOffset, int endOffset, FileType fileType) {
    super(manager, name, text, startOffset, endOffset, fileType, XmlElementType.HTML_FILE);
  }

  public String toString() {
    return "HtmlFile:"+getName();
  }

  public XmlDocument getDocument() {
    CompositeElement treeElement = calcTreeElement();
    ChameleonTransforming.transformChildren(treeElement);
    return (XmlDocument)treeElement.findChildByRoleAsPsiElement(ChildRole.HTML_DOCUMENT);
  }
}
