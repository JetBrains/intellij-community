/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.xml.dom;

import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.JavaNameStrategy;
import com.intellij.util.xml.NameStrategy;
import org.intellij.plugins.relaxNG.model.CommonElement;
import org.jetbrains.annotations.NotNull;

@NameStrategy(JavaNameStrategy.class)
public interface RngDomElement extends DomElement, CommonElement<XmlElement> {
  /**
   * Returns the value of the ns child.
   *
   * @return the value of the ns child.
   */
  @NotNull
  @Attribute("ns")
  GenericAttributeValue<String> getNamespace();

  /**
   * Returns the value of the datatypeLibrary child.
   *
   * @return the value of the datatypeLibrary child.
   */
  @NotNull
  GenericAttributeValue<String> getDatatypeLibrary();
}
