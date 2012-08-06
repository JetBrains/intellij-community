/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class MockGenericAttributeValue extends MockDomElement implements GenericAttributeValue<Object> {
  @Override
  public XmlAttribute getXmlAttribute() {
    throw new UnsupportedOperationException("Method getXmlAttribute is not yet implemented in " + getClass().getName());
  }

  @Override
  public XmlAttributeValue getXmlAttributeValue() {
    throw new UnsupportedOperationException("Method getXmlAttributeValue is not yet implemented in " + getClass().getName());
  }

  @Override
  @NotNull
  public Converter getConverter() {
    throw new UnsupportedOperationException("Method getConverter is not yet implemented in " + getClass().getName());
  }

  @Override
  public void setStringValue(String value) {
    throw new UnsupportedOperationException("Method setStringValue is not yet implemented in " + getClass().getName());
  }

  @Override
  public void setValue(Object value) {
    throw new UnsupportedOperationException("Method setValue is not yet implemented in " + getClass().getName());
  }

  @Override
  public String getStringValue() {
    throw new UnsupportedOperationException("Method getStringValue is not yet implemented in " + getClass().getName());
  }

  @Override
  public String getRawText() {
    throw new UnsupportedOperationException("Method getRawText is not yet implemented in " + getClass().getName());
  }

  @Override
  public Object getValue() {
    throw new UnsupportedOperationException("Method getValue is not yet implemented in " + getClass().getName());
  }
}
