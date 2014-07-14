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
package com.intellij.util.xml.impl;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public abstract class DomChildDescriptionImpl extends AbstractDomChildDescriptionImpl implements DomChildrenDescription {
  private final XmlName myTagName;

  protected DomChildDescriptionImpl(final XmlName tagName, @NotNull final Type type) {
    super(type);
    myTagName = tagName;
  }

  @Override
  public String getName() {
    return myTagName.getLocalName();
  }

  @Override
  @NotNull
  public String getXmlElementName() {
    return myTagName.getLocalName();
  }

  @Override
  @NotNull
  public final XmlName getXmlName() {
    return myTagName;
  }

  @Override
  @NotNull
  public String getCommonPresentableName(@NotNull DomElement parent) {
    return getCommonPresentableName(getDomNameStrategy(parent));
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;

    final DomChildDescriptionImpl that = (DomChildDescriptionImpl)o;

    if (myTagName != null ? !myTagName.equals(that.myTagName) : that.myTagName != null) return false;

    return true;
  }

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
