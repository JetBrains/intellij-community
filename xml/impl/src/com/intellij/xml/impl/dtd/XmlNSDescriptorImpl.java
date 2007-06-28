package com.intellij.xml.impl.dtd;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.ExternalDocumentValidator;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Mike
 */
public class XmlNSDescriptorImpl implements XmlNSDescriptor,Validator {
  private XmlElement myElement;
  private XmlFile myDescriptorFile;

  private static final SimpleFieldCache<CachedValue<Map<String, XmlElementDescriptor>>, XmlNSDescriptorImpl> myCachedDeclsCache = new
    SimpleFieldCache<CachedValue<Map<String, XmlElementDescriptor>>, XmlNSDescriptorImpl>() {
    protected final CachedValue<Map<String, XmlElementDescriptor>> compute(final XmlNSDescriptorImpl xmlNSDescriptor) {
      return xmlNSDescriptor.doBuildDeclarationMap();
    }

    protected final CachedValue<Map<String, XmlElementDescriptor>> getValue(final XmlNSDescriptorImpl xmlNSDescriptor) {
      return xmlNSDescriptor.myCachedDecls;
    }

    protected final void putValue(final CachedValue<Map<String, XmlElementDescriptor>> cachedValue, final XmlNSDescriptorImpl xmlNSDescriptor) {
      xmlNSDescriptor.myCachedDecls = cachedValue;
    }
  };

  private volatile CachedValue<Map<String, XmlElementDescriptor>> myCachedDecls;
  private static final XmlUtil.DuplicationInfoProvider<XmlElementDecl> XML_ELEMENT_DECL_PROVIDER = new XmlUtil.DuplicationInfoProvider<XmlElementDecl>() {
    public String getName(final XmlElementDecl psiElement) {
      return psiElement.getName();
    }

    @NotNull
    public String getNameKey(final XmlElementDecl psiElement, final String name) {
      return name;
    }

    @NotNull
    public PsiElement getNodeForMessage(final XmlElementDecl psiElement) {
      return psiElement.getNameElement();
    }
  };

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

  private final Map<String,XmlElementDescriptor> buildDeclarationMap() {
    return myCachedDeclsCache.get(this).getValue();
  }

  // Read-only calculation
  private CachedValue<Map<String, XmlElementDescriptor>> doBuildDeclarationMap() {
    return myElement.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<Map<String, XmlElementDescriptor>>() {
      public Result<Map<String, XmlElementDescriptor>> compute() {
        final List<XmlElementDecl> result = new ArrayList<XmlElementDecl>();
        myElement.processElements(new FilterElementProcessor(new ClassFilter(XmlElementDecl.class), result), getDeclaration());
        final Map<String, XmlElementDescriptor> ret = new LinkedHashMap<String, XmlElementDescriptor>((int)(result.size() * 1.5));
        for (final XmlElementDecl xmlElementDecl : result) {
          final XmlElement nameElement = xmlElementDecl.getNameElement();
          if (nameElement != null) {
            String text = nameElement.getText();
            if (!ret.containsKey(text)) {
              ret.put(text, new XmlElementDescriptorImpl(xmlElementDecl));
            }
          }
        }
        return new Result<Map<String, XmlElementDescriptor>>(ret, myDescriptorFile);
       }
     }, false);
  }

  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    String name = tag.getName();
    return getElementDescriptor(name);
  }

  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument document) {
    // Suggest more appropriate variant if DOCTYPE <element_name> exists
    final XmlProlog prolog = document != null ? document.getProlog():null;

    if (prolog != null) {
      final XmlDoctype doctype = prolog.getDoctype();

      if (doctype != null) {
        final XmlElement element = doctype.getNameElement();

        if (element != null) {
          final XmlElementDescriptor descriptor = getElementDescriptor(element.getText());

          if (descriptor != null) return new XmlElementDescriptor[] {descriptor};
        }
      }
    }

    return getElements();
  }

  public final XmlElementDescriptor getElementDescriptor(String name){
    return buildDeclarationMap().get(name);
  }

  public PsiElement getDeclaration() {
    return myElement;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    final ElementClassHint classHint = processor.getHint(ElementClassHint.class);
    final NameHint nameHint = processor.getHint(NameHint.class);

    if (classHint == null || classHint.shouldProcess(XmlElementDecl.class)) {
      if (nameHint != null) {
        final XmlElementDecl element = getElement(nameHint.getName());
        if (element != null) return processor.execute(element, substitutor);
      }
      else {
        for (final XmlElementDescriptor xmlElementDescriptor : buildDeclarationMap().values()) {
          if (!processor.execute(xmlElementDescriptor.getDeclaration(), substitutor)) return false;
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
    return new Object[]{myElement, ExternalResourceManager.getInstance()};
  }

  public void validate(PsiElement context, ValidationHost host) {
    if (context instanceof XmlDocument && context.getLanguage() == StdLanguages.DTD) {
      final List<XmlElementDecl> decls = new ArrayList<XmlElementDecl>(3);

      XmlUtil.processXmlElements((XmlDocument)context, new PsiElementProcessor() {
        public boolean execute(final PsiElement element) {
          if (element instanceof XmlElementDecl) decls.add((XmlElementDecl)element);
          return true;
        }
      }, false);
      XmlUtil.doDuplicationCheckForElements(
        decls.toArray(new XmlElementDecl[decls.size()]),
        new HashMap<String, XmlElementDecl>(decls.size()),
        XML_ELEMENT_DECL_PROVIDER,
        host
      );
      return;
    }
    ExternalDocumentValidator.doValidation(context,host);
  }
}
