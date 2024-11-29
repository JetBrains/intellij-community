// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

public abstract class DomChildDescriptionImpl extends AbstractDomChildDescriptionImpl implements DomChildrenDescription {
  private final XmlName myTagName;

  protected DomChildDescriptionImpl(final XmlName tagName, final @NotNull Type type) {
    super(type);
    myTagName = tagName;
  }

  @Override
  public String getName() {
    return myTagName.getLocalName();
  }

  @Override
  public @NotNull String getXmlElementName() {
    return myTagName.getLocalName();
  }

  @Override
  public final @NotNull XmlName getXmlName() {
    return myTagName;
  }

  @Override
  public @NotNull String getCommonPresentableName(@NotNull DomElement parent) {
    return getCommonPresentableName(getDomNameStrategy(parent));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;

    final DomChildDescriptionImpl that = (DomChildDescriptionImpl)o;

    if (myTagName != null ? !myTagName.equals(that.myTagName) : that.myTagName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myTagName != null ? myTagName.hashCode() : 0);
    return result;
  }

  @Override
  public int compareTo(final AbstractDomChildDescriptionImpl o) {
    return o instanceof DomChildDescriptionImpl ? myTagName.compareTo(((DomChildDescriptionImpl)o).myTagName) : 1;
  }
}
