package com.intellij.psi.impl.source.html.dtd;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 2, 2004
 * Time: 4:19:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlElementDescriptorImpl implements XmlElementDescriptor {
  private XmlElementDescriptor delegate;
  private HashMap<String,XmlElementDescriptor> cachedDescriptors;
  private HashMap<String,XmlAttributeDescriptor> cachedAttributeDescriptors;

  public HtmlElementDescriptorImpl(XmlElementDescriptor _delegate) {
    delegate = _delegate;
  }

  public HtmlElementDescriptorImpl() {
    assert false;
  }

  public String getQualifiedName() {
    return delegate.getQualifiedName();
  }

  public String getDefaultName() {
    return delegate.getDefaultName();
  }

  private XmlElementDescriptor[] myElementDescriptors = null;

  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {

    if (myElementDescriptors==null) {
      XmlElementDescriptor[] elementsDescriptors = delegate.getElementsDescriptors(context);
      myElementDescriptors = new XmlElementDescriptor[elementsDescriptors.length];

      for (int i = 0; i < elementsDescriptors.length; i++) {
        myElementDescriptors[i] = new HtmlElementDescriptorImpl( elementsDescriptors[i] );
      }
    }

    return myElementDescriptors;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag element) {
    String name = element.getName();
    name = name.toLowerCase();

    if (cachedDescriptors==null) {
      cachedDescriptors = new HashMap<String, XmlElementDescriptor>();
      XmlElementDescriptor[] elementDescriptors = delegate.getElementsDescriptors(null);

      for (int i = 0; i < elementDescriptors.length; i++) {
        XmlElementDescriptor elementDescriptor = elementDescriptors[i];
        cachedDescriptors.put(elementDescriptor.getName(),new HtmlElementDescriptorImpl(elementDescriptor));
      }
    }

    return cachedDescriptors.get(name);
  }

  private XmlAttributeDescriptor[] myAttributeDescriptors;

  public XmlAttributeDescriptor[] getAttributesDescriptors() {

    if (myAttributeDescriptors==null) {
      final XmlAttributeDescriptor[] attributesDescriptors = delegate.getAttributesDescriptors();
      myAttributeDescriptors = new XmlAttributeDescriptor[attributesDescriptors.length];

      for (int i = 0; i < attributesDescriptors.length; i++) {
        myAttributeDescriptors[i] = new HtmlAttributeDescriptorImpl( attributesDescriptors[i] );
      }
    }
    return myAttributeDescriptors;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName) {
    attributeName = attributeName.toLowerCase();

    if (cachedAttributeDescriptors==null) {
      cachedAttributeDescriptors = new HashMap<String, XmlAttributeDescriptor>();
      XmlAttributeDescriptor[] elementAttributeDescriptors = delegate.getAttributesDescriptors();

      for (int i = 0; i < elementAttributeDescriptors.length; i++) {
        final XmlAttributeDescriptor attributeDescriptor = elementAttributeDescriptors[i];

        cachedAttributeDescriptors.put(
          attributeDescriptor.getName(),
          new HtmlAttributeDescriptorImpl(attributeDescriptor)
        );
      }
    }
    return cachedAttributeDescriptors.get(attributeName);
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr) {
    return getAttributeDescriptor(attr.getName());
  }

  public XmlNSDescriptor getNSDescriptor() {
    return delegate.getNSDescriptor();
  }

  public int getContentType() {
    return delegate.getContentType();
  }

  public PsiElement getDeclaration() {
    return delegate.getDeclaration();
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return delegate.processDeclarations(context, processor, substitutor, lastElement, place);
  }

  public String getName(PsiElement context) {
    return delegate.getName(context);
  }

  public String getName() {
    return delegate.getName();
  }

  public void init(PsiElement element) {
    delegate.init(element);
  }

  public Object[] getDependences() {
    return delegate.getDependences();
  }
}
