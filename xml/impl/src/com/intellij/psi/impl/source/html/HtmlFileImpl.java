package com.intellij.psi.impl.source.html;

import com.intellij.psi.FileViewProvider;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElementType;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 02.11.2004
 * Time: 23:42:00
 * To change this template use File | Settings | File Templates.
 */
public class HtmlFileImpl extends XmlFileImpl {
  public HtmlFileImpl(FileViewProvider provider) {
    super(provider, XmlElementType.HTML_FILE);
  }

  public String toString() {
    return "HtmlFile:"+getName();
  }

  public XmlDocument getDocument() {
    CompositeElement treeElement = calcTreeElement();
    ChameleonTransforming.transformChildren(treeElement);
    return (XmlDocument)treeElement.findChildByRoleAsPsiElement(XmlChildRole.HTML_DOCUMENT);
  }
}
