package com.intellij.psi.impl.source.html.dtd;

import com.intellij.jsp.impl.RelaxedNsXmlNSDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class HtmlNSDescriptorImpl implements XmlNSDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl");

  private XmlNSDescriptor myDelegate;
  private boolean myRelaxed;
  private static SimpleFieldCache<Map<String, XmlElementDescriptor>, HtmlNSDescriptorImpl> myCachedDeclsCache = new SimpleFieldCache<Map<String, XmlElementDescriptor>, HtmlNSDescriptorImpl>() {
    protected Map<String, XmlElementDescriptor> compute(final HtmlNSDescriptorImpl htmlNSDescriptor) {
      return htmlNSDescriptor.doBuildCachedMap();
    }

    protected Map<String, XmlElementDescriptor> getValue(final HtmlNSDescriptorImpl htmlNSDescriptor) {
      return htmlNSDescriptor.myCachedDecls;
    }

    protected void putValue(final Map<String, XmlElementDescriptor> map, final HtmlNSDescriptorImpl htmlNSDescriptor) {
      htmlNSDescriptor.myCachedDecls = map;
    }
  };

  private volatile Map<String, XmlElementDescriptor> myCachedDecls;

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate) {
    myDelegate = _delegate;
    myRelaxed = myDelegate instanceof RelaxedNsXmlNSDescriptor;
  }

  public HtmlNSDescriptorImpl() {
    LOG.error("Should not be called");
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {
    return myCachedDeclsCache.get(this);
  }

  // Read-only calculation
  private HashMap<String, XmlElementDescriptor> doBuildCachedMap() {
    HashMap<String, XmlElementDescriptor> decls = new HashMap<String, XmlElementDescriptor>();
    XmlElementDescriptor[] elements = myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(null);

    for (XmlElementDescriptor element : elements) {
      decls.put(
        element.getName(),
        new HtmlElementDescriptorImpl(element)
      );
    }
    return decls;
  }

  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    final String name = tag.getName().toLowerCase();

    XmlElementDescriptor xmlElementDescriptor = buildDeclarationMap().get(name);
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(tag);
    }
    return xmlElementDescriptor;
  }

  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument document) {
    return myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(document);
  }

  @Nullable
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
