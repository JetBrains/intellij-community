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

// Generated on Fri Aug 10 16:21:20 CEST 2007
// DTD/Schema  :    http://relaxng.org/ns/structure/1.0

package org.intellij.plugins.relaxNG.xml.dom;

import com.intellij.util.xml.NamedEnum;

/**
 * http://relaxng.org/ns/structure/1.0:combineAttrType enumeration.
 */
public enum Combine implements NamedEnum {
  CHOICE("choice"),
  INTERLEAVE("interleave");

  private final String value;

  Combine(String value) {
    this.value = value;
  }

  @Override
  public String getValue() {
    return value;
  }

}
