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

import org.jetbrains.annotations.NotNull;

public class XPathType {
    public static final XPathType UNKNOWN = new XPathType("unknown", true);
    public static final XPathType ANY = new XPathType("any", true);

    public static final XPathType NUMBER = new XPathType("number", false);
    public static final XPathType BOOLEAN = new XPathType("boolean", false);
    public static final XPathType STRING = new XPathType("string", false);
    public static final XPathType NODESET = new XPathType("nodeset", false);

    protected final String type;
    private final boolean myAbstract;

    protected XPathType(String s, boolean isAbstract) {
        type = s;
        myAbstract = isAbstract;
    }

    public String toString() {
        return "XPathType: " + type;
    }

    public String getName() {
        return type;
    }

    public boolean isAbstract() {
        return myAbstract;
    }

  public static XPathType getSuperType(XPathType type) {
    return type instanceof XPath2Type ? ((XPath2Type)type).getSuperType() : null;
  }

  public static XPathType fromString(String value) {
        if ("string".equals(value)) {
            return XPathType.STRING;
        } else if ("number".equals(value)) {
            return XPathType.NUMBER;
        } else if ("boolean".equals(value)) {
            return XPathType.BOOLEAN;
        } else if ("nodeset".equals(value)) {
            return XPathType.NODESET;
        }
        return XPathType.UNKNOWN;
    }

  public boolean isAssignableFrom(XPathType type) {
    return isAbstract() || type.isAbstract() || this != NODESET || type == NODESET;
  }

  public boolean canBePromotedTo(XPathType type) {
    return type != NODESET;
  }

  public static boolean isAssignable(@NotNull XPathType left, @NotNull XPathType type) {
    if (left instanceof ChoiceType) {
      final XPathType[] types = ((ChoiceType)left).getTypes();
      for (XPathType t : types) {
        if (isAssignable(t, type)) {
          return true;
        }
      }
      return false;
    }
    return left.isAssignableFrom(type) || type.canBePromotedTo(left);
  }

  public static final class ChoiceType extends XPathType {
    private final XPathType[] myTypes;

    ChoiceType(XPathType[] types, String name) {
      super(name, true);
      myTypes = types;
    }

    public static XPathType create(XPathType... types) {
      final StringBuilder name = new StringBuilder();
      for (XPathType type : types) {
        if (name.length() > 0) {
          name.append(", ");
        }
        name.append(type.getName());
      }
      name.insert(0, "one of ");
      return new ChoiceType(types, name.toString());
    }

    public XPathType[] getTypes() {
      return myTypes;
    }

    @Override
    public boolean isAssignableFrom(@NotNull XPathType type) {
      return false;
    }

    @Override
    public boolean canBePromotedTo(XPathType type) {
      return false;
    }
  }
}