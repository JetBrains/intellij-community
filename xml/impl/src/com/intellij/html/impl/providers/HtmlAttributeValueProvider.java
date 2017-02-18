/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.html.impl.providers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

public class HtmlAttributeValueProvider {
  public static final ExtensionPointName<HtmlAttributeValueProvider> EP_NAME =
    new ExtensionPointName<>("com.intellij.html.attributeValueProvider");

  /**
   * Calculates attribute value. Used when it is impossible to get attribute value as text of PSI element
   * @param tag
   * @param attributeName
   * @return calculated value
   */
  @Nullable
  public String getCustomAttributeValues(final XmlTag tag, final String attributeName) {
    return null;
  }
}

