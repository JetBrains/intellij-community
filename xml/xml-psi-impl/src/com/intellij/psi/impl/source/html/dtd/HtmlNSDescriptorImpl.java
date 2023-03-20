// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.html.dtd;

import com.intellij.html.RelaxedHtmlNSDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SimpleFieldCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.impl.schema.TypeDescriptor;
import com.intellij.xml.impl.schema.XmlNSTypeDescriptorProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class HtmlNSDescriptorImpl implements XmlNSDescriptor, DumbAware, XmlNSTypeDescriptorProvider {
  private final XmlNSDescriptor myDelegate;
  protected final boolean myRelaxed;
  protected final boolean myCaseSensitive;

  private static final SimpleFieldCache<Map<String, HtmlElementDescriptorImpl>, HtmlNSDescriptorImpl> myCachedDeclsCache =
    new SimpleFieldCache<>() {
      @Override
      protected Map<String, HtmlElementDescriptorImpl> compute(final HtmlNSDescriptorImpl htmlNSDescriptor) {
        return htmlNSDescriptor.doBuildCachedMap();
      }

      @Override
      protected Map<String, HtmlElementDescriptorImpl> getValue(final HtmlNSDescriptorImpl htmlNSDescriptor) {
        return htmlNSDescriptor.myCachedDecls;
      }

      @Override
      protected void putValue(final Map<String, HtmlElementDescriptorImpl> map, final HtmlNSDescriptorImpl htmlNSDescriptor) {
        htmlNSDescriptor.myCachedDecls = map;
      }
    };

  private volatile Map<String, HtmlElementDescriptorImpl> myCachedDecls;

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

  public static XmlAttributeDescriptor @NotNull [] getCommonAttributeDescriptors(XmlTag context) {
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

  private Map<String,HtmlElementDescriptorImpl> buildDeclarationMap() {
    return myCachedDeclsCache.get(this);
  }

  // Read-only calculation
  private HashMap<String, HtmlElementDescriptorImpl> doBuildCachedMap() {
    HashMap<String, HtmlElementDescriptorImpl> decls = new HashMap<>();
    XmlElementDescriptor[] elements = myDelegate == null ? XmlElementDescriptor.EMPTY_ARRAY : myDelegate.getRootElementsDescriptors(null);

    for (XmlElementDescriptor element : elements) {
      decls.put(
        element.getName(),
        createHtmlElementDescriptor(element)
      );
    }
    return decls;
  }

  @NotNull
  protected HtmlElementDescriptorImpl createHtmlElementDescriptor(XmlElementDescriptor element) {
    return new HtmlElementDescriptorImpl(element, myRelaxed, myCaseSensitive);
  }

  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    XmlElementDescriptor xmlElementDescriptor = getElementDescriptorByName(tag.getLocalName());
    if (xmlElementDescriptor == null && myRelaxed) {
      xmlElementDescriptor = myDelegate.getElementDescriptor(tag);
    }
    return xmlElementDescriptor;
  }

  @ApiStatus.Internal
  public XmlElementDescriptor getElementDescriptorByName(String name) {
    if (!myCaseSensitive) name = StringUtil.toLowerCase(name);

    return buildDeclarationMap().get(name);
  }

  @Override
  public XmlElementDescriptor @NotNull [] getRootElementsDescriptors(@Nullable final XmlDocument document) {
    if (myDelegate == null) return XmlElementDescriptor.EMPTY_ARRAY;
    if (document != null) return myDelegate.getRootElementsDescriptors(document);

    return buildDeclarationMap()
      .values()
      .stream()
      .map(HtmlElementDescriptorImpl::getDelegate)
      .toArray(XmlElementDescriptor[]::new);
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

  @Override
  public Object @NotNull [] getDependencies() {
    return myDelegate == null ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : myDelegate.getDependencies();
  }

  @Override
  public TypeDescriptor getTypeDescriptor(@NotNull String name, XmlTag context) {
    return myDelegate instanceof XmlNSTypeDescriptorProvider ?
           ((XmlNSTypeDescriptorProvider)myDelegate).getTypeDescriptor(name, context) : null;
  }

  @Override
  public TypeDescriptor getTypeDescriptor(XmlTag descriptorTag) {
    return myDelegate instanceof XmlNSTypeDescriptorProvider ?
           ((XmlNSTypeDescriptorProvider)myDelegate).getTypeDescriptor(descriptorTag) : null;
  }
}
