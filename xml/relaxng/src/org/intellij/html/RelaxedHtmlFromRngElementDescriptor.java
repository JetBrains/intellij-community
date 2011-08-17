package org.intellij.html;

import com.intellij.html.impl.RelaxedHtmlFromSchemaElementDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.*;
import com.intellij.xml.util.HtmlUtil;

/**
 * @author Eugene.Kudelevsky
 */
public class RelaxedHtmlFromRngElementDescriptor implements XmlElementDescriptor, XmlElementDescriptorAwareAboutChildren {
  private final XmlElementDescriptor myDelegate;

  public RelaxedHtmlFromRngElementDescriptor(XmlElementDescriptor delegate) {
    myDelegate = delegate;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
    XmlElementDescriptor elementDescriptor = myDelegate.getElementDescriptor(childTag, contextTag);

    if (elementDescriptor == null) {
      return RelaxedHtmlFromSchemaElementDescriptor.getRelaxedDescriptor(this, childTag);
    }

    return elementDescriptor;
  }

  @Override
  public String getQualifiedName() {
    return myDelegate.getQualifiedName();
  }

  @Override
  public String getDefaultName() {
    return myDelegate.getDefaultName();
  }

  public XmlElementDescriptor[] getElementsDescriptors(final XmlTag context) {
    return ArrayUtil.mergeArrays(
      myDelegate.getElementsDescriptors(context),
      HtmlUtil.getCustomTagDescriptors(context),
      XmlElementDescriptor.class
    );
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return RelaxedHtmlFromSchemaElementDescriptor.addAttrDescriptorsForFacelets(context, myDelegate.getAttributesDescriptors(context));
  }

  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName(), attribute.getParent());
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return myDelegate.getNSDescriptor();
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    return myDelegate.getTopGroup();
  }

  @Override
  public int getContentType() {
    return myDelegate.getContentType();
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    final XmlAttributeDescriptor descriptor = myDelegate.getAttributeDescriptor(attributeName.toLowerCase(), context);
    if (descriptor != null) return descriptor;

    return RelaxedHtmlFromSchemaElementDescriptor.getAttributeDescriptorFromFacelets(attributeName, context);
  }

  @Override
  public PsiElement getDeclaration() {
    return myDelegate.getDeclaration();
  }

  @Override
  public String getName(PsiElement context) {
    return myDelegate.getName(context);
  }

  @Override
  public String getName() {
    return myDelegate.getName();
  }

  @Override
  public void init(PsiElement element) {
    myDelegate.init(element);
  }

  @Override
  public Object[] getDependences() {
    return myDelegate.getDependences();
  }

  @Override
  public boolean allowElementsFromNamespace(String namespace, XmlTag context) {
    return true;
  }
}
