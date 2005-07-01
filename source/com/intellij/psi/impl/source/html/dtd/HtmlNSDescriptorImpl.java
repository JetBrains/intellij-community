package com.intellij.psi.impl.source.html.dtd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.jsp.impl.RelaxedNsXmlNSDescriptor;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 2, 2004
 * Time: 4:18:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlNSDescriptorImpl implements XmlNSDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl");

  private XmlNSDescriptor myDelegate;
  private boolean myRelaxed;
  private Map<String, XmlElementDescriptor> myCachedDecls;

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate) {
    myDelegate = _delegate;
    myRelaxed = myDelegate instanceof RelaxedNsXmlNSDescriptor;
  }

  public HtmlNSDescriptorImpl() {
    LOG.error("Should not be called");
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {
    if (myCachedDecls == null) {
      myCachedDecls = new HashMap<String, XmlElementDescriptor>();
      XmlElementDescriptor[] elements = (myDelegate!=null)?myDelegate.getRootElementsDescriptors():XmlElementDescriptor.EMPTY_ARRAY;

      for (XmlElementDescriptor element : elements) {
        myCachedDecls.put(
          element.getName(),
          new HtmlElementDescriptorImpl(element)
        );
      };
    }
    return myCachedDecls;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag tag) {
    String name = tag.getName();
    name = name.toLowerCase();

    XmlElementDescriptor xmlElementDescriptor = buildDeclarationMap().get(name);
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(tag);
    }
    return xmlElementDescriptor;
  }

  public XmlElementDescriptor[] getRootElementsDescriptors() {
    return (myDelegate!=null)?myDelegate.getRootElementsDescriptors():XmlElementDescriptor.EMPTY_ARRAY;
  }

  public XmlFile getDescriptorFile() {
    return (myDelegate!=null)?myDelegate.getDescriptorFile():null;
  }

  public boolean isHierarhyEnabled() {
    return false;
  }

  public PsiElement getDeclaration() {
    return (myDelegate!=null)?myDelegate.getDeclaration():null;
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return (myDelegate!=null)?myDelegate.processDeclarations(context, processor, substitutor, lastElement, place):true;
  }

  public String getName(PsiElement context) {
    return (myDelegate!=null)?myDelegate.getName(context):"";
  }

  public String getName() {
    return (myDelegate!=null)?myDelegate.getName():"";
  }

  public void init(PsiElement element) {
    myDelegate.init(element);
  }

  public Object[] getDependences() {
    return (myDelegate!=null)?myDelegate.getDependences():null;
  }
}
