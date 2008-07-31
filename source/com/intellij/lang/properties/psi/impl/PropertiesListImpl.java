package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;

/**
 * @author max
 */
public class PropertiesListImpl extends PropertiesStubElementImpl<PropertiesListStub> implements PropertiesList {
  public PropertiesListImpl(final ASTNode node) {
    super(node);
  }

  public PropertiesListImpl(final PropertiesListStub stub) {
    super(stub, PropertiesElementTypes.PROPERTIES_LIST);
  }

  public String toString() {
    return "PropertiesList";
  }
}
