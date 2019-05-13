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
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public Converter<Object> getConverter() {
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
  @Nullable
  public String getRawText() {
    throw new UnsupportedOperationException("Method getRawText is not yet implemented in " + getClass().getName());
  }

  @Override
  public Object getValue() {
    throw new UnsupportedOperationException("Method getValue is not yet implemented in " + getClass().getName());
  }
}
