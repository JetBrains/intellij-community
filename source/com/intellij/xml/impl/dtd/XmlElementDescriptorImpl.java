package com.intellij.xml.impl.dtd;

import com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiBaseElementProcessor;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author Mike
 */
public class XmlElementDescriptorImpl implements XmlElementDescriptor {
  private XmlElementDecl myElementDecl;
  private XmlAttlistDecl[] myAttlistDecl;

  public XmlElementDescriptorImpl(XmlElementDecl elementDecl) {
    myElementDecl = elementDecl;
  }

  public XmlElementDescriptorImpl() {}

  public PsiElement getDeclaration(){
    return myElementDecl;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    final ElementClassHint hint = processor.getHint(ElementClassHint.class);
    final XmlTag tag = (XmlTag)context;
    final PsiMetaData meta = tag.getMetaData();
    if(meta == null)
      return true;
    if(hint == null || hint.shouldProcess(XmlAttributeDecl.class)){
      final XmlAttlistDecl[] decls = getAttlistDecls();
      for(int i = 0; i < decls.length; i++){
        if(decls[i].getNameElement() != null && decls[i].getNameElement().getText().equals(meta.getName())){
          final XmlAttributeDecl[] attributes = decls[i].getAttributeDecls();
          for(int j = 0; j < attributes.length; j++){
            if(!processor.execute(attributes[j], substitutor)) return false;
          }
        }
      }
    }
    if(hint == null || hint.shouldProcess(XmlElementDecl.class)){
      final XmlElementDescriptor[] decls = getElementsDescriptors(tag);
      for(int i = 0; i < decls.length; i++){
        final XmlElementDescriptor decl = decls[i];
        if(!processor.execute(decl.getDeclaration(), substitutor)) return false;
      }
    }

    return true;
  }

  public String getName(PsiElement context){
    return getName();
  }

  private String myName;

  public String getName() {
    if (myName!=null) return myName;
    return myName = myElementDecl.getNameElement().getText();
  }

  public void init(PsiElement element){
    myElementDecl = (XmlElementDecl) element;
  }

  public Object[] getDependences(){
    return new Object[]{myElementDecl, ExternalResourceManagerImpl.getInstance()};
  }

  public XmlNSDescriptor getNSDescriptor() {
    final PsiFile file = myElementDecl.getContainingFile();
    if(!(file instanceof XmlFile)) return null;
    final XmlDocument document = ((XmlFile)file).getDocument();
    XmlNSDescriptor descriptor = (XmlNSDescriptor) document.getMetaData();
    if(descriptor == null){
      final XmlDoctype doctype = document.getProlog().getDoctype();
      if(doctype != null){
        if(doctype.getMarkupDecl() != null){
          descriptor = (XmlNSDescriptor)doctype.getMarkupDecl().getMetaData();
        }
      }
    }
    return descriptor;
  }

  private XmlElementDescriptor[] myElementDescriptors = null;
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    if(myElementDescriptors != null) return myElementDescriptors;
    final List<XmlElementDescriptor> result = new ArrayList<XmlElementDescriptor>();

    final XmlElementContentSpec contentSpecElement = myElementDecl.getContentSpecElement();
    final XmlNSDescriptor NSDescriptor = getNSDescriptor();
    if(!(NSDescriptor instanceof XmlNSDescriptorImpl)) return XmlElementDescriptor.EMPTY_ARRAY;
    contentSpecElement.processElements(new PsiBaseElementProcessor(){
      public boolean execute(PsiElement child){
        if (child instanceof XmlToken) {
          final XmlToken token = (XmlToken)child;

          if (token.getTokenType() == XmlTokenType.XML_NAME) {
            final String text = child.getText();
            final XmlElementDescriptor element = ((XmlNSDescriptorImpl)NSDescriptor).getElementDescriptor(text);

            if (element != null) {
              result.add(element);
            }
          }
          else if (token.getTokenType() == XmlToken.XML_CONTENT_ANY) {
            XmlElementDescriptor[] elements = ((XmlNSDescriptorImpl) NSDescriptor).getElements();
            result.addAll(Arrays.asList(elements));
          }
        }
        return true;
      }
    }, getDeclaration());

    return myElementDescriptors = result.toArray(new XmlElementDescriptor[result.size()]);
  }

  private XmlAttributeDescriptor[] myAttributeDescriptors;

  public XmlAttributeDescriptor[] getAttributesDescriptors() {
    if (myAttributeDescriptors!=null) return myAttributeDescriptors;

    final List<PsiMetaData> result = new ArrayList<PsiMetaData>();
    final XmlAttlistDecl[] attlistDecls = findAttlistDecls(getName());

    for (int i = 0; i < attlistDecls.length; i++) {
      final XmlAttributeDecl[] attributeDecls = attlistDecls[i].getAttributeDecls();

      for (int j = 0; j < attributeDecls.length; j++) {
        result.add(attributeDecls[j].getMetaData());
      }
    }

    myAttributeDescriptors = result.toArray(new XmlAttributeDescriptor[result.size()]);
    return myAttributeDescriptors;
  }

  private HashMap<String,XmlAttributeDescriptor> attributeDescriptorsMap;

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName) {
    if (attributeDescriptorsMap==null) {
      final XmlAttributeDescriptor[] xmlAttributeDescriptors = getAttributesDescriptors();
      attributeDescriptorsMap = new HashMap<String, XmlAttributeDescriptor>(xmlAttributeDescriptors.length);

      for (int i = 0; i < xmlAttributeDescriptors.length; i++) {
        final XmlAttributeDescriptor xmlAttributeDescriptor = xmlAttributeDescriptors[i];

        attributeDescriptorsMap.put(xmlAttributeDescriptor.getName(),xmlAttributeDescriptor);
      }
    }

    return attributeDescriptorsMap.get(attributeName);
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr){
    String name = attr.getName();
    //if (attr.getContainingFile().getFileType()==StdFileTypes.HTML) {
    //  name = name.toLowerCase();
    //}
    return getAttributeDescriptor(name);
  }

  private XmlAttlistDecl[] findAttlistDecls(String elementName) {
    final List<XmlAttlistDecl> result = new ArrayList<XmlAttlistDecl>();

    final XmlAttlistDecl[] decls = getAttlistDecls();

    for (int i = 0; i < decls.length; i++) {
      final XmlAttlistDecl decl = decls[i];
      final XmlElement nameElement = decl.getNameElement();
      if (nameElement != null && nameElement.textMatches(elementName)) {
        result.add(decl);
      }
    }

    return result.toArray(new XmlAttlistDecl[result.size()]);
  }

  private XmlAttlistDecl[] getAttlistDecls() {
    if (myAttlistDecl != null) return myAttlistDecl;

    final List result = new ArrayList();
    ((XmlElement)getDeclaration().getParent()).processElements(new FilterElementProcessor(new ClassFilter(XmlAttlistDecl.class), result), getDeclaration());
    myAttlistDecl = (XmlAttlistDecl[])result.toArray(new XmlAttlistDecl[result.size()]);
    return myAttlistDecl;
  }

  public int getContentType() {
    if (myElementDecl.getContentSpecElement().isAny()) {
      return CONTENT_TYPE_ANY;
    }
    if (myElementDecl.getContentSpecElement().hasChildren()) {
      return CONTENT_TYPE_CHILDREN;
    }
    if (myElementDecl.getContentSpecElement().isEmpty()) {
      return CONTENT_TYPE_EMPTY;
    }
    if (myElementDecl.getContentSpecElement().isMixed()) {
      return CONTENT_TYPE_MIXED;
    }

    return CONTENT_TYPE_ANY;
  }

  private HashMap<String,XmlElementDescriptor> elementDescriptorsMap;

  public XmlElementDescriptor getElementDescriptor(XmlTag element){
    String name = element.getName();
    if(name == null) return null;
    //if (element.getContainingFile().getFileType()==StdFileTypes.HTML) {
    //  name = name.toLowerCase();
    //}

    if (elementDescriptorsMap==null) {
      final XmlElementDescriptor[] descriptors = getElementsDescriptors(element);
      elementDescriptorsMap = new HashMap<String, XmlElementDescriptor>(descriptors.length);

      for (int i = 0; i < descriptors.length; i++) {
        final XmlElementDescriptor descriptor = descriptors[i];
        elementDescriptorsMap.put(descriptor.getName(),descriptor);
      }
    }

    return elementDescriptorsMap.get(name);
  }

  public String getQualifiedName() {
    return getName();
  }

  public String getDefaultName() {
    return getName();
  }
}
