package com.intellij.psi.impl.source.html.dtd;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiLock;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.jsp.impl.RelaxedNsXmlNSDescriptor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class HtmlNSDescriptorImpl implements XmlNSDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl");

  private XmlNSDescriptor myDelegate;
  private boolean myRelaxed;
  private volatile Map<String, XmlElementDescriptor> myCachedDecls;

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate) {
    myDelegate = _delegate;
    myRelaxed = myDelegate instanceof RelaxedNsXmlNSDescriptor;
  }

  public HtmlNSDescriptorImpl() {
    LOG.error("Should not be called");
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {
    if (myCachedDecls == null) {
      synchronized(PsiLock.LOCK) {
        if (myCachedDecls == null) {
          HashMap<String, XmlElementDescriptor> decls = new HashMap<String, XmlElementDescriptor>();
          XmlElementDescriptor[] elements = myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(null);

          for (XmlElementDescriptor element : elements) {
            decls.put(
              element.getName(),
              new HtmlElementDescriptorImpl(element)
            );
          }
          myCachedDecls = decls;
        }
      }
    }
    return myCachedDecls;
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag tag) {
    final String name = tag.getName().toLowerCase();

    XmlElementDescriptor xmlElementDescriptor = buildDeclarationMap().get(name);
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(tag);
    }
    return xmlElementDescriptor;
  }

  public XmlElementDescriptor[] getRootElementsDescriptors(final XmlDocument document) {
    return myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(document);
  }

  public XmlFile getDescriptorFile() {
    return myDelegate == null ? null : myDelegate.getDescriptorFile();
  }

  public boolean isHierarhyEnabled() {
    return false;
  }

  public PsiElement getDeclaration() {
    return myDelegate == null ? null : myDelegate.getDeclaration();
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return myDelegate == null || myDelegate.processDeclarations(context, processor, substitutor, lastElement, place);
  }

  public String getName(PsiElement context) {
    return myDelegate == null ? "" : myDelegate.getName(context);
  }

  public String getName() {
    return myDelegate == null ? "" : myDelegate.getName();
  }

  public void init(PsiElement element) {
    myDelegate.init(element);
  }

  public Object[] getDependences() {
    return myDelegate == null ? null : myDelegate.getDependences();
  }
}
