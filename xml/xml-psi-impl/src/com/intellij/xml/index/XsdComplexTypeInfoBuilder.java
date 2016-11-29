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
package com.intellij.xml.index;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.xml.NanoXmlUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 7/4/12
 * Time: 6:37 PM
 */
public class XsdComplexTypeInfoBuilder extends NanoXmlUtil.IXMLBuilderAdapter {
  private final static String SIGN = "";
  public static final String HTTP_WWW_W3_ORG_2001_XMLSCHEMA = "http://www.w3.org/2001/XMLSchema";
  // base type -> inherited types
  private final MultiMap<SchemaTypeInfo, SchemaTypeInfo> myMap;
  private NameSpaceHelper myNameSpaceHelper;
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.index.XsdComplexTypeInfoBuilder");

  public void setNameSpaceHelper(NameSpaceHelper nameSpaceHelper) {
    myNameSpaceHelper = nameSpaceHelper;
  }

  public static MultiMap<SchemaTypeInfo, SchemaTypeInfo> parse(final InputStream is) {
    return parse(new InputStreamReader(is));
  }

  public static MultiMap<SchemaTypeInfo, SchemaTypeInfo> parse(final Reader reader) {
    try {
      final XsdComplexTypeInfoBuilder builder = new XsdComplexTypeInfoBuilder();
      final NameSpaceHelper helper = new NameSpaceHelper();
      builder.setNameSpaceHelper(helper);
      NanoXmlUtil.parse(reader, builder, helper);
      final MultiMap<SchemaTypeInfo,SchemaTypeInfo> map = builder.getMap();
      return map;
    } finally {
      try {
        if (reader != null) {
          reader.close();
        }
      }
      catch (IOException e) {
        // can never happen
      }
    }
  }

  private XsdComplexTypeInfoBuilder() {
    myMap = new MultiMap<>();
  }

  public MultiMap<SchemaTypeInfo, SchemaTypeInfo> getMap() {
    return myMap;
  }

  // todo work with substitution groups also!

  private String myCurrentElementName;
  private String myCurrentElementNsName;
  private String myCurrentComplexTypeName;
  private String myCurrentComplexTypeNsName;
  private String myCurrentSimpleTypeName;
  private String myCurrentSimpleTypeNsName;

  private boolean myInsideSchema;
  private boolean myInsideRestriction;
  private boolean myInsideExtension;
  private boolean myInsideContent;

  @Override
  public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
    if (! HTTP_WWW_W3_ORG_2001_XMLSCHEMA.equals(nsURI)) return;
    myInsideSchema = false;
    if ("schema".equals(name)) {
      myInsideSchema = true;
    } else if ("complexType".equals(name)) {
      myCurrentComplexTypeName = SIGN;
      myCurrentComplexTypeNsName = nsURI;
    } else if ("simpleType".equals(name)) {
      myCurrentSimpleTypeName = SIGN;
      myCurrentSimpleTypeNsName = nsURI;
    } else if ("element".equals(name)) {
      myCurrentElementNsName = nsURI;
      myCurrentElementName = SIGN;
    } else if ("restriction".equals(name)) {
      myInsideRestriction = true;
    } else if ("extension".equals(name)) {
      myInsideExtension = true;
    } else if ("simpleContent".equals(name) || "complexContent".equals(name)) {
      myInsideContent = true;
    }
  }

  @Override
  public void endElement(String name, String nsPrefix, String nsURI) throws Exception {
    if (! HTTP_WWW_W3_ORG_2001_XMLSCHEMA.equals(nsURI)) return;
    if ("schema".equals(name)) {
      myInsideSchema = false;
    } else if ("complexType".equals(name)) {
      myCurrentComplexTypeName = null;
      myCurrentComplexTypeNsName = null;
    } else if ("simpleType".equals(name)) {
      myCurrentSimpleTypeName = null;
      myCurrentSimpleTypeNsName = null;
    } else if ("element".equals(name)) {
      myCurrentElementNsName = null;
      myCurrentElementName = null;
    } else if ("restriction".equals(name)) {
      myInsideRestriction = false;
    } else if ("extension".equals(name)) {
      myInsideExtension = false;
    } else if ("simpleContent".equals(name) || "complexContent".equals(name)) {
      myInsideContent = false;
    }
  }

  @Override
  public void addAttribute(String key, String nsPrefix, String nsURI, String value, String type) throws Exception {
    if (! StringUtil.isEmptyOrSpaces(nsURI) && ! HTTP_WWW_W3_ORG_2001_XMLSCHEMA.equals(nsURI)) return;
    if ("base".equals(key)) {
      if (myCurrentComplexTypeName != null && myInsideContent && (myInsideExtension || myInsideRestriction)) {
        putTypeDataToMap(nsURI, value, myCurrentComplexTypeName, myCurrentComplexTypeNsName);
      } else if (myCurrentSimpleTypeName != null && myInsideRestriction) {
        putTypeDataToMap(nsURI, value, myCurrentSimpleTypeName, myCurrentSimpleTypeNsName);
      }
    } else if (myInsideSchema) {
    } else if ("name".equals(key) || "ref".equals(key)) {
      if (SIGN.equals(myCurrentElementName) && ! myInsideContent && ! myInsideExtension && ! myInsideRestriction && ! myInsideSchema &&
        myCurrentComplexTypeName == null && myCurrentSimpleTypeName == null) {
        myCurrentElementName = value;
      } else if (SIGN.equals(myCurrentComplexTypeName) && ! myInsideContent && ! myInsideExtension && ! myInsideRestriction && ! myInsideSchema &&
              myCurrentSimpleTypeName == null) {
        myCurrentComplexTypeName = value;
      } else if (SIGN.equals(myCurrentSimpleTypeName) && ! myInsideContent && ! myInsideExtension && ! myInsideRestriction && ! myInsideSchema &&
                 myCurrentComplexTypeName == null) {
        myCurrentSimpleTypeName = value;
      }
    }
  }

  private void putTypeDataToMap(String nsURI, String value, final String typeName, final String typeNamespace) {
    /*final int separatorIdx = value.indexOf(':');
    final String ns = separatorIdx <= 0 ? "" : new String(value.substring(0, separatorIdx));
    final String element = separatorIdx <= 0 ? value : new String(value.substring(separatorIdx + 1));
    String nsUri = myNameSpaceHelper.getNamespaces().get(ns);
    nsUri = (nsUri == null ? ns : nsURI);*/

    final boolean isAnonymous = SIGN.equals(typeName);
    if (isAnonymous && myCurrentElementName != null) {
      myMap.putValue(createSchemaTypeInfo(value, true), createSchemaTypeInfo(myCurrentElementName, false));
    } else {
      myMap.putValue(createSchemaTypeInfo(value, true), createSchemaTypeInfo(typeName, true));
      //myMap.putValue(new SchemaTypeInfo(element, true, nsURI), new SchemaTypeInfo(typeName, true, typeNamespace));
    }
  }

  private SchemaTypeInfo createSchemaTypeInfo(final String value, final boolean isType) {
    final int separatorIdx = value.indexOf(':');
    final String ns = separatorIdx <= 0 ? "" : new String(value.substring(0, separatorIdx));
    final String element = separatorIdx <= 0 ? value : new String(value.substring(separatorIdx + 1));
    String nsUri = myNameSpaceHelper.getNamespaces().get(ns);
    nsUri = nsUri == null ? ns : nsUri;
    return new SchemaTypeInfo(element, isType, nsUri);
  }

  private static class NameSpaceHelper extends NanoXmlUtil.EmptyValidator {
    public static final String XMLNS = "xmlns";
    public static final String XMLNS_ = "xmlns:";
    private boolean myInSchema;
    private final Map<String, String> myNamespaces;

    private NameSpaceHelper() {
      myNamespaces = new HashMap<>();
    }

    @Override
    public void attributeAdded(String key, String value, String systemId, int lineNr) {
      super.attributeAdded(key, value, systemId, lineNr);
      if (myInSchema) {
        if (key.startsWith(XMLNS)) {
          if (key.length() == XMLNS.length()) {
            myNamespaces.put("", value);
          } else if (key.startsWith(XMLNS_)) {
            final String prefix = new String(key.substring(XMLNS_.length()));
            myNamespaces.put(prefix, value);
          }
        }
      }
    }

    @Override
    public void elementStarted(String name, String systemId, int lineNr) {
      super.elementStarted(name, systemId, lineNr);
      myInSchema = "schema".equals(name) || name.endsWith(":schema");
    }

    public Map<String, String> getNamespaces() {
      return myNamespaces;
    }
  }
}
