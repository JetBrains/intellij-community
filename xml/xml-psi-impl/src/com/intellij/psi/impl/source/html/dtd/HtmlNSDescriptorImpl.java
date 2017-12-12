/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.html.dtd;

import com.intellij.html.RelaxedHtmlNSDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlNSTypeDescriptorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class HtmlNSDescriptorImpl implements XmlNSDescriptor, DumbAware, XmlNSTypeDescriptorProvider {
  private final XmlNSDescriptor myDelegate;
  private final boolean myRelaxed;
  private final boolean myCaseSensitive;

  private static final SimpleFieldCache<Map<String, XmlElementDescriptor>, HtmlNSDescriptorImpl> myCachedDeclsCache = new SimpleFieldCache<Map<String, XmlElementDescriptor>, HtmlNSDescriptorImpl>() {
    @Override
    protected Map<String, XmlElementDescriptor> compute(final HtmlNSDescriptorImpl htmlNSDescriptor) {
      return htmlNSDescriptor.doBuildCachedMap();
    }

    @Override
    protected Map<String, XmlElementDescriptor> getValue(final HtmlNSDescriptorImpl htmlNSDescriptor) {
      return htmlNSDescriptor.myCachedDecls;
    }

    @Override
    protected void putValue(final Map<String, XmlElementDescriptor> map, final HtmlNSDescriptorImpl htmlNSDescriptor) {
      htmlNSDescriptor.myCachedDecls = map;
    }
  };

  private volatile Map<String, XmlElementDescriptor> myCachedDecls;

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate) {
    this(_delegate, _delegate instanceof RelaxedHtmlNSDescriptor, false);
  }

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate, boolean relaxed, boolean caseSensitive) {
    myDelegate = _delegate;
    myRelaxed = relaxed;
    myCaseSensitive = caseSensitive;
  }

  @Nullable
  public static XmlAttributeDescriptor getCommonAttributeDescriptor(@NotNull final String attributeName, @Nullable final XmlTag context) {
    final XmlElementDescriptor descriptor = guessTagForCommonAttributes(context);
    if (descriptor != null) {
      return descriptor.getAttributeDescriptor(attributeName, context);
    }
    return null;
  }

  @NotNull
  public static XmlAttributeDescriptor[] getCommonAttributeDescriptors(XmlTag context) {
    final XmlElementDescriptor descriptor = guessTagForCommonAttributes(context);
    if (descriptor != null) {
      return descriptor.getAttributesDescriptors(context);
    }
    return XmlAttributeDescriptor.EMPTY;
  }

  @Nullable
  public static XmlElementDescriptor guessTagForCommonAttributes(@Nullable final XmlTag context) {
    if (context == null) return null;
    final XmlNSDescriptor nsDescriptor = context.getNSDescriptor(context.getNamespace(), false);
    if (nsDescriptor instanceof HtmlNSDescriptorImpl) {
      XmlElementDescriptor descriptor = ((HtmlNSDescriptorImpl)nsDescriptor).getElementDescriptorByName("div");
      descriptor = descriptor == null ? ((HtmlNSDescriptorImpl)nsDescriptor).getElementDescriptorByName("span") : descriptor;
      return descriptor;
    }
    return null;
  }

  private Map<String,XmlElementDescriptor> buildDeclarationMap() {
    return myCachedDeclsCache.get(this);
  }

  // Read-only calculation
  private HashMap<String, XmlElementDescriptor> doBuildCachedMap() {
    HashMap<String, XmlElementDescriptor> decls = new HashMap<>();
    XmlElementDescriptor[] elements = myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(null);

    for (XmlElementDescriptor element : elements) {
      decls.put(
        element.getName(),
        new HtmlElementDescriptorImpl(element, myRelaxed, myCaseSensitive)
      );
    }
    return decls;
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor xmlElementDescriptor = getElementDescriptorByName(tag.getLocalName());
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(tag);
    }
    return xmlElementDescriptor;
  }

  private XmlElementDescriptor getElementDescriptorByName(String name) {
    if (!myCaseSensitive) name = name.toLowerCase();

    return buildDeclarationMap().get(name);
  }

  @Override
  @NotNull
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable final XmlDocument document) {
    return myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(document);
  }

  @Override
  @Nullable
  public XmlFile getDescriptorFile() {
    return myDelegate == null ? null : myDelegate.getDescriptorFile();
  }

  @Override
  public PsiElement getDeclaration() {
    return myDelegate == null ? null : myDelegate.getDeclaration();
  }

  @Override
  public String getName(PsiElement context) {
    return myDelegate == null ? "" : myDelegate.getName(context);
  }

  @Override
  public String getName() {
    return myDelegate == null ? "" : myDelegate.getName();
  }

  @Override
  public void init(PsiElement element) {
    myDelegate.init(element);
  }

  @NotNull
  @Override
  public Object[] getDependences() {
    return myDelegate == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : myDelegate.getDependences();
  }

  @Override
  public TypeDescriptor getTypeDescriptor(String name, XmlTag context) {
    return myDelegate instanceof XmlNSTypeDescriptorProvider ?
           ((XmlNSTypeDescriptorProvider)myDelegate).getTypeDescriptor(name, context) : null;
  }

  @Override
  public TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    return myDelegate instanceof XmlNSTypeDescriptorProvider ?
           ((XmlNSTypeDescriptorProvider)myDelegate).getTypeDescriptor(descriptorTag) : null;
  }
}
