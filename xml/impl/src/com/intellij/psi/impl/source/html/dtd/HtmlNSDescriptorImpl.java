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
package com.intellij.psi.impl.source.html.dtd;

import com.intellij.html.impl.RelaxedHtmlFromSchemaNSDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.psi.PsiElement;
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
public class HtmlNSDescriptorImpl implements XmlNSDescriptor, DumbAware {
  private XmlNSDescriptor myDelegate;
  private boolean myRelaxed;
  private boolean myCaseSensitive;

  private static final SimpleFieldCache<Map<String, XmlElementDescriptor>, HtmlNSDescriptorImpl> myCachedDeclsCache = new SimpleFieldCache<Map<String, XmlElementDescriptor>, HtmlNSDescriptorImpl>() {
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
    this(_delegate, _delegate instanceof RelaxedHtmlFromSchemaNSDescriptor, false);
  }

  public HtmlNSDescriptorImpl(XmlNSDescriptor _delegate, boolean relaxed, boolean caseSensitive) {
    myDelegate = _delegate;
    myRelaxed = relaxed;
    myCaseSensitive = caseSensitive;
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
        new HtmlElementDescriptorImpl(element, myRelaxed, myCaseSensitive)
      );
    }
    return decls;
  }

  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    String name = tag.getLocalName();
    if (!myCaseSensitive) name = name.toLowerCase();

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
