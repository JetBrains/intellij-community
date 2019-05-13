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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface GenericDomValue<T> extends DomElement, MutableGenericValue<T>{

  @NotNull
  Converter<T> getConverter();

  @Override
  @TagValue
  void setStringValue(String value);

  @Override
  void setValue(T value);

  /**
   * @return text of the value as it is specified in the underlying XML. No conversions or substitutions are made 
   */
  @Nullable
  String getRawText();
}
