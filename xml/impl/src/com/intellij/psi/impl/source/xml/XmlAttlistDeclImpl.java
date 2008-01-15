package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.xml.*;
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
      return XmlChildRole.XML_NAME;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public XmlElement getNameElement() {
    return (XmlElement)findChildByRoleAsPsiElement(XmlChildRole.XML_NAME);
  }

  public XmlAttributeDecl[] getAttributeDecls() {
    final List<XmlAttributeDecl> result = new ArrayList<XmlAttributeDecl>();
    processElements(new FilterElementProcessor(new ClassFilter(XmlAttributeDecl.class), result) {
      public boolean execute(final PsiElement element) {
        if (element instanceof XmlAttributeDecl) {
          if (element.getNextSibling() == null && element.getChildren().length == 1) {
            return true;
          }
          return super.execute(element);
        }
        return true;
      }
    }, this);
    return result.toArray(new XmlAttributeDecl[result.size()]);
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this,XmlAttlistDecl.class);
  }

  public String getName() {
    XmlElement xmlElement = getNameElement();
    if (xmlElement != null) return xmlElement.getText();

    return getNameFromEntityRef(this, XmlElementType.XML_ATTLIST_DECL_START);
  }
}
