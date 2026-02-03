// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;

public class PropertiesListStubImpl extends StubBase<PropertiesList> implements PropertiesListStub {
  public PropertiesListStubImpl(final StubElement parent) {
    super(parent, PropertiesElementTypes.PROPERTIES_LIST);
  }

  @Override
  public IElementType getElementType() {
    return PropertiesElementTypes.PROPERTIES_LIST;
  }
}
