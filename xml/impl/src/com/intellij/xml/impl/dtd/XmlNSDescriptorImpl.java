/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.impl.dtd;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.lang.dtd.DTDLanguage;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
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
public class XmlNSDescriptorImpl implements XmlNSDescriptor,Validator<XmlDocument>, DumbAware {
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
    public String getName(@NotNull final XmlElementDecl psiElement) {
      return psiElement.getName();
    }

    @NotNull
    public String getNameKey(@NotNull final XmlElementDecl psiElement, @NotNull final String name) {
      return name;
    }

    @NotNull
    public PsiElement getNodeForMessage(@NotNull final XmlElementDecl psiElement) {
      return psiElement.getNameElement();
    }
  };

  public XmlNSDescriptorImpl() {}

  public XmlNSDescriptorImpl(XmlElement element) {
    init(element);
  }

  public XmlFile getDescriptorFile() {
    return myDescriptorFile;
  }

  public boolean isHierarhyEnabled() {
    return false;
  }

  public XmlElementDescriptor[] getElements() {
    final Collection<XmlElementDescriptor> delcarations = buildDeclarationMap().values();
    return delcarations.toArray(new XmlElementDescriptor[delcarations.size()]);
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {
    return myCachedDeclsCache.get(this).getValue();
  }

  // Read-only calculation
  private CachedValue<Map<String, XmlElementDescriptor>> doBuildDeclarationMap() {
    return CachedValuesManager.getManager(myElement.getProject()).createCachedValue(new CachedValueProvider<Map<String, XmlElementDescriptor>>() {
      public Result<Map<String, XmlElementDescriptor>> compute() {
        final List<XmlElementDecl> result = new ArrayList<XmlElementDecl>();
        myElement.processElements(new FilterElementProcessor(new ClassFilter(XmlElementDecl.class), result), getDeclaration());
        final Map<String, XmlElementDescriptor> ret = new LinkedHashMap<String, XmlElementDescriptor>((int)(result.size() * 1.5));

        for (final XmlElementDecl xmlElementDecl : result) {
          final String name = xmlElementDecl.getName();
          if (name != null) {
            if (!ret.containsKey(name)) {
              ret.put(name, new XmlElementDescriptorImpl(xmlElementDecl));
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

  public void validate(@NotNull XmlDocument document, @NotNull ValidationHost host) {
    if (document.getLanguage() == DTDLanguage.INSTANCE) {
      final List<XmlElementDecl> decls = new ArrayList<XmlElementDecl>(3);

      XmlUtil.processXmlElements(document, new PsiElementProcessor() {
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
    ExternalDocumentValidator.doValidation(document,host);
  }
}
