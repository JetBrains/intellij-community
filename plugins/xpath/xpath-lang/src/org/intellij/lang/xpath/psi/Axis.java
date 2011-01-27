/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.psi;

@SuppressWarnings({"UnusedDeclaration"})
public enum Axis {
    ANCESTOR,
    ANCESTOR_OR_SELF,
    ATTRIBUTE,
    CHILD,
    DESCENDANT,
    DESCENDANT_OR_SELF,
    FOLLOWING,
    FOLLOWING_SIBLING,
    NAMESPACE,
    PARENT,
    PRECEDING,
    PRECEDING_SIBLING,
    SELF,
    INVALID;

    public String getName() {
        return name().toLowerCase().replace('_', '-');
    }

    public static Axis fromName(String name) {
      try {
        return valueOf(name.toUpperCase().replace('-', '_'));
      } catch (IllegalArgumentException e) {
        return INVALID;
      }
    }
}
