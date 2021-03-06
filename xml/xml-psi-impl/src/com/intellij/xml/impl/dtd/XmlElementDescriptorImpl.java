// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.dtd;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.psi.scope.processor.FilterElementProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlNSDescriptorSequence;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class XmlElementDescriptorImpl extends BaseXmlElementDescriptorImpl implements PsiWritableMetaData {
  protected XmlElementDecl myElementDecl;
  private String myName;

  private static final Class[] ourParentClassesToScanAttributes = new Class[] { XmlMarkupDecl.class, XmlDocument.class };
  private static final Key<CachedValue<XmlAttlistDecl[]>> ourCachedAttlistKeys = Key.create("cached_declarations");

  public XmlElementDescriptorImpl(XmlElementDecl elementDecl) {
    init(elementDecl);
  }

  public XmlElementDescriptorImpl() {
  }

  private static final UserDataCache<CachedValue<XmlAttlistDecl[]>,XmlElement, Object> myAttlistDeclCache = new UserDataCache<>() {
    @Override
    protected final CachedValue<XmlAttlistDecl[]> compute(final XmlElement owner, Object o) {
      return CachedValuesManager.getManager(owner.getProject()).createCachedValue(() -> {
        XmlAttlistDecl[] decls = doCollectAttlistDeclarations(owner);
        return new CachedValueProvider.Result<>(decls, (Object[])ArrayUtil.append(decls, owner, XmlElement.class));
      });
    }
  };

  @Override
  public PsiElement getDeclaration(){
    return myElementDecl;
  }

  @Override
  public String getName(PsiElement context){
    return getName();
  }

  @Override
  public String getName() {
    if (myName!=null) return myName;
    return myName = myElementDecl.getName();
  }

  @Override
  public void init(PsiElement element){
    myElementDecl = (XmlElementDecl) element;
  }

  @Override
  public Object @NotNull [] getDependencies(){
    return new Object[]{myElementDecl, ExternalResourceManager.getInstance()};
  }

  @Override
  public XmlNSDescriptor getNSDescriptor() {
    return getNsDescriptorFrom(myElementDecl);
  }

  @Nullable
  private static XmlNSDescriptor getNsDescriptorFrom(final PsiElement elementDecl) {
    final XmlFile file = XmlUtil.getContainingFile(elementDecl);
    if (file == null) {
      return null;
    }
    final XmlDocument document = file.getDocument();
    assert document != null;
    XmlNSDescriptor descriptor = (XmlNSDescriptor)document.getMetaData();
    return descriptor == null ? document.getDefaultNSDescriptor(XmlUtil.EMPTY_URI, false) : descriptor;
  }

  // Read-only action
  @Override
  protected final XmlElementDescriptor[] doCollectXmlDescriptors(final XmlTag context) {
    final LinkedHashSet<XmlElementDescriptor> result = new LinkedHashSet<>();
    final XmlElementContentSpec contentSpecElement = myElementDecl.getContentSpecElement();
    final XmlNSDescriptor nsDescriptor = getNSDescriptor();
    final XmlNSDescriptor NSDescriptor = nsDescriptor != null? nsDescriptor:getNsDescriptorFrom(context);

    XmlUtil.processXmlElements(contentSpecElement, new PsiElementProcessor(){
      @Override
      public boolean execute(@NotNull PsiElement child){
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
              ContainerUtil.addAll(result, ((XmlNSDescriptorImpl)NSDescriptor).getElements());
            } else if (NSDescriptor instanceof XmlNSDescriptorSequence) {

              for (XmlNSDescriptor xmlNSDescriptor : ((XmlNSDescriptorSequence)NSDescriptor).getSequence()) {
                if (xmlNSDescriptor instanceof XmlNSDescriptorImpl) {
                  ContainerUtil.addAll(result, ((XmlNSDescriptorImpl)xmlNSDescriptor).getElements());
                }
              }
            }
          }
        }
        return true;
      }
    }, true, false, XmlUtil.getContainingFile(getDeclaration()));

    return result.toArray(XmlElementDescriptor.EMPTY_ARRAY);
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

  // Read-only calculation
  @Override
  protected final XmlAttributeDescriptor[] collectAttributeDescriptors(final XmlTag context) {
    final List<XmlAttributeDescriptor> result = new SmartList<>();
    for (XmlAttlistDecl attlistDecl : findAttlistDeclarations(getName())) {
      for (XmlAttributeDecl attributeDecl : attlistDecl.getAttributeDecls()) {
        final PsiMetaData psiMetaData = attributeDecl.getMetaData();
        assert psiMetaData instanceof XmlAttributeDescriptor;
        result.add((XmlAttributeDescriptor)psiMetaData);
      }
    }
    return result.toArray(XmlAttributeDescriptor.EMPTY);
  }

  // Read-only calculation
  @Override
  protected HashMap<String, XmlAttributeDescriptor> collectAttributeDescriptorsMap(final XmlTag context) {
    final HashMap<String, XmlAttributeDescriptor> localADM;
    final XmlAttributeDescriptor[] xmlAttributeDescriptors = getAttributesDescriptors(context);
    localADM = new HashMap<>(xmlAttributeDescriptors.length);

    for (final XmlAttributeDescriptor xmlAttributeDescriptor : xmlAttributeDescriptors) {
      localADM.put(xmlAttributeDescriptor.getName(), xmlAttributeDescriptor);
    }
    return localADM;
  }

  private XmlAttlistDecl[] findAttlistDeclarations(String elementName) {
    final List<XmlAttlistDecl> result = new ArrayList<>();
    for (final XmlAttlistDecl declaration : getAttlistDeclarations()) {
      final String name = declaration.getName();
      if (name != null && name.equals(elementName)) {
        result.add(declaration);
      }
    }
    return result.toArray(XmlAttlistDecl.EMPTY_ARRAY);
  }

  private XmlAttlistDecl[] getAttlistDeclarations() {
    return getCachedAttributeDeclarations((XmlElement)getDeclaration());
  }

  public static XmlAttlistDecl @NotNull [] getCachedAttributeDeclarations(@Nullable XmlElement owner) {
    if (owner == null) return XmlAttlistDecl.EMPTY_ARRAY;
    owner = (XmlElement)PsiTreeUtil.getParentOfType(owner, ourParentClassesToScanAttributes);
    if (owner == null) return XmlAttlistDecl.EMPTY_ARRAY;
    return myAttlistDeclCache.get(ourCachedAttlistKeys, owner, null).getValue();
  }

  private static XmlAttlistDecl[] doCollectAttlistDeclarations(XmlElement xmlElement) {
    final List<XmlAttlistDecl> result = new ArrayList<>();
    XmlUtil.processXmlElements(xmlElement, new FilterElementProcessor(new ClassFilter(XmlAttlistDecl.class), result), false, false, XmlUtil.getContainingFile(xmlElement));
    return result.toArray(XmlAttlistDecl.EMPTY_ARRAY);
  }

  @Override
  public XmlElementsGroup getTopGroup() {
    XmlElementContentGroup topGroup = myElementDecl.getContentSpecElement().getTopGroup();
    return topGroup == null ? null : new XmlElementsGroupImpl(topGroup, null);
  }

  @Override
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

  // Read-only calculation
  @Override
  protected HashMap<String, XmlElementDescriptor> collectElementDescriptorsMap(final XmlTag element) {
    final HashMap<String, XmlElementDescriptor> elementDescriptorsMap;
    final XmlElementDescriptor[] descriptors = getElementsDescriptors(element);
    elementDescriptorsMap = new HashMap<>(descriptors.length);

    for (final XmlElementDescriptor descriptor : descriptors) {
      elementDescriptorsMap.put(descriptor.getName(), descriptor);
    }
    return elementDescriptorsMap;
  }

  @Override
  public String getQualifiedName() {
    return getName();
  }

  @Override
  public String getDefaultName() {
    return getName();
  }

  @Override
  public void setName(final String name) throws IncorrectOperationException {
    // IDEADEV-11439
    myName = null;
  }
}