package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.impl.events.XmlAttributeSetImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class XmlAttributeImpl extends XmlElementImpl implements XmlAttribute {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.xml.XmlAttributeImpl");

  public XmlAttributeImpl() {
    super(XML_ATTRIBUTE);
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == XML_NAME) {
      return ChildRole.XML_NAME;
    }
    else if (i == XML_ATTRIBUTE_VALUE) {
      return ChildRole.XML_ATTRIBUTE_VALUE;
    }
    else {
      return ChildRole.NONE;
    }
  }

  public XmlAttributeValue getValueElement() {
    return (XmlAttributeValue)XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(this);
  }

  public void setValue(String valueText) throws IncorrectOperationException{
    final ASTNode value = XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild(this);
    final PomModel model = getProject().getModel();
    final ASTNode newValue = XmlChildRole.ATTRIBUTE_VALUE_FINDER.findChild((CompositeElement)getManager().getElementFactory().createXmlAttribute("a", valueText));
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    model.runTransaction(new PomTransactionBase(this, aspect) {
      public PomModelEvent runInner(){
        if(value != null){
          XmlAttributeImpl.this.replaceChild(value, newValue);
        }
        else {
          XmlAttributeImpl.this.addChild(newValue);
        }
        return XmlAttributeSetImpl.createXmlAttributeSet(model, getParent(), getName(), value != null ? value.getText() : null);
      }
    });
  }

  public XmlElement getNameElement() {
    return (XmlElement)XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(this);
  }

  public String getNamespace() {
    final String name = getName();
    if(name != null){
      final String prefixByQualifiedName = XmlUtil.findPrefixByQualifiedName(name);
      if(prefixByQualifiedName.length() == 0) return getParent().getNamespace();
      return getParent().getNamespaceByPrefix(prefixByQualifiedName);
    }
    return XmlUtil.EMPTY_URI;
  }

  public XmlTag getParent(){
    return (XmlTag)super.getParent();
  }

  public String getLocalName() {
    return getName() != null ? XmlUtil.findLocalNameByQualifiedName(getName()) : "";
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitXmlAttribute(this);
  }

  public String getValue() {
    final XmlAttributeValue valueElement = getValueElement();
    return valueElement != null ? valueElement.getValue() : null;
  }

  public String getName() {
    XmlElement element = getNameElement();
    return element != null ? element.getText() : "";
  }

  public boolean isNamespaceDeclaration() {
    final @NonNls String name = getName();
    return name.startsWith("xmlns:") || name.equals("xmlns");
  }

  public PsiElement setName(final String nameText) throws IncorrectOperationException {
    final ASTNode name = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(this);
    final String oldName = name.getText();
    final PomModel model = getProject().getModel();
    final ASTNode newName = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild((CompositeElement)getManager().getElementFactory().createXmlAttribute(nameText, ""));
    final XmlAspect aspect = model.getModelAspect(XmlAspect.class);
    model.runTransaction(new PomTransactionBase(getParent(), aspect) {
      public PomModelEvent runInner(){
        CodeEditUtil.replaceChild(XmlAttributeImpl.this, name, newName);
        return XmlAttributeSetImpl.createXmlAttributeSet(model, getParent(), nameText, getValue());
      }
    });
    model.runTransaction(new PomTransactionBase(getParent(), aspect) {
      public PomModelEvent runInner(){
        return XmlAttributeSetImpl.createXmlAttributeSet(model, getParent(), oldName, null);
      }
    });

    return this;
  }

  public PsiReference getReference() {
    final PsiReference[] refs = getReferences();
    if (refs != null && refs.length > 0){
      return refs[0];
    }
    return null;
  }

  public PsiReference[] getReferences() {
    final PsiElement parent = getParent();
    if (!(parent instanceof XmlTag)) return PsiReference.EMPTY_ARRAY;
    final XmlElementDescriptor descr = ((XmlTag)parent).getDescriptor();
    if (descr != null){
      return new PsiReference[]{new MyPsiReference(descr)};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  public XmlAttributeDescriptor getDescriptor() {
    final PsiElement parent = getParent();
    if (parent instanceof XmlDecl) return null;
    final XmlElementDescriptor descr = ((XmlTag)parent).getDescriptor();
    if (descr == null) return null;
    final XmlAttributeDescriptor attributeDescr = descr.getAttributeDescriptor(this);
    return attributeDescr == null ? descr.getAttributeDescriptor(getName()) : attributeDescr;
  }

  private class MyPsiReference implements PsiReference {
    private final XmlElementDescriptor myDescr;

    public MyPsiReference(XmlElementDescriptor descr) {
      myDescr = descr;
    }

    public PsiElement getElement() {
      return XmlAttributeImpl.this;
    }

    public TextRange getRangeInElement() {
      final int parentOffset = getNameElement().getStartOffsetInParent();
      return new TextRange(parentOffset, parentOffset + getNameElement().getTextLength());
    }

    public PsiElement resolve() {
      final XmlAttributeDescriptor descriptor = myDescr.getAttributeDescriptor(XmlAttributeImpl.this.getName());
      if (descriptor != null)
        return descriptor.getDeclaration();
      return null;
    }

    public String getCanonicalText() {
      return XmlAttributeImpl.this.getName();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return setName(newElementName);
    }

    // TODO[ik]: namespace support
    public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiMetaOwner){
        final PsiMetaOwner owner = (PsiMetaOwner)element;
        if (owner.getMetaData() instanceof XmlElementDescriptor){
          setName(owner.getMetaData().getName());
        }
      }
      throw new IncorrectOperationException("Cant bind to not a xml element definition!");
    }

    public boolean isReferenceTo(PsiElement element) {
      return element.getManager().areElementsEquivalent(element, resolve());
    }

    public Object[] getVariants() {
      final List<String> variants = new ArrayList<String>();

      final XmlElementDescriptor parentDescriptor = getParent().getDescriptor();
      if (parentDescriptor != null){
        final XmlTag declarationTag = getParent();
        final XmlAttribute[] attributes = declarationTag.getAttributes();
        final XmlAttributeDescriptor[] descriptors = parentDescriptor.getAttributesDescriptors();
        outer:
        for (XmlAttributeDescriptor descriptor1 : descriptors) {
          for (final XmlAttribute attribute : attributes) {
            if (attribute == XmlAttributeImpl.this) continue;
            final String name = attribute.getName();
            if (name != null && name.equals(descriptor1.getName())) continue outer;
          }
          final XmlAttributeDescriptor descriptor = descriptor1;
          variants.add(descriptor.getName());
        }
      }
      return variants.toArray();
    }

    public boolean isSoft() {
      return false;
    }
  }

}
