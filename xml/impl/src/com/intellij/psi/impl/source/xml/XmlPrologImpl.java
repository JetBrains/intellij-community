package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlProlog;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlPrologImpl extends XmlElementImpl implements XmlProlog, XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlPrologImpl");

  public XmlPrologImpl() {
    super(XML_PROLOG);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlProlog(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (child.getElementType() == XML_DOCTYPE) {
      return XmlChildRole.XML_DOCTYPE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public XmlDoctype getDoctype() {
    return (XmlDoctype)findChildByRoleAsPsiElement(XmlChildRole.XML_DOCTYPE);
  }
}
