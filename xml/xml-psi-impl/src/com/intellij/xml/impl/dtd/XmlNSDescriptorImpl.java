// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.dtd;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptorEx;
import com.intellij.xml.impl.ExternalDocumentValidator;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class XmlNSDescriptorImpl implements XmlNSDescriptorEx,Validator<XmlDocument>, DumbAware {
  private XmlElement myElement;
  private XmlFile myDescriptorFile;

  private static final SimpleFieldCache<CachedValue<Map<String, XmlElementDescriptor>>, XmlNSDescriptorImpl> myCachedDeclsCache = new
    SimpleFieldCache<>() {
      @Override
      protected final CachedValue<Map<String, XmlElementDescriptor>> compute(final XmlNSDescriptorImpl xmlNSDescriptor) {
        return xmlNSDescriptor.doBuildDeclarationMap();
      }

      @Override
      protected final CachedValue<Map<String, XmlElementDescriptor>> getValue(final XmlNSDescriptorImpl xmlNSDescriptor) {
        return xmlNSDescriptor.myCachedDecls;
      }

      @Override
      protected final void putValue(final CachedValue<Map<String, XmlElementDescriptor>> cachedValue,
                                    final XmlNSDescriptorImpl xmlNSDescriptor) {
        xmlNSDescriptor.myCachedDecls = cachedValue;
      }
    };

  private volatile CachedValue<Map<String, XmlElementDescriptor>> myCachedDecls;
  private static final XmlUtil.DuplicationInfoProvider<XmlElementDecl> XML_ELEMENT_DECL_PROVIDER = new XmlUtil.DuplicationInfoProvider<>() {
    @Override
    public String getName(@NotNull final XmlElementDecl psiElement) {
      return psiElement.getName();
    }

    @Override
    @NotNull
    public String getNameKey(@NotNull final XmlElementDecl psiElement, @NotNull final String name) {
      return name;
    }

    @Override
    @NotNull
    public PsiElement getNodeForMessage(@NotNull final XmlElementDecl psiElement) {
      return psiElement.getNameElement();
    }
  };

  public XmlNSDescriptorImpl() {}

  @Override
  public XmlFile getDescriptorFile() {
    return myDescriptorFile;
  }

  public XmlElementDescriptor[] getElements() {
    final Collection<XmlElementDescriptor> declarations = buildDeclarationMap().values();
    return declarations.toArray(XmlElementDescriptor.EMPTY_ARRAY);
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {
    return myCachedDeclsCache.get(this).getValue();
  }

  // Read-only calculation
  private CachedValue<Map<String, XmlElementDescriptor>> doBuildDeclarationMap() {
    return CachedValuesManager.getManager(myElement.getProject()).createCachedValue(() -> {
      final List<XmlElementDecl> result = new ArrayList<>();
      myElement.processElements(new FilterElementProcessor(new ClassFilter(XmlElementDecl.class), result), getDeclaration());
      final Map<String, XmlElementDescriptor> ret = new LinkedHashMap<>((int)(result.size() * 1.5));
      Set<PsiFile> dependencies = new HashSet<>(1);
      dependencies.add(myDescriptorFile);

      for (final XmlElementDecl xmlElementDecl : result) {
        final String name = xmlElementDecl.getName();
        if (name != null) {
          if (!ret.containsKey(name)) {
            ret.put(name, new XmlElementDescriptorImpl(xmlElementDecl));
            // if element descriptor was produced from entity reference use proper dependency
            PsiElement dependingElement = xmlElementDecl.getUserData(XmlElement.DEPENDING_ELEMENT);
            if (dependingElement != null) {
              PsiFile dependingElementContainingFile = dependingElement.getContainingFile();
              if (dependingElementContainingFile != null) dependencies.add(dependingElementContainingFile);
            }
          }
        }
      }
      return new CachedValueProvider.Result<>(ret, dependencies.toArray());
     }, false);
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    String name = tag.getName();
    return getElementDescriptor(name);
  }

  @Override
  public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(@Nullable final XmlDocument document) {
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

  @Override
  public PsiElement getDeclaration() {
    return myElement;
  }

  @Override
  public String getName(PsiElement context){
    return getName();
  }

  @Override
  public String getName(){
    return myDescriptorFile.getName();
  }

  @Override
  public void init(PsiElement element){
    myElement = (XmlElement)element;
    myDescriptorFile = (XmlFile)element.getContainingFile();

    if (myElement instanceof XmlFile) {
      myElement = ((XmlFile)myElement).getDocument();
    }
  }

  @Override
  public Object @NotNull [] getDependencies(){
    return new Object[]{myElement, ExternalResourceManager.getInstance()};
  }

  @Override
  public void validate(@NotNull XmlDocument document, @NotNull ValidationHost host) {
    if (document.getLanguage() == DTDLanguage.INSTANCE) {
      final List<XmlElementDecl> decls = new ArrayList<>(3);

      XmlUtil.processXmlElements(document, new PsiElementProcessor() {
        @Override
        public boolean execute(@NotNull final PsiElement element) {
          if (element instanceof XmlElementDecl) decls.add((XmlElementDecl)element);
          return true;
        }
      }, false);
      XmlUtil.doDuplicationCheckForElements(
        decls.toArray(new XmlElementDecl[0]),
        new HashMap<>(decls.size()),
        XML_ELEMENT_DECL_PROVIDER,
        host
      );
      return;
    }
    ExternalDocumentValidator.doValidation(document,host);
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(String localName, String namespace) {
    return getElementDescriptor(localName);
  }
}
