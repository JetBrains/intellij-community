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
package com.intellij.util.xml.reflect;

import com.intellij.openapi.util.Key;
import com.intellij.util.SmartList;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.impl.ConvertAnnotationImpl;
import com.intellij.util.xml.impl.DomChildDescriptionImpl;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public class DomExtensionImpl implements DomExtension {
  public static final Key<List<DomExtender>> DOM_EXTENDER_KEY = Key.create("Dom.Extender");
  private final XmlName myXmlName;
  private final Type myType;
  private Converter myConverter;
  private final List<Annotation> myCustomAnnos = new SmartList<Annotation>();
  private boolean mySoft;
  private int myCount = 1;
  private Map myUserMap;
  private CustomDomChildrenDescription.TagNameDescriptor myTagNameDescriptor = CustomDomChildrenDescription.TagNameDescriptor.EMPTY;

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

  @NotNull
  public XmlName getXmlName() {
    return myXmlName;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  public DomExtension setDeclaringElement(@NotNull DomElement declaringElement) {
    putUserData(KEY_DECLARATION, declaringElement);
    return this;
  }

  public DomExtension setConverter(@NotNull Converter converter) {
    return setConverter(converter, false);
  }

  public final DomExtension setConverter(@NotNull final Converter converter, final boolean soft) {
    myConverter = converter;
    mySoft = soft;
    return this;
  }

  public DomExtension addCustomAnnotation(@NotNull final Annotation anno) {
    myCustomAnnos.add(anno);
    return this;
  }

  public <T> void putUserData(final Key<T> key, final T value) {
    if (myUserMap == null) myUserMap = new THashMap();
    myUserMap.put(key, value);
  }

  public DomExtension addExtender(final DomExtender extender) {
    if (myUserMap == null || !myUserMap.containsKey(DOM_EXTENDER_KEY)) {
      putUserData(DOM_EXTENDER_KEY, new SmartList<DomExtender>());
    }
    ((List<DomExtender>)myUserMap.get(DOM_EXTENDER_KEY)).add(extender);
    return this;
  }

  public final DomExtensionImpl setCount(final int count) {
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
    for (final Annotation anno : myCustomAnnos) {
      t.addCustomAnnotation(anno);
    }
    return t;
  }
}
