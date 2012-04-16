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

  public static final String EXSLT_DATE_TIME = "http://exslt.org/dates-and-times";
  public static final String EXSLT_COMMON = "http://exslt.org/common";
  public static final String EXSLT_MATH = "http://exslt.org/math";
  public static final String EXSLT_SETS = "http://exslt.org/sets";
  public static final String EXSLT_DYNAMIC = "http://exslt.org/dynamic";

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

    final Parameter optional_string = new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL);
    final Parameter required_nodeset = new Parameter(XPathType.NODESET, Parameter.Kind.REQUIRED);

    // EXSLT (http://www.exslt.org) extensions supported by Xalan & Saxon
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("date", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("date-time", XPathType.STRING));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-abbreviation", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-in-month", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-in-week", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-in-year", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-name", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("day-of-week-in-month", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("hour-in-day", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("leap-year", XPathType.BOOLEAN, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("minute-in-hour", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("month-abbreviation", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("month-in-year", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("month-name", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("second-in-minute", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("time", XPathType.STRING, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("week-in-year", XPathType.NUMBER, optional_string));
    addFunction(decls, EXSLT_DATE_TIME, new FunctionImpl("year", XPathType.NUMBER, optional_string));

    addFunction(decls, EXSLT_COMMON, new FunctionImpl("node-set", XPathType.NODESET, new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED)));
    addFunction(decls, EXSLT_COMMON, new FunctionImpl("object-type", XPathType.STRING, new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED)));

    addFunction(decls, EXSLT_MATH, new FunctionImpl("highest", XPathType.NODESET, required_nodeset));
    addFunction(decls, EXSLT_MATH, new FunctionImpl("lowest", XPathType.NODESET, required_nodeset));
    addFunction(decls, EXSLT_MATH, new FunctionImpl("max", XPathType.NUMBER, required_nodeset));
    addFunction(decls, EXSLT_MATH, new FunctionImpl("min", XPathType.NUMBER, required_nodeset));

    addFunction(decls, EXSLT_SETS, new FunctionImpl("difference", XPathType.NODESET,
                                                   required_nodeset,
                                                   required_nodeset));
    addFunction(decls, EXSLT_SETS, new FunctionImpl("intersection", XPathType.NODESET,
                                                   required_nodeset,
                                                   required_nodeset));
    addFunction(decls, EXSLT_SETS, new FunctionImpl("leading", XPathType.NODESET,
                                                   required_nodeset,
                                                   required_nodeset));
    addFunction(decls, EXSLT_SETS, new FunctionImpl("trailing", XPathType.NODESET,
                                                   required_nodeset,
                                                   required_nodeset));
    addFunction(decls, EXSLT_SETS, new FunctionImpl("has-same-node", XPathType.BOOLEAN,
                                                   required_nodeset,
                                                   required_nodeset));
    addFunction(decls, EXSLT_SETS, new FunctionImpl("distinct", XPathType.NODESET,
                                                   required_nodeset));

    // Xalan only
    addFunction(decls, EXSLT_DYNAMIC, new FunctionImpl("evaluate", XPathType.ANY, new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));


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
