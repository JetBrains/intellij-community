package com.intellij.psi.impl.source.html.dtd;

import com.intellij.jsp.impl.RelaxedNsXmlElementDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.dtd.BaseXmlElementDescriptorImpl;

import java.util.HashMap;

/**
 * @by Maxim.Mossienko
 */
public class HtmlElementDescriptorImpl extends BaseXmlElementDescriptorImpl {
  private XmlElementDescriptor myDelegate;
  private boolean myRelaxed;

  public HtmlElementDescriptorImpl(XmlElementDescriptor _delegate) {
    myDelegate = _delegate;
    myRelaxed = myDelegate instanceof RelaxedNsXmlElementDescriptor;
  }

  public String getQualifiedName() {
    return myDelegate.getQualifiedName();
  }

  public String getDefaultName() {
    return myDelegate.getDefaultName();
  }

  // Read-only calculation
  protected final XmlElementDescriptor[] doCollectXmlDescriptors(final XmlTag context) {
    XmlElementDescriptor[] elementsDescriptors = myDelegate.getElementsDescriptors(context);
    XmlElementDescriptor[] temp = new XmlElementDescriptor[elementsDescriptors.length];

    for (int i = 0; i < elementsDescriptors.length; i++) {
      temp[i] = new HtmlElementDescriptorImpl( elementsDescriptors[i] );
    }
    return temp;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag element) {
    String name = element.getName();
    name = name.toLowerCase();

    XmlElementDescriptor xmlElementDescriptor = getElementDescriptor(name, element);
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(element);
    }

    return xmlElementDescriptor;
  }

  // Read-only calculation
  protected HashMap<String, XmlElementDescriptor> collectElementDescriptorsMap(final XmlTag element) {
    final HashMap<String, XmlElementDescriptor> hashMap = new HashMap<String, XmlElementDescriptor>();
    final XmlElementDescriptor[] elementDescriptors = myDelegate.getElementsDescriptors(element);

    for (XmlElementDescriptor elementDescriptor : elementDescriptors) {
      hashMap.put(elementDescriptor.getName(), new HtmlElementDescriptorImpl(elementDescriptor));
    }
    return hashMap;
  }

  // Read-only calculation
  protected XmlAttributeDescriptor[] collectAttributeDescriptors(final XmlTag context) {
    final XmlAttributeDescriptor[] attributesDescriptors = myDelegate.getAttributesDescriptors(context);
    XmlAttributeDescriptor[] temp = new XmlAttributeDescriptor[attributesDescriptors.length];

    for (int i = 0; i < attributesDescriptors.length; i++) {
      temp[i] = new HtmlAttributeDescriptorImpl(attributesDescriptors[i]);
    }
    return temp;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName, final XmlTag context) {
    return super.getAttributeDescriptor(attributeName.toLowerCase(), context);
  }

  // Read-only calculation
  protected HashMap<String, XmlAttributeDescriptor> collectAttributeDescriptorsMap(final XmlTag context) {
    final HashMap<String, XmlAttributeDescriptor> hashMap = new HashMap<String, XmlAttributeDescriptor>();
    XmlAttributeDescriptor[] elementAttributeDescriptors = myDelegate.getAttributesDescriptors(context);

    for (final XmlAttributeDescriptor attributeDescriptor : elementAttributeDescriptors) {
      hashMap.put(
        attributeDescriptor.getName(),
        new HtmlAttributeDescriptorImpl(attributeDescriptor)
      );
    }
    return hashMap;
  }

  public XmlNSDescriptor getNSDescriptor() {
    return myDelegate.getNSDescriptor();
  }

  public int getContentType() {
    return myDelegate.getContentType();
  }

  public PsiElement getDeclaration() {
    return myDelegate.getDeclaration();
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return myDelegate.processDeclarations(context, processor, substitutor, lastElement, place);
  }

  public String getName(PsiElement context) {
    return myDelegate.getName(context);
  }

  public String getName() {
    return myDelegate.getName();
  }

  public void init(PsiElement element) {
    myDelegate.init(element);
  }

  public Object[] getDependences() {
    return myDelegate.getDependences();
  }
}
