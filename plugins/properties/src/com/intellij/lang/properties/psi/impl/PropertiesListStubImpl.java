/*
 * @author max
 */
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.properties.parsing.PropertiesElementTypes;
import com.intellij.lang.properties.psi.PropertiesList;
import com.intellij.lang.properties.psi.PropertiesListStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

public class PropertiesListStubImpl extends StubBase<PropertiesList> implements PropertiesListStub {
  public PropertiesListStubImpl(final StubElement parent) {
    super(parent, PropertiesElementTypes.PROPERTIES_LIST);
  }
}