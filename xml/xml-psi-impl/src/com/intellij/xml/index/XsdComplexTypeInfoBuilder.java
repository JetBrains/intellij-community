// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.index;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

public class XsdComplexTypeInfoBuilder implements NanoXmlBuilder {
  private final static String SIGN = "";
  public static final String HTTP_WWW_W3_ORG_2001_XMLSCHEMA = "http://www.w3.org/2001/XMLSchema";
  // base type -> inherited types
  private final MultiMap<SchemaTypeInfo, SchemaTypeInfo> myMap;
  private NameSpaceHelper myNameSpaceHelper;

  public void setNameSpaceHelper(NameSpaceHelper nameSpaceHelper) {
    myNameSpaceHelper = nameSpaceHelper;
  }

  public static MultiMap<SchemaTypeInfo, SchemaTypeInfo> parse(final InputStream is) {
    return parse(new InputStreamReader(is));
  }

  public static MultiMap<SchemaTypeInfo, SchemaTypeInfo> parse(@NotNull Reader reader) {
    try {
      final XsdComplexTypeInfoBuilder builder = new XsdComplexTypeInfoBuilder();
      final NameSpaceHelper helper = new NameSpaceHelper();
      builder.setNameSpaceHelper(helper);
      NanoXmlUtil.parse(reader, builder, helper);
      return builder.getMap();
    }
    finally {
      try {
        reader.close();
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
  private String myCurrentComplexTypeName;
  private String myCurrentSimpleTypeName;

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
    } else if ("simpleType".equals(name)) {
      myCurrentSimpleTypeName = SIGN;
    } else if ("element".equals(name)) {
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
    } else if ("simpleType".equals(name)) {
      myCurrentSimpleTypeName = null;
    } else if ("element".equals(name)) {
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
    if (!StringUtil.isEmptyOrSpaces(nsURI) && !HTTP_WWW_W3_ORG_2001_XMLSCHEMA.equals(nsURI)) return;
    if ("base".equals(key)) {
      if (myCurrentComplexTypeName != null && myInsideContent && (myInsideExtension || myInsideRestriction)) {
        putTypeDataToMap(value, myCurrentComplexTypeName);
      }
      else if (myCurrentSimpleTypeName != null && myInsideRestriction) {
        putTypeDataToMap(value, myCurrentSimpleTypeName);
      }
    }
    else if (myInsideSchema) {

    }
    else if ("name".equals(key) || "ref".equals(key)) {
      if (SIGN.equals(myCurrentElementName) &&
          !myInsideContent &&
          !myInsideExtension &&
          !myInsideRestriction &&
          myCurrentComplexTypeName == null &&
          myCurrentSimpleTypeName == null) {
        myCurrentElementName = value;
      }
      else if (SIGN.equals(myCurrentComplexTypeName) &&
               !myInsideContent &&
               !myInsideExtension &&
               !myInsideRestriction &&
               myCurrentSimpleTypeName == null) {
        myCurrentComplexTypeName = value;
      }
      else if (SIGN.equals(myCurrentSimpleTypeName) &&
               !myInsideContent &&
               !myInsideExtension &&
               !myInsideRestriction &&
               myCurrentComplexTypeName == null) {
        myCurrentSimpleTypeName = value;
      }
    }
  }

  private void putTypeDataToMap(String value, final String typeName) {
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
    final String ns = separatorIdx <= 0 ? "" : value.substring(0, separatorIdx);
    final String element = separatorIdx <= 0 ? value : value.substring(separatorIdx + 1);
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
            final String prefix = key.substring(XMLNS_.length());
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
