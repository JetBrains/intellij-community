/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 30, 2002
 * Time: 8:55:08 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.xml.impl.schema;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;

public  class AnyXmlElementDescriptor implements XmlElementDescriptor {
  private final XmlElementDescriptor myParentDescriptor;
  private final XmlNSDescriptor myXmlNSDescriptor;

  public AnyXmlElementDescriptor(XmlElementDescriptor parentDescriptor, XmlNSDescriptor xmlNSDescriptor) {
    myParentDescriptor = parentDescriptor == null ? NullElementDescriptor.getInstance() : parentDescriptor;
    myXmlNSDescriptor = xmlNSDescriptor;
  }

  public XmlNSDescriptor getNSDescriptor() {
    return myXmlNSDescriptor;
  }

  public PsiElement getDeclaration(){
    return null;
  }

  public String getName(PsiElement context){
    return getName();
  }

  public String getName() {
    return myParentDescriptor.getName();
  }

  public void init(PsiElement element){
  }

  public Object[] getDependences(){
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public String getQualifiedName() {
    return myParentDescriptor.getQualifiedName();
  }

  public String getDefaultName() {
    return myParentDescriptor.getDefaultName();
  }

  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return myParentDescriptor.getElementsDescriptors(context);
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag tag, XmlTag contextTag){
    return myParentDescriptor.getElementDescriptor(tag, contextTag);
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return new XmlAttributeDescriptor[0];
  }

  public XmlAttributeDescriptor getAttributeDescriptor(final String attributeName, final XmlTag context) {
    return new AnyXmlAttributeDescriptor(attributeName);
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr){
    return myParentDescriptor.getAttributeDescriptor(attr);
  }

  public int getContentType() {
    return 0;
  }
}
