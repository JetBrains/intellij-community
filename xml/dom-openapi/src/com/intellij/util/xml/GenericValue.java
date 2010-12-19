/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import org.jetbrains.annotations.Nullable;

/**
 * This interface may be interpreted as a reference, whose text is {@link #getStringValue()}, and resolving to
 * the result of {@link #getValue()} method.
 *
 * @author peter
 */
public interface GenericValue<T> {

  /**
   * @return the string representation of the value. Even if {@link #getValue()} returns null, this method
   * can return something more descriptive.
   */
  @TagValue
  @Nullable
  String getStringValue();

  /**
   * @return resolved value (may take time). It's strongly recommended that even if T is {@link String}, one uses
   * {@link #getStringValue()} method instead. 
   */
  @Nullable
  T getValue();

}