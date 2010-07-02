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
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomReflectionUtil;
import com.intellij.util.xml.EvaluatedXmlName;
import com.intellij.util.xml.JavaMethod;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class CustomDomChildrenDescriptionImpl extends AbstractDomChildDescriptionImpl implements CustomDomChildrenDescription, AbstractCollectionChildDescription {
  @Nullable private final JavaMethod myGetter;
  public static final NotNullFunction<DomInvocationHandler,List<XmlTag>> CUSTOM_TAGS_GETTER = new NotNullFunction<DomInvocationHandler, List<XmlTag>>() {
    @NotNull
    public List<XmlTag> fun(final DomInvocationHandler handler) {
      return DomImplUtil.getCustomSubTags(handler, handler.getXmlTag().getSubTags(), handler.getFile());
    }
  };
  private final TagNameDescriptor myTagNameDescriptor;

  public CustomDomChildrenDescriptionImpl(@NotNull final JavaMethod getter) {
    this(getter, DomReflectionUtil.extractCollectionElementType(getter.getGenericReturnType()), TagNameDescriptor.EMPTY);
  }

  public CustomDomChildrenDescriptionImpl(@Nullable final JavaMethod getter, @NotNull Type type, @NotNull TagNameDescriptor descriptor) {
    super(type);
    myGetter = getter;
    myTagNameDescriptor = descriptor;
  }

  @Nullable public JavaMethod getGetterMethod() {
    return myGetter;
  }

  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomInvocationHandler parent) {
    if (!parent.getGenericInfo().checkInitialized()) {
      return Collections.emptyList();
    }
    return parent.getCollectionChildren(this, CUSTOM_TAGS_GETTER);
  }

  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomElement parent) {
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(parent);
    if (handler != null) return getValues(handler);

    assert myGetter != null;
    return (List<? extends DomElement>)myGetter.invoke(parent, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  public int compareTo(final AbstractDomChildDescriptionImpl o) {
    return equals(o) ? 0 : -1;
  }

  public List<XmlTag> getSubTags(final DomInvocationHandler handler, final XmlTag[] subTags, final XmlFile file) {
    return DomImplUtil.getCustomSubTags(handler, subTags, file);
  }

  public EvaluatedXmlName createEvaluatedXmlName(final DomInvocationHandler parent, final XmlTag childTag) {
    return new DummyEvaluatedXmlName(childTag.getLocalName(), childTag.getNamespace());
  }

  @NotNull
  @Override
  public TagNameDescriptor getTagNameDescriptor() {
    return myTagNameDescriptor;
  }
}
