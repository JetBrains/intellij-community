package com.intellij.jsp.impl;

import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import com.intellij.psi.xml.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jan 11, 2005
 * Time: 8:55:41 PM
 * To change this template use File | Settings | File Templates.
 */
public class TldTagDescriptor implements XmlElementDescriptor {
  private XmlTag myTag;
  private String myName;
  private XmlAttributeDescriptor[] myAttributeDescriptors;
  private TldDescriptor myNsDescriptor;

  public TldTagDescriptor() {}

  public TldTagDescriptor(XmlTag tag) {
    init(tag);
  }

  public String getQualifiedName() {
    return getName();
  }

  public String getDefaultName() {
    return getName();
  }

  //todo: refactor to support full DTD spec
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return new XmlElementDescriptor[0];
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag childTag) {
    return null;
  }

  public XmlAttributeDescriptor[] getAttributesDescriptors() {
    if (myAttributeDescriptors==null) {
      final XmlTag[] subTags = myTag.findSubTags("attribute", null);
      myAttributeDescriptors = new XmlAttributeDescriptor[subTags.length];

      for (int i = 0; i < subTags.length; i++) {
        myAttributeDescriptors[i] = new TldAttributeDescriptor(subTags[i]);
      }
    }
    return myAttributeDescriptors;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName) {
    final XmlAttributeDescriptor[] attributesDescriptors = getAttributesDescriptors();

    for (int i = 0; i < attributesDescriptors.length; i++) {
      final XmlAttributeDescriptor attributesDescriptor = attributesDescriptors[i];

      if (attributesDescriptor.getName().equals(attributeName)) {
        return attributesDescriptor;
      }
    }
    return null;
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
    return getAttributeDescriptor(attribute.getName());
  }

  public XmlNSDescriptor getNSDescriptor() {
    if (myNsDescriptor==null) {
      final PsiFile file = myTag.getContainingFile();
      if(!(file instanceof XmlFile)) return null;
      final XmlDocument document = ((XmlFile)file).getDocument();
      myNsDescriptor = (TldDescriptor) document.getMetaData();
    }

    return myNsDescriptor;
  }

  public int getContentType() {
    return 0;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public PsiElement getDeclaration() {
    return myTag;
  }

  public boolean processDeclarations(PsiElement context,
                                     PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastElement,
                                     PsiElement place) {
    return true;
  }

  public String getName(PsiElement context) {
    String value = getName();

    if(context instanceof XmlElement){
      final XmlTag tag = PsiTreeUtil.getParentOfType(context, XmlTag.class, false);

      if(tag != null){
        final String namespacePrefix = tag.getPrefixByNamespace( ((TldDescriptor)getNSDescriptor()).getUri() );
        if(namespacePrefix != null && namespacePrefix.length() > 0)
          value = namespacePrefix + ":" + XmlUtil.findLocalNameByQualifiedName(value);
      }
    }

    return value;
  }

  public String getName() {
    if (myName == null) {
      final XmlTag firstSubTag = myTag.findFirstSubTag("name");
      myName = (firstSubTag!=null)?firstSubTag.getValue().getText():null;
    }
    return myName;
  }

  public void init(PsiElement element) {
    if (myTag!=element && myTag!=null) {
      myNsDescriptor = null;
    }
    myTag = (XmlTag)element;
  }

  public Object[] getDependences() {
    return new Object[]{myTag, ExternalResourceManagerImpl.getInstance()};
  }
}
