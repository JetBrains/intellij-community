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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 07.01.11
*/
@SuppressWarnings({"UnusedDeclaration"})
public class XPath2Type extends XPathType {
  public static final String XMLSCHEMA_NS = "http://www.w3.org/2001/XMLSchema";

  private static Map<QName, XPath2Type> ourMap = new HashMap<>();

  public static final XPath2Type ITEM = createItemType("item()", ANY);
  public static final XPath2Type NODE = createItemType("node()", ITEM);
  public static final XPath2Type TEXT = createItemType("text()", ITEM);
  public static final XPath2Type ELEMENT = createItemType("element()", NODE);
  public static final XPath2Type ATTRIBUTE = createItemType("attribute()", NODE);
  public static final XPath2Type PROCESSING_INSTRUCTION = createItemType("processing-instruction()", NODE);

  public static final XPath2Type NODE_SEQUENCE = XPath2SequenceType.create(NODE, XPath2SequenceType.Cardinality.ZERO_OR_MORE);
  public static final XPath2Type SEQUENCE = XPath2SequenceType.create(ITEM, XPath2SequenceType.Cardinality.ZERO_OR_MORE);

  public static final XPath2Type ANYATOMICTYPE = createSchemaType("anyAtomicType", ITEM);
  public static final XPath2Type UNTYPEDATOMIC = createSchemaType("untypedAtomic", ANYATOMICTYPE);

  public static final XPath2Type STRING = createSchemaType("string", ANYATOMICTYPE);
  public static final XPath2Type NORMALIZEDSTRING = createSchemaType("normalizedString", STRING);
  public static final XPath2Type TOKEN = createSchemaType("token", NORMALIZEDSTRING);
  public static final XPath2Type LANGUAGE = createSchemaType("language", TOKEN);
  public static final XPath2Type NMTOKEN = createSchemaType("NMTOKEN", TOKEN);
  public static final XPath2Type NAME = createSchemaType("Name", TOKEN);
  public static final XPath2Type NCNAME = createSchemaType("NCName", NAME);
  public static final XPath2Type ID = createSchemaType("ID", NCNAME);
  public static final XPath2Type IDREF = createSchemaType("IDREF", NCNAME);
  public static final XPath2Type ENTITY = createSchemaType("ENTITY", NCNAME);

  public static final XPath2Type BOOLEAN = createSchemaType("boolean", ANYATOMICTYPE);
  public static final XPath2Type BOOLEAN_STRICT = new SchemaType("boolean", BOOLEAN);

  public static final XPath2Type NUMERIC = createSchemaType("numeric", ANYATOMICTYPE);
  public static final XPath2Type FLOAT = createSchemaType("float", NUMERIC);
  public static final XPath2Type DOUBLE = createSchemaType("double", NUMERIC);
  public static final XPath2Type DECIMAL = createSchemaType("decimal", NUMERIC);
  public static final XPath2Type INTEGER = createSchemaType("integer", DECIMAL);

  public static final XPath2Type QNAME = createSchemaType("QName", ANYATOMICTYPE);
  public static final XPath2Type ANYURI = createSchemaType("anyURI", ANYATOMICTYPE);
  public static final XPath2Type BASE64BINARY = createSchemaType("base64Binary", ANYATOMICTYPE);
  public static final XPath2Type HEXBINARY = createSchemaType("hexBinary", ANYATOMICTYPE);

  public static final XPath2Type DATE = createSchemaType("date", ANYATOMICTYPE);
  public static final XPath2Type TIME = createSchemaType("time", ANYATOMICTYPE);
  public static final XPath2Type DATETIME = createSchemaType("dateTime", ANYATOMICTYPE);
  public static final XPath2Type DURATION = createSchemaType("duration", ANYATOMICTYPE);
  public static final XPath2Type DAYTIMEDURATION = createSchemaType("dayTimeDuration", DURATION);
  public static final XPath2Type YEARMONTHDURATION = createSchemaType("yearMonthDuration", DURATION);

  public static final XPath2Type GYEARMONTH = createSchemaType("gYearMonth", ANYATOMICTYPE);
  public static final XPath2Type GYEAR = createSchemaType("gYear", ANYATOMICTYPE);
  public static final XPath2Type GMONTHDAY = createSchemaType("gMonthDay", ANYATOMICTYPE);
  public static final XPath2Type GDAY = createSchemaType("gDay", ANYATOMICTYPE);
  public static final XPath2Type GMONTH = createSchemaType("gMonth", ANYATOMICTYPE);

  private final XPathType mySuperType;

  protected XPath2Type(String s, XPathType superType) {
    super(s, false);
    mySuperType = superType;
  }

  protected static XPath2Type createSchemaType(String s, XPathType superType) {
    final XPath2Type type = new SchemaType(s, superType);
    ourMap.put(new QName(XMLSCHEMA_NS, s), type);
    return type;
  }

  protected static XPath2Type createItemType(String s, XPathType superType) {
    final XPath2Type type = new ItemType(s, superType);
    ourMap.put(new QName("", s), type);
    return type;
  }

  public XPathType getSuperType() {
    return mySuperType;
  }

  @Override
  public boolean isAssignableFrom(@NotNull XPathType type) {
    if (type instanceof XPath2SequenceType) {
      type = ((XPath2SequenceType)type).getType();
    }
    if (type instanceof XPath2Type) {
      if (this.equals(type)) {
        return true;
      } else {
        XPathType t = ((XPath2Type)type).getSuperType();
        while (t != null) {
          if (t.equals(this)) return true;
          if (!(t instanceof XPath2Type)) {
            break;
          }
          t = ((XPath2Type)t).getSuperType();
        }
      }
    }

    return false;
  }

  @Override
  public boolean canBePromotedTo(XPathType type) {
    while (type instanceof XPath2SequenceType) {
      type = ((XPath2SequenceType)type).getType();
    }
    if (this == ITEM || NODE.isAssignableFrom(this) || (this == ANYATOMICTYPE && ANYATOMICTYPE.isAssignableFrom(type))) return true;

    if (FLOAT.isAssignableFrom(this) && type == DOUBLE) return true;
    if (DECIMAL.isAssignableFrom(this) && (type == DOUBLE || type == FLOAT)) return true;
    if (INTEGER.isAssignableFrom(this) && (type == DOUBLE || type == FLOAT || type == INTEGER || type == DECIMAL)) return true;

    if (ANYURI.isAssignableFrom(this) && type == STRING) return true;

    // effective boolean value
    if (ANYURI.isAssignableFrom(this) && type == BOOLEAN) return true;
    if (STRING.isAssignableFrom(this) && type == BOOLEAN) return true;
    if (NUMERIC.isAssignableFrom(this) && type == BOOLEAN) return true;
    if (this == UNTYPEDATOMIC && type == BOOLEAN) return true;

    // function parameters do not use eff. boolean value, type must strictly match
    if (this == BOOLEAN && type == BOOLEAN_STRICT) return true;

    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final XPath2Type type = (XPath2Type)o;

    if (!mySuperType.equals(type.mySuperType)) return false;

    if (!Comparing.equal(getQName(), type.getQName())) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return getName().hashCode() + mySuperType.hashCode() * 5;
  }

  @Nullable
  public static XPath2Type schemaType(String name) {
    return ourMap.get(new QName(XMLSCHEMA_NS, name));
  }

  @Nullable
  public static XPath2Type fromName(QName name) {
    final String local = name.getLocalPart();
    if (local.endsWith("*")) {
      return XPath2SequenceType.create(lookupSequenceType(name, local), XPath2SequenceType.Cardinality.ZERO_OR_MORE);
    } else if (local.endsWith("+")) {
      return XPath2SequenceType.create(lookupSequenceType(name, local), XPath2SequenceType.Cardinality.ONE_OR_MORE);
    } else if (local.endsWith("?")) {
      return XPath2SequenceType.create(lookupSequenceType(name, local), XPath2SequenceType.Cardinality.OPTIONAL);
    }
    return ourMap.get(name);
  }

  public QName getQName() {
    return new QName(null, type);
  }

  private static XPathType lookupSequenceType(QName name, String local) {
    final XPath2Type type = ourMap.get(new QName(name.getNamespaceURI(), local.substring(0, local.length() - 1)));
    return type != null ? type : UNKNOWN;
  }

  public static XPathType mapType(XPathType type) {

    if (type == XPathType.STRING) {
      type = STRING;
    } else if (type == XPathType.BOOLEAN) {
      type = BOOLEAN;
    } else if (type == XPathType.NUMBER) {
      type = NUMERIC;
    } else if (type == XPathType.NODESET) {
      type = XPath2SequenceType.create(NODE, XPath2SequenceType.Cardinality.ZERO_OR_MORE);
    }
    return type;
  }

  public static class ItemType extends XPath2Type {
    ItemType(String name, XPathType superType) {
      super(name, superType);
    }

    @Override
    public boolean isAssignableFrom(@NotNull XPathType type) {
      return super.isAssignableFrom(type) || type == NODESET || this == ITEM;
    }
  }

  public static class SchemaType extends XPath2Type {
    SchemaType(String name, XPathType superType) {
      super(name, superType);
    }

    @Override
    public String getName() {
      return "xs:" + super.getName();
    }

    @Override
    public QName getQName() {
      return new QName(XMLSCHEMA_NS, type);
    }

    @Override
    public boolean isAbstract() {
      return this == NUMERIC;
    }

    public static List<XPath2Type> listSchemaTypes() {
      return ContainerUtil.filter(ourMap.values(), type1 -> type1.getQName().getNamespaceURI().equals(XMLSCHEMA_NS) && !type1.isAbstract());
    }
  }
}