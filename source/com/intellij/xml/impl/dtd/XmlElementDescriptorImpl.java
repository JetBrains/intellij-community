package com.intellij.xml.impl.dtd;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentCachedValue;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlNSDescriptorSequence;
import com.intellij.xml.util.XmlUtil;

import java.util.*;

/**
 * @author Mike
 */
public class XmlElementDescriptorImpl implements XmlElementDescriptor, PsiWritableMetaData {
  private XmlElementDecl myElementDecl;

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
    final PsiMetaDataBase meta = tag.getMetaData();
    if (meta == null) {
      return true;
    }
    if(hint == null || hint.shouldProcess(XmlAttributeDecl.class)){
      final XmlAttlistDecl[] decls = getAttlistDecls();
      for (XmlAttlistDecl decl : decls) {
        if (decl.getNameElement() != null && decl.getNameElement().getText().equals(meta.getName())) {
          final XmlAttributeDecl[] attributes = decl.getAttributeDecls();
          for (XmlAttributeDecl attribute : attributes) {
            if (!processor.execute(attribute, substitutor)) return false;
          }
        }
      }
    }
    if(hint == null || hint.shouldProcess(XmlElementDecl.class)){
      final XmlElementDescriptor[] decls = getElementsDescriptors(tag);
      for (final XmlElementDescriptor decl : decls) {
        if (!processor.execute(decl.getDeclaration(), substitutor)) return false;
      }
    }

    return true;
  }

  public String getName(PsiElement context){
    return getName();
  }

  private final ConcurrentCachedValue<Void, String> myName = new ConcurrentCachedValue<Void, String>(new Computable<String>() {
    public String compute() {
      return myElementDecl.getNameElement().getText();
    }
  });

  public String getName() {
    return myName.getOrCache();
  }

  public void init(PsiElement element){
    myElementDecl = (XmlElementDecl) element;
  }

  public Object[] getDependences(){
    return new Object[]{myElementDecl, ExternalResourceManager.getInstance()};
  }

  public XmlNSDescriptor getNSDescriptor() {
    return getNsDescriptorFrom(myElementDecl);
  }

  private static XmlNSDescriptor getNsDescriptorFrom(final PsiElement elementDecl) {
    final PsiFile file = elementDecl.getContainingFile();
    if(!(file instanceof XmlFile)) return null;
    final XmlDocument document = ((XmlFile)file).getDocument();
    XmlNSDescriptor descriptor = (XmlNSDescriptor) document.getMetaData();
    if(descriptor == null) descriptor = document.getDefaultNSDescriptor(XmlUtil.EMPTY_URI, false);
    return descriptor;
  }

  private final ConcurrentCachedValue<XmlTag, XmlElementDescriptor[]> myElementDescriptors = new ConcurrentCachedValue<XmlTag, XmlElementDescriptor[]>(new Function<XmlTag, XmlElementDescriptor[]>() {
    public XmlElementDescriptor[] fun(final XmlTag xmlTag) {
      final List<XmlElementDescriptor> result = new ArrayList<XmlElementDescriptor>();

      final XmlElementContentSpec contentSpecElement = myElementDecl.getContentSpecElement();
      final XmlNSDescriptor nsDescriptor = getNSDescriptor();
      final XmlNSDescriptor NSDescriptor = nsDescriptor != null? nsDescriptor:getNsDescriptorFrom(xmlTag);

      contentSpecElement.processElements(new PsiElementProcessor(){
        public boolean execute(PsiElement child){
          if (child instanceof XmlToken) {
            final XmlToken token = (XmlToken)child;

            if (token.getTokenType() == XmlTokenType.XML_NAME) {
              final String text = child.getText();
              XmlElementDescriptor element = getElementDescriptor(text, NSDescriptor);

              if (element != null) {
                result.add(element);
              }
            }
            else if (token.getTokenType() == XmlTokenType.XML_CONTENT_ANY) {
              if (NSDescriptor instanceof XmlNSDescriptorImpl) {
                result.addAll(Arrays.asList(((XmlNSDescriptorImpl) NSDescriptor).getElements()));
              } else if (NSDescriptor instanceof XmlNSDescriptorSequence) {

                for (XmlNSDescriptor xmlNSDescriptor : ((XmlNSDescriptorSequence)NSDescriptor).getSequence()) {
                  if (xmlNSDescriptor instanceof XmlNSDescriptorImpl) {
                    result.addAll(Arrays.asList(((XmlNSDescriptorImpl) xmlNSDescriptor).getElements()));
                  }
                }
              }
            }
          }
          return true;
        }
      }, getDeclaration());

      return result.toArray(new XmlElementDescriptor[result.size()]);
    }
  });
  public XmlElementDescriptor[] getElementsDescriptors(XmlTag context) {
    return myElementDescriptors.getOrCache(context);
  }

  private static XmlElementDescriptor getElementDescriptor(final String text, final XmlNSDescriptor NSDescriptor) {
    XmlElementDescriptor element = null;
    if (NSDescriptor instanceof XmlNSDescriptorImpl) {
      element = ((XmlNSDescriptorImpl)NSDescriptor).getElementDescriptor(text);
    }
    else if (NSDescriptor instanceof XmlNSDescriptorSequence) {
      final List<XmlNSDescriptor> sequence = ((XmlNSDescriptorSequence)NSDescriptor).getSequence();
      for (XmlNSDescriptor xmlNSDescriptor : sequence) {
        if (xmlNSDescriptor instanceof XmlNSDescriptorImpl) {
          element = ((XmlNSDescriptorImpl)xmlNSDescriptor).getElementDescriptor(text);
          if(element != null) break;
        }
      }
    }
    else {
      element = null;
    }
    return element;
  }

  private final ConcurrentCachedValue<Void, XmlAttributeDescriptor[]> myAttributeDescriptors = new ConcurrentCachedValue<Void, XmlAttributeDescriptor[]>(new Computable<XmlAttributeDescriptor[]>() {
    public XmlAttributeDescriptor[] compute() {
      final List<XmlAttributeDescriptor> result = new ArrayList<XmlAttributeDescriptor>();
      for (XmlAttlistDecl attlistDecl : findAttlistDecls(getName())) {
        for (XmlAttributeDecl attributeDecl : attlistDecl.getAttributeDecls()) {
          result.add((XmlAttributeDescriptor)attributeDecl.getMetaData());
        }
      }
      return result.toArray(new XmlAttributeDescriptor[result.size()]);
    }
  });

  public XmlAttributeDescriptor[] getAttributesDescriptors() {
    return myAttributeDescriptors.getOrCache();
  }

  private final ConcurrentCachedValue<Void, Map<String,XmlAttributeDescriptor>> attributeDescriptorsMap = new ConcurrentCachedValue<Void, Map<String, XmlAttributeDescriptor>>(new Computable<Map<String, XmlAttributeDescriptor>>() {
    public Map<String, XmlAttributeDescriptor> compute() {
      final XmlAttributeDescriptor[] xmlAttributeDescriptors = getAttributesDescriptors();
      Map<String, XmlAttributeDescriptor> result = new HashMap<String, XmlAttributeDescriptor>(xmlAttributeDescriptors.length);
      for (final XmlAttributeDescriptor xmlAttributeDescriptor : xmlAttributeDescriptors) {
        result.put(xmlAttributeDescriptor.getName(), xmlAttributeDescriptor);
      }
      return result;
    }
  });

  public XmlAttributeDescriptor getAttributeDescriptor(String attributeName) {
    Map<String, XmlAttributeDescriptor> map = attributeDescriptorsMap.getOrCache();
    return map.get(attributeName);
  }

  public XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attr){
    final String name = attr.getName();
    
    return getAttributeDescriptor(name);
  }

  private XmlAttlistDecl[] findAttlistDecls(String elementName) {
    final List<XmlAttlistDecl> result = new ArrayList<XmlAttlistDecl>();

    final XmlAttlistDecl[] decls = getAttlistDecls();

    for (final XmlAttlistDecl decl : decls) {
      final XmlElement nameElement = decl.getNameElement();
      if (nameElement != null && nameElement.textMatches(elementName)) {
        result.add(decl);
      }
    }

    return result.toArray(new XmlAttlistDecl[result.size()]);
  }

  private static final Class<XmlElement>[] ourParentClassesToScanAttributes = new Class[] { XmlMarkupDecl.class, XmlDocument.class };

  private final ConcurrentCachedValue<Void, XmlAttlistDecl[]> myAttlistDecl = new ConcurrentCachedValue<Void, XmlAttlistDecl[]>(new Computable<XmlAttlistDecl[]>() {
    public XmlAttlistDecl[] compute() {
      final List<XmlAttlistDecl> result = new ArrayList<XmlAttlistDecl>();
      final XmlElement xmlElement = PsiTreeUtil.getParentOfType(getDeclaration(),ourParentClassesToScanAttributes);
      xmlElement.processElements(new FilterElementProcessor(new ClassFilter(XmlAttlistDecl.class), result), getDeclaration());
      return result.toArray(new XmlAttlistDecl[result.size()]);
    }
  });
  private XmlAttlistDecl[] getAttlistDecls() {
    return myAttlistDecl.getOrCache();
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

  private final ConcurrentCachedValue<XmlTag, Map<String,XmlElementDescriptor>> elementDescriptorsMap = new ConcurrentCachedValue<XmlTag, Map<String, XmlElementDescriptor>>(new Function<XmlTag, Map<String, XmlElementDescriptor>>() {
    public Map<String, XmlElementDescriptor> fun(final XmlTag xmlTag) {
      final XmlElementDescriptor[] descriptors = getElementsDescriptors(xmlTag);
      Map<String, XmlElementDescriptor> map = new HashMap<String, XmlElementDescriptor>(descriptors.length);

      for (final XmlElementDescriptor descriptor : descriptors) {
        map.put(descriptor.getName(), descriptor);
      }
      return map;
    }
  });

  public XmlElementDescriptor getElementDescriptor(XmlTag element){
    String name = element.getName();
    Map<String, XmlElementDescriptor> map = elementDescriptorsMap.getOrCache(element);
    return map.get(name);
  }

  public String getQualifiedName() {
    return getName();
  }

  public String getDefaultName() {
    return getName();
  }

  public void setName(final String name) throws IncorrectOperationException {
    myName.setValue(name);
  }
}
