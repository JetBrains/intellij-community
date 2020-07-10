// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.reflect;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.SmartList;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomAnchor;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.ConvertAnnotationImpl;
import com.intellij.util.xml.impl.DomAnchorImpl;
import com.intellij.util.xml.impl.DomChildDescriptionImpl;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author peter
 */
public class DomExtensionImpl implements DomExtension {
  public static final Key<Supplier<? extends DomElement>> KEY_DOM_DECLARATION = Key.create("DOM_DECLARATION");
  public static final Key<List<DomExtender<?>>> DOM_EXTENDER_KEY = Key.create("Dom.Extender");
  public static final Key<SmartPsiElementPointer<?>> DECLARING_ELEMENT_KEY = Key.create("Dom.Extension.PsiDeclaration");
  private final XmlName myXmlName;
  private final Type myType;
  private Converter<?> myConverter;
  private final List<Annotation> myCustomAnnotations = new SmartList<>();
  private boolean mySoft;
  private int myCount = 1;
  private Map<Key<?>, Object> myUserMap;
  private CustomDomChildrenDescription.TagNameDescriptor myTagNameDescriptor = CustomDomChildrenDescription.AttributeDescriptor.EMPTY;
  private CustomDomChildrenDescription.AttributeDescriptor myAttributesDescriptor;

  public DomExtensionImpl(final Type type, final XmlName xmlName) {
    myType = type;
    myXmlName = xmlName;
  }

  public void setTagNameDescriptor(CustomDomChildrenDescription.TagNameDescriptor tagNameDescriptor) {
    myTagNameDescriptor = tagNameDescriptor;
  }

  public CustomDomChildrenDescription.TagNameDescriptor getTagNameDescriptor() {
    return myTagNameDescriptor;
  }

  public CustomDomChildrenDescription.AttributeDescriptor getAttributesDescriptor() {
    return myAttributesDescriptor;
  }

  public void setAttributesDescriptor(CustomDomChildrenDescription.AttributeDescriptor attributesDescriptor) {
    myAttributesDescriptor = attributesDescriptor;
  }

  @NotNull
  public XmlName getXmlName() {
    return myXmlName;
  }

  @Override
  @NotNull
  public Type getType() {
    return myType;
  }

  @Override
  public DomExtension setDeclaringElement(@NotNull DomElement declaringElement) {
    DomAnchor<DomElement> anchor = DomAnchorImpl.createAnchor(declaringElement, true);
    return setDeclaringDomElement(() -> anchor.retrieveDomElement());
  }

  @Override
  public DomExtension setDeclaringDomElement(@NotNull Supplier<? extends DomElement> declarationFinder) {
    putUserData(KEY_DOM_DECLARATION, declarationFinder);
    return this;
  }

  @Override
  public DomExtension setDeclaringElement(@NotNull PsiElement declaringElement) {
    putUserData(DECLARING_ELEMENT_KEY, SmartPointerManager.getInstance(declaringElement.getProject()).createSmartPsiElementPointer(declaringElement));
    return this;
  }

  @Override
  public DomExtension setConverter(@NotNull Converter converter) {
    return setConverter(converter, false);
  }

  @Override
  public final DomExtension setConverter(@NotNull final Converter converter, final boolean soft) {
    myConverter = converter;
    mySoft = soft;
    return this;
  }

  @Override
  public DomExtension addCustomAnnotation(@NotNull final Annotation anno) {
    myCustomAnnotations.add(anno);
    return this;
  }

  @Override
  public <T> void putUserData(Key<T> key, T value) {
    Map<Key<?>, Object> map = myUserMap;
    if (map == null) {
      map = new THashMap<>();
      myUserMap = map;
    }
    map.put(key, value);
  }

  @Override
  public DomExtension addExtender(final DomExtender extender) {
    if (myUserMap == null || !myUserMap.containsKey(DOM_EXTENDER_KEY)) {
      putUserData(DOM_EXTENDER_KEY, new SmartList<>());
    }
    //noinspection unchecked
    ((List<DomExtender<?>>)myUserMap.get(DOM_EXTENDER_KEY)).add(extender);
    return this;
  }

  public final DomExtensionImpl setCount(int count) {
    myCount = count;
    return this;
  }

  public final int getCount() {
    return myCount;
  }

  public final <T extends DomChildDescriptionImpl> T addAnnotations(T t) {
    t.setUserMap(myUserMap);
    if (myConverter != null) {
      t.addCustomAnnotation(new ConvertAnnotationImpl(myConverter, mySoft));
    }
    for (final Annotation anno : myCustomAnnotations) {
      t.addCustomAnnotation(anno);
    }
    return t;
  }
}
