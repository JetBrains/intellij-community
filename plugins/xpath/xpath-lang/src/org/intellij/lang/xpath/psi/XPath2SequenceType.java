/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 08.01.11
*/
public class XPath2SequenceType extends XPath2Type {
  public enum Cardinality {
    OPTIONAL("?"), ONE_OR_MORE("+"), ZERO_OR_MORE("*"), UNDEFINED(null);

    private final String myType;

    Cardinality(String c) {
      myType = c;
    }
  }

  private final XPathType myType;

  protected XPath2SequenceType(XPathType s, Cardinality c) {
    super(makeName(s, c), ANY);
    myType = s;
  }

  private static String makeName(XPathType s, Cardinality c) {
    return c.myType != null ? s.getName() + c.myType : "sequence of " + s.getName();
  }

  public XPathType getType() {
    return myType;
  }

  public static XPath2Type create(@NotNull XPathType type, Cardinality c) {
    return new XPath2SequenceType(type, c);
  }

  public static XPath2Type create(@NotNull XPathType type) {
    return new XPath2SequenceType(type, Cardinality.UNDEFINED);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final XPath2SequenceType that = (XPath2SequenceType)o;

    if (!myType.equals(that.myType)) return false;

    return true;
  }

  @Override
  public boolean isAssignableFrom(@NotNull XPathType type) {
    return super.isAssignableFrom(type) || getType().isAssignableFrom(unwrap(type));
  }

  private static XPathType unwrap(XPathType type) {
    return type instanceof XPath2SequenceType ? ((XPath2SequenceType)type).getType() : type;
  }

  @Override
  public boolean canBePromotedTo(XPathType type) {
    return super.canBePromotedTo(type) || getType().canBePromotedTo(unwrap(type));
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myType.hashCode();
    return result;
  }
}