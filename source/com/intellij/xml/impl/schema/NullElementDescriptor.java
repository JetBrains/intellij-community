/**
 * @author cdr
 */
package com.intellij.xml.impl.schema;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;

public class NullElementDescriptor implements XmlElementDescriptor {
  private static final NullElementDescriptor INSTANCE = new NullElementDescriptor();

  public static NullElementDescriptor getInstance() {
    return INSTANCE;
  }

  private NullElementDescriptor() {
  }

  public String getQualifiedName() {
    return null;
  }

  public String getDefaultName() {
    return null;
  }

  //todo: refactor to support full DTD spec
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return new XmlElementDescriptor[0];
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag childTag) {
    return null;
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors(final XmlTag context) {
    return new XmlAttributeDescriptor[0];
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    return null;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return null;
  }

  public XmlNSDescriptor getNSDescriptor() {
    return null;
  }

  public int getContentType() {
    return 0;
  }

  public PsiElement getDeclaration() {
    return null;
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return false;
  }

  public String getName(PsiElement context) {
    return null;
  }

  public String getName() {
    return null;
  }

  public void init(PsiElement element) {
  }

  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }
}