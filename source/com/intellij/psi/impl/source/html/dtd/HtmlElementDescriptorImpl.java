package com.intellij.psi.impl.source.html.dtd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiLock;
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
  private volatile HashMap<String,XmlElementDescriptor> cachedDescriptors;
  private volatile HashMap<String,XmlAttributeDescriptor> cachedAttributeDescriptors;
  private volatile XmlElementDescriptor[] myElementDescriptors = null;
  private volatile XmlAttributeDescriptor[] myAttributeDescriptors;

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

  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {

    if (myElementDescriptors==null) {
      synchronized(PsiLock.LOCK) {
        if (myElementDescriptors == null) {
          XmlElementDescriptor[] elementsDescriptors = myDelegate.getElementsDescriptors(context);
          XmlElementDescriptor[] temp = new XmlElementDescriptor[elementsDescriptors.length];

          for (int i = 0; i < elementsDescriptors.length; i++) {
            temp[i] = new HtmlElementDescriptorImpl( elementsDescriptors[i] );
          }

          myElementDescriptors = temp;
        }
      }
    }

    return myElementDescriptors;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag element) {
    String name = element.getName();
    name = name.toLowerCase();

    if (cachedDescriptors == null) {
      synchronized (PsiLock.LOCK) {
        if (cachedDescriptors == null) {
          final HashMap<String, XmlElementDescriptor> hashMap = new HashMap<String, XmlElementDescriptor>();
          final XmlElementDescriptor[] elementDescriptors = myDelegate.getElementsDescriptors(element);

          for (XmlElementDescriptor elementDescriptor : elementDescriptors) {
            hashMap.put(elementDescriptor.getName(), new HtmlElementDescriptorImpl(elementDescriptor));
          }

          cachedDescriptors = hashMap;
        }
      }
    }

    XmlElementDescriptor xmlElementDescriptor = cachedDescriptors.get(name);
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(element);
    }

    return xmlElementDescriptor;
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors() {

    if (myAttributeDescriptors == null) {
      synchronized (PsiLock.LOCK) {
        if (myAttributeDescriptors == null) {
          final XmlAttributeDescriptor[] attributesDescriptors = myDelegate.getAttributesDescriptors();
          XmlAttributeDescriptor[] temp = new XmlAttributeDescriptor[attributesDescriptors.length];

          for (int i = 0; i < attributesDescriptors.length; i++) {
            temp[i] = new HtmlAttributeDescriptorImpl(attributesDescriptors[i]);
          }

          myAttributeDescriptors = temp;
        }
      }
    }
    return myAttributeDescriptors;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName) {
    attributeName = attributeName.toLowerCase();

    if (cachedAttributeDescriptors==null) {
      synchronized(PsiLock.LOCK) {
        if (cachedAttributeDescriptors == null) {
          final HashMap<String, XmlAttributeDescriptor> hashMap = new HashMap<String, XmlAttributeDescriptor>();
          XmlAttributeDescriptor[] elementAttributeDescriptors = myDelegate.getAttributesDescriptors();

          for (final XmlAttributeDescriptor attributeDescriptor : elementAttributeDescriptors) {
            hashMap.put(
              attributeDescriptor.getName(),
              new HtmlAttributeDescriptorImpl(attributeDescriptor)
            );
          }

          cachedAttributeDescriptors = hashMap;
        }
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
