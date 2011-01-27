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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import org.apache.commons.collections.map.CompositeMap;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.functions.*;
import org.intellij.lang.xpath.psi.XPathType;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class XsltFunctionContext extends DefaultFunctionContext {

  private static final Map<Pair<QName, Integer>, Function> XSLT_FUNCTIONS;

  private static final Factory<FunctionContext> FACTORY = new Factory<FunctionContext>() {
    @Override
    public FunctionContext create() {
      return new XsltFunctionContext();
    }
  };

  static {
    final Map<Pair<QName, Integer>, Function> decls = new HashMap<Pair<QName, Integer>, Function>();

    // string format-number(number, string, string?)
    addFunction(decls, new FunctionImpl("format-number", XPathType.STRING,
            new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL)));

    // string unparsed-entity-uri(string)
    addFunction(decls, new FunctionImpl("unparsed-entity-uri", XPathType.STRING,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

    // node-set key(string, object)
    addFunction(decls, new FunctionImpl("key", XPathType.NODESET,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED)));

    // string generate-id(node-set?)
    addFunction(decls, new FunctionImpl("generate-id", XPathType.STRING,
            new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)));

    // object system-property(string)
    addFunction(decls, new FunctionImpl("system-property", XPathType.ANY,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

    // boolean element-available(string)
    addFunction(decls, new FunctionImpl("element-available", XPathType.BOOLEAN,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

    // boolean function-available(string)
    addFunction(decls, new FunctionImpl("function-available", XPathType.BOOLEAN,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

    // node-set current()
    addFunction(decls, new FunctionImpl("current", XPathType.NODESET));

    XSLT_FUNCTIONS = Collections.unmodifiableMap(decls);
  }

  public XsltFunctionContext() {
    super(XsltContextProvider.TYPE);
  }

  @Override
  protected Map<Pair<QName, Integer>, Function> createFunctionMap(ContextType contextType) {
    return new CompositeMap(XSLT_FUNCTIONS, super.createFunctionMap(contextType));
  }

  public boolean allowsExtensions() {
    return true;
  }

  public static FunctionContext getInstance() {
    return getInstance(XsltContextProvider.TYPE, FACTORY);
  }
}
