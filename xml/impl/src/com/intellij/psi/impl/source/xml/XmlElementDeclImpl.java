package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mike
 */
public class XmlElementDeclImpl extends XmlElementImpl implements XmlElementDecl, XmlElementType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlElementDeclImpl");

  public XmlElementDeclImpl() {
    super(XML_ELEMENT_DECL);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_NAME) {
      return ChildRole.XML_NAME;
    }
    else if (i == XML_ELEMENT_CONTENT_SPEC) {
      return ChildRole.XML_ELEMENT_CONTENT_SPEC;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public int getTextOffset() {
    final XmlElement name = getNameElement();
    return name != null ? name.getTextOffset() : super.getTextOffset();
  }

  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(ChildRole.XML_NAME);
  }

  public XmlElementContentSpec getContentSpecElement() {
    return (XmlElementContentSpec)findChildByRoleAsPsiElement(ChildRole.XML_ELEMENT_CONTENT_SPEC);
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return true;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    XmlElementChangeUtil.doNameReplacement(this, getNameElement(), name);

    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ResolveUtil.getReferencesFromProviders(this,XmlElementDecl.class);
  }
  
  public PsiElement getOriginalElement() {
    if (isPhysical()) return super.getOriginalElement();

    final PsiNamedElement element = XmlUtil.findRealNamedElement(this);

    if (element != null) {
      return element;
    }

    return this;
  }

  public boolean canNavigate() {
    if (!isPhysical()) {
      return getOriginalElement() != this;
    }

    return super.canNavigate();
  }

  public void navigate(boolean requestFocus) {
    if (!isPhysical()) {
      PsiElement element = getOriginalElement();

      if (element != this) {
        ((Navigatable)element).navigate(requestFocus);
        return;
      }
    }

    super.navigate(requestFocus);
  }

  public String getName() {
    XmlElement xmlElement = getNameElement();
    if (xmlElement != null) return xmlElement.getText();

    return getNameFromEntityRef(this, XmlElementType.XML_ELEMENT_DECL_START);
  }
}
