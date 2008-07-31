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
  private String myKey;

  public PropertyStubImpl(final StubElement parent, final String key) {
    super(parent, PropertiesElementTypes.PROPERTY);
    myKey = key;
  }

  public String getKey() {
    return myKey;
  }
}