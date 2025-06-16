// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PropertyStubImpl extends StubBase<Property> implements PropertyStub {
  private final String myKey;

  public PropertyStubImpl(final StubElement parent, final String key) {
    super(parent, PropertiesElementTypes.PROPERTY_TYPE);
    myKey = key;
  }

  @Override
  public String getKey() {
    return myKey;
  }
}
