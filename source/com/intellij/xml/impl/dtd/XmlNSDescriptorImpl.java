package com.intellij.xml.impl.dtd;

import com.intellij.j2ee.openapi.impl.ExternalResourceManagerImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlElementDescriptor;

import java.util.*;

/**
 * @author Mike
 */
public class XmlNSDescriptorImpl implements XmlNSDescriptor {
  private XmlElement myElement;
  private XmlFile myDescriptorFile;
  private CachedValue<Map<String, XmlElementDescriptor>> myCachedDecls;

  public XmlNSDescriptorImpl() {
  }

  public XmlNSDescriptorImpl(XmlElement element) {
    init(element);
  }

  public XmlFile getDescriptorFile() {
    return myDescriptorFile;
  }

  public boolean isHierarhyEnabled() {
    return false;
  }


  private XmlElementDecl getElement(String elementName) {
    return (XmlElementDecl)buildDeclarationMap().get(elementName).getDeclaration();
  }


  public XmlElementDescriptor[] getElements() {
    return buildDeclarationMap().values().toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {
    if (myCachedDecls == null) {
      myCachedDecls = myElement.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<Map<String, XmlElementDescriptor>>() {
        public Result<Map<String, XmlElementDescriptor>> compute() {
          final List<XmlElementDecl> result = new ArrayList<XmlElementDecl>();
          myElement.processElements(new FilterElementProcessor(new ClassFilter(XmlElementDecl.class), result), getDeclaration());
          final Map<String, XmlElementDescriptor> ret = new LinkedHashMap<String, XmlElementDescriptor>((int)(result.size() * 1.5));
          final Iterator<XmlElementDecl> iterator = result.iterator();
          while(iterator.hasNext()){
            final XmlElementDecl xmlElementDecl = iterator.next();
            final XmlElement nameElement = xmlElementDecl.getNameElement();
            if(nameElement != null) {
              String text = nameElement.getText();
              if (!ret.containsKey(text)) {
                ret.put(text, new XmlElementDescriptorImpl(xmlElementDecl));
              }
            }
          }
          return new Result<Map<String, XmlElementDescriptor>>(ret, new Object[]{myDescriptorFile});
         }
       }, false);
    }
    return myCachedDecls.getValue();
  }

  public XmlElementDescriptor getElementDescriptor(XmlTag tag) {
    String name = tag.getName();
    return getElementDescriptor(name);
  }

  public XmlElementDescriptor[] getRootElementsDescriptors() {
    return getElements();
  }

  final XmlElementDescriptor getElementDescriptor(String name){
    return buildDeclarationMap().get(name);
  }

  public PsiElement getDeclaration() {
    return myElement;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    final ElementClassHint classHint = (ElementClassHint) processor.getHint(ElementClassHint.class);
    final NameHint nameHint =  (NameHint) processor.getHint(NameHint.class);

    if(classHint == null || classHint.shouldProcess(XmlElementDecl.class)){
      if(nameHint != null){
        final XmlElementDecl element = getElement(nameHint.getName());
        if(element != null)
          return processor.execute(element, substitutor);
      }
      else{
        final Iterator<XmlElementDescriptor> iterator = buildDeclarationMap().values().iterator();
        while (iterator.hasNext()) {
          if(!processor.execute(iterator.next().getDeclaration(), substitutor)) return false;
        }
      }
    }
    return true;
  }

  public String getName(PsiElement context){
    return getName();
  }

  public String getName(){
    return myDescriptorFile.getName();
  }

  public void init(PsiElement element){
    myElement = (XmlElement)element;
    myDescriptorFile = (XmlFile)element.getContainingFile();

    if (myElement instanceof XmlFile) {
      myElement = ((XmlFile)myElement).getDocument();
    }
  }

  public Object[] getDependences(){
    return new Object[]{myElement, ExternalResourceManagerImpl.getInstance()};
  }
}
