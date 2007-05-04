package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.XmlAttlistDecl;
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class XmlAttlistDeclImpl extends XmlElementImpl implements XmlAttlistDecl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttlistDeclImpl");

  public XmlAttlistDeclImpl() {
    super(XmlElementType.XML_ATTLIST_DECL);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XmlElementType.XML_NAME) {
      return ChildRole.XML_NAME;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(ChildRole.XML_NAME);
  }

  public XmlAttributeDecl[] getAttributeDecls() {
    final List<XmlAttributeDecl> result = new ArrayList<XmlAttributeDecl>();
    processElements(new FilterElementProcessor(new ClassFilter(XmlAttributeDecl.class), result), this);
    return result.toArray(new XmlAttributeDecl[result.size()]);
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ResolveUtil.getReferencesFromProviders(this,XmlAttlistDecl.class);
  }
}
