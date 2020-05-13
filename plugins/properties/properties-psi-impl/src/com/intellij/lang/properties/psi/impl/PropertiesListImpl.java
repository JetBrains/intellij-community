// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;

public class PropertiesListImpl extends PropertiesStubElementImpl<PropertiesListStub> implements PropertiesList {
  public PropertiesListImpl(final ASTNode node) {
    super(node);
  }

  public PropertiesListImpl(final PropertiesListStub stub) {
    super(stub, PropertiesElementTypes.PROPERTIES_LIST);
  }

  @Override
  public String toString() {
    return "PropertiesList";
  }

}
