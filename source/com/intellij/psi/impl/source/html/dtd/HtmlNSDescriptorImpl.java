package com.intellij.psi.impl.source.html.dtd;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Nov 2, 2004
 * Time: 4:18:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class HtmlNSDescriptorImpl implements XmlNSDescriptor {
  private XmlNSDescriptor delegate;
  private Map<String, XmlElementDescriptor> myCachedDecls;

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate) {
    delegate = _delegate;
  }

  public HtmlNSDescriptorImpl() {
    assert false;
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {

    if (myCachedDecls == null) {
      myCachedDecls = new HashMap<String, XmlElementDescriptor>();
      XmlElementDescriptor[] elements = (delegate!=null)?delegate.getRootElementsDescriptors():XmlElementDescriptor.EMPTY_ARRAY;

      for (int i = 0; i < elements.length; i++) {
        XmlElementDescriptor element = elements[i];

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

    return buildDeclarationMap().get(name);
  }

  public XmlElementDescriptor[] getRootElementsDescriptors() {
    return (delegate!=null)?delegate.getRootElementsDescriptors():XmlElementDescriptor.EMPTY_ARRAY;
  }

  public XmlFile getDescriptorFile() {
    return (delegate!=null)?delegate.getDescriptorFile():null;
  }

  public boolean isHierarhyEnabled() {
    return false;
  }

  public PsiElement getDeclaration() {
    return (delegate!=null)?delegate.getDeclaration():null;
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return (delegate!=null)?delegate.processDeclarations(context, processor, substitutor, lastElement, place):true;
  }

  public String getName(PsiElement context) {
    return (delegate!=null)?delegate.getName(context):"";
  }

  public String getName() {
    return (delegate!=null)?delegate.getName():"";
  }

  public void init(PsiElement element) {
    delegate.init(element);
  }

  public Object[] getDependences() {
    return (delegate!=null)?delegate.getDependences():null;
  }
}
