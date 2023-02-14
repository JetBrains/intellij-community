// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.CustomDomChildrenDescription;
import com.intellij.util.xml.reflect.DomExtensionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class CustomDomChildrenDescriptionImpl extends AbstractDomChildDescriptionImpl implements CustomDomChildrenDescription, AbstractCollectionChildDescription {
  @Nullable private final JavaMethod myGetter;
  private final TagNameDescriptor myTagNameDescriptor;
  private final AttributeDescriptor myAttributeDescriptor;


  public CustomDomChildrenDescriptionImpl(@NotNull final JavaMethod getter) {
    this(getter, DomReflectionUtil.extractCollectionElementType(getter.getGenericReturnType()),
         AttributeDescriptor.EMPTY, AttributeDescriptor.EMPTY);
  }

  public CustomDomChildrenDescriptionImpl(DomExtensionImpl custom) {
    this(null, custom.getType(), custom.getTagNameDescriptor(), custom.getAttributesDescriptor());
  }

  private CustomDomChildrenDescriptionImpl(@Nullable final JavaMethod getter, @NotNull Type type,
                                          @Nullable TagNameDescriptor descriptor,
                                          @Nullable AttributeDescriptor attributesDescriptor) {
    super(type);
    myGetter = getter;
    myTagNameDescriptor = descriptor;
    myAttributeDescriptor = attributesDescriptor;
  }

  @Nullable public JavaMethod getGetterMethod() {
    return myGetter;
  }

  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomInvocationHandler parent) {
    if (!parent.getGenericInfo().checkInitialized()) {
      return Collections.emptyList();
    }
    return parent.getCollectionChildren(this);
  }

  @Override
  @NotNull
  public List<? extends DomElement> getValues(@NotNull final DomElement parent) {
    final DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(parent);
    if (handler != null) return getValues(handler);

    assert myGetter != null;
    //noinspection unchecked
    return (List<? extends DomElement>)myGetter.invoke(parent, ArrayUtilRt.EMPTY_OBJECT_ARRAY);
  }

  @Override
  public int compareTo(final AbstractDomChildDescriptionImpl o) {
    return equals(o) ? 0 : -1;
  }

  @Override
  public List<XmlTag> getSubTags(final DomInvocationHandler handler, final XmlTag[] subTags, final XmlFile file) {
    return DomImplUtil.getCustomSubTags(handler, subTags, file);
  }

  @Override
  public EvaluatedXmlName createEvaluatedXmlName(final DomInvocationHandler parent, final XmlTag childTag) {
    return new DummyEvaluatedXmlName(childTag.getLocalName(), childTag.getNamespace());
  }

  @Override
  public TagNameDescriptor getTagNameDescriptor() {
    return myTagNameDescriptor;
  }

  @Override
  public AttributeDescriptor getCustomAttributeDescriptor() {
    return myAttributeDescriptor;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof CustomDomChildrenDescriptionImpl;
  }

  @Override
  public int hashCode() {
    return 239;
  }
}
