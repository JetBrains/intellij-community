package com.intellij.psi.impl.source.html.dtd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.jsp.impl.RelaxedNsXmlElementDescriptor;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 2, 2004
 * Time: 4:19:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlElementDescriptorImpl implements XmlElementDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.html.dtd.HtmlElementDescriptorImpl");

  private XmlElementDescriptor myDelegate;
  private boolean myRelaxed;
  private HashMap<String,XmlElementDescriptor> cachedDescriptors;
  private HashMap<String,XmlAttributeDescriptor> cachedAttributeDescriptors;

  public HtmlElementDescriptorImpl(XmlElementDescriptor _delegate) {
    myDelegate = _delegate;
    myRelaxed = myDelegate instanceof RelaxedNsXmlElementDescriptor;
  }

  public HtmlElementDescriptorImpl() {
    LOG.error("Should not be called");
  }

  public String getQualifiedName() {
    return myDelegate.getQualifiedName();
  }

  public String getDefaultName() {
    return myDelegate.getDefaultName();
  }

  private XmlElementDescriptor[] myElementDescriptors = null;

  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {

    if (myElementDescriptors==null) {
      XmlElementDescriptor[] elementsDescriptors = myDelegate.getElementsDescriptors(context);
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
      XmlElementDescriptor[] elementDescriptors = myDelegate.getElementsDescriptors(element);

      for (XmlElementDescriptor elementDescriptor : elementDescriptors) {
        cachedDescriptors.put(elementDescriptor.getName(), new HtmlElementDescriptorImpl(elementDescriptor));
      }
    }

    XmlElementDescriptor xmlElementDescriptor = cachedDescriptors.get(name);
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(element);
    }

    return xmlElementDescriptor;
  }

  private XmlAttributeDescriptor[] myAttributeDescriptors;

  public XmlAttributeDescriptor[] getAttributesDescriptors() {

    if (myAttributeDescriptors==null) {
      final XmlAttributeDescriptor[] attributesDescriptors = myDelegate.getAttributesDescriptors();
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
      XmlAttributeDescriptor[] elementAttributeDescriptors = myDelegate.getAttributesDescriptors();

      for (final XmlAttributeDescriptor attributeDescriptor : elementAttributeDescriptors) {
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
