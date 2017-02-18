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
package org.intellij.lang.xpath.xslt.context;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.functions.*;
import org.intellij.lang.xpath.psi.XPath2Type;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.intellij.lang.xpath.xslt.context.XsltFunctionContext.SAXON_7;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 08.01.11
*/
public class Xslt2FunctionContext extends DefaultFunctionContext {

  protected static final Map<Pair<QName, Integer>, Function> XSLT2_FUNCTIONS;

  private static final Factory<FunctionContext> FACTORY = () -> new Xslt2FunctionContext();

  protected Xslt2FunctionContext() {
    super(Xslt2ContextProvider.TYPE);
  }

  static {
    final Map<Pair<QName, Integer>, Function> decls = new HashMap<>();

    // xslt 2.0

    FunctionDeclarationParsing.addFunction(decls, "function-available($function-name as xs:string) as xs:boolean ");
    FunctionDeclarationParsing.addFunction(decls, "function-available($function-name as xs:string, $arity as xs:integer) as xs:boolean");

    FunctionDeclarationParsing.addFunction(decls, "type-available($type-name as xs:string) as xs:boolean ");
    FunctionDeclarationParsing.addFunction(decls, "element-available($element-name as xs:string) as xs:boolean ");

    FunctionDeclarationParsing.addFunction(decls, "current() as item() ");
    FunctionDeclarationParsing.addFunction(decls, "current-group() as item()* ");
    FunctionDeclarationParsing.addFunction(decls, "current-grouping-key() as xs:anyAtomicType? ");

    FunctionDeclarationParsing.addFunction(decls, "regex-group($group-number as xs:integer) as xs:string ");

    FunctionDeclarationParsing.addFunction(decls, "unparsed-entity-uri($entity-name as xs:string) as xs:anyURI ");
    FunctionDeclarationParsing.addFunction(decls, "unparsed-entity-public-id($entity-name as xs:string) as xs:string ");

    FunctionDeclarationParsing.addFunction(decls, "generate-id() as xs:string ");
    FunctionDeclarationParsing.addFunction(decls, "generate-id($node as node()?) as xs:string ");

    FunctionDeclarationParsing.addFunction(decls, "system-property($property-name as xs:string) as xs:string ");

    FunctionDeclarationParsing.addFunction(decls, "document($uri-sequence as item()*) as node()*");
    FunctionDeclarationParsing.addFunction(decls, "document($uri-sequence as item()*, $base-node as node()) as node()*");

    FunctionDeclarationParsing.addFunction(decls, "unparsed-text($href as xs:string?) as xs:string?");
    FunctionDeclarationParsing.addFunction(decls, "unparsed-text($href as xs:string?, $encoding as xs:string) as xs:string?");

    FunctionDeclarationParsing.addFunction(decls, "unparsed-text-available($href as xs:string?) as xs:boolean");
    FunctionDeclarationParsing.addFunction(decls, "unparsed-text-available($href as xs:string?, $encoding as xs:string?) as xs:boolean");

    FunctionDeclarationParsing.addFunction(decls, "key($key-name as xs:string, $key-value as xs:anyAtomicType*) as node()* ");
    FunctionDeclarationParsing.addFunction(decls, "key($key-name as xs:string, $key-value as xs:anyAtomicType*, $top as node()) as node()*");

    FunctionDeclarationParsing.addFunction(decls, "format-number($value as numeric?, $picture as xs:string) as xs:string ");
    FunctionDeclarationParsing.addFunction(decls, "format-number($value as numeric?, $picture as xs:string, $decimal-format-name as xs:string) as xs:string");

    FunctionDeclarationParsing.addFunction(decls, "format-dateTime($value  as xs:dateTime?, $picture as xs:string) as xs:string? ");
    FunctionDeclarationParsing.addFunction(decls, "format-dateTime($value \t as xs:dateTime?,\n" +
            "$picture as xs:string,\n" +
            "$language as xs:string?,\n" +
            "$calendar as xs:string?,\n" +
            "$country as xs:string?) as xs:string?");

    FunctionDeclarationParsing.addFunction(decls, "format-time($value as xs:time?, $picture as xs:string) as xs:string? ");
    FunctionDeclarationParsing.addFunction(decls, "format-time($value \t as xs:time?,\n" +
            "$picture as xs:string,\n" +
            "$language as xs:string?,\n" +
            "$calendar as xs:string?,\n" +
            "$country as xs:string?) as xs:string?");

    FunctionDeclarationParsing.addFunction(decls, "format-date($value as xs:date?, $picture as xs:string) as xs:string? ");
    FunctionDeclarationParsing.addFunction(decls, "format-date($value \t as xs:date?,\n" +
            "$picture as xs:string,\n" +
            "$language as xs:string?,\n" +
            "$calendar as xs:string?,\n" +
            "$country as xs:string?) as xs:string?");

    final Parameter optional_string = new Parameter(XPath2Type.STRING, Parameter.Kind.OPTIONAL);
    final Parameter required_string = new Parameter(XPath2Type.STRING, Parameter.Kind.REQUIRED);
    final Parameter required_nodeset = new Parameter(XPath2Type.NODE_SEQUENCE, Parameter.Kind.REQUIRED);
    final Parameter required_any = new Parameter(XPath2Type.ITEM, Parameter.Kind.REQUIRED);
    final Parameter optional_any = new Parameter(XPath2Type.ITEM, Parameter.Kind.OPTIONAL);
    final Parameter any_list = new Parameter(XPath2Type.ITEM, Parameter.Kind.VARARG);

    addFunction(decls, SAXON_7, new FunctionImpl("dayTimeDuration-from-seconds", XPath2Type.DAYTIMEDURATION, new Parameter(XPath2Type.INTEGER, Parameter.Kind.REQUIRED)));
    addFunction(decls, SAXON_7, new FunctionImpl("yearMonthDuration-from-months", XPath2Type.YEARMONTHDURATION, new Parameter(XPath2Type.INTEGER, Parameter.Kind.REQUIRED)));

    addFunction(decls, SAXON_7, new FunctionImpl("distinct", XPath2Type.NODE_SEQUENCE, required_nodeset, optional_any));
    addFunction(decls, SAXON_7, new FunctionImpl("eval", XPath2Type.ITEM, required_any));
    addFunction(decls, SAXON_7, new FunctionImpl("evaluate", XPath2Type.ITEM, required_string, any_list));
    addFunction(decls, SAXON_7, new FunctionImpl("expression", XPath2Type.ITEM, required_string, any_list));
    addFunction(decls, SAXON_7, new FunctionImpl("get-pseudo-attribute", XPath2Type.ITEM, required_string));
    addFunction(decls, SAXON_7, new FunctionImpl("has-same-nodes", XPath2Type.BOOLEAN, required_nodeset, required_nodeset));
    addFunction(decls, SAXON_7, new FunctionImpl("highest", XPath2Type.NODE_SEQUENCE, required_nodeset, optional_any));
    addFunction(decls, SAXON_7, new FunctionImpl("leading", XPath2Type.NODE_SEQUENCE, required_nodeset, required_any));
    addFunction(decls, SAXON_7, new FunctionImpl("line-number", XPath2Type.INTEGER));
    addFunction(decls, SAXON_7, new FunctionImpl("max", XPath2Type.NUMERIC, required_nodeset, optional_any));
    addFunction(decls, SAXON_7, new FunctionImpl("min", XPath2Type.NUMERIC, required_string));
    addFunction(decls, SAXON_7, new FunctionImpl("parse", XPath2Type.NODE_SEQUENCE, required_nodeset, optional_any));
    addFunction(decls, SAXON_7, new FunctionImpl("path", XPath2Type.STRING));
    addFunction(decls, SAXON_7, new FunctionImpl("serialize", XPath2Type.STRING, required_nodeset, required_string));
    addFunction(decls, SAXON_7, new FunctionImpl("sum", XPath2Type.NUMERIC, required_nodeset, optional_any));
    addFunction(decls, SAXON_7, new FunctionImpl("systemId", XPath2Type.STRING));
    addFunction(decls, SAXON_7, new FunctionImpl("tokenize", XPath2Type.NODE_SEQUENCE, required_string, optional_string));

    XSLT2_FUNCTIONS = Collections.unmodifiableMap(decls);
  }

  @Override
  protected Map<Pair<QName, Integer>, Function> createFunctionMap(ContextType contextType) {
    return ContainerUtil.union(XSLT2_FUNCTIONS, super.createFunctionMap(contextType));
  }

  @Override
  public boolean allowsExtensions() {
    return true;
  }

  @Override
  public Function resolve(QName name, int argCount) {
    if (name.getNamespaceURI().length() == 0) {
      name = new QName(FunctionDeclarationParsing.FUNCTION_NAMESPACE, name.getLocalPart());
    }
    return super.resolve(name, argCount);
  }

  public static FunctionContext getInstance() {
    return getInstance(Xslt2ContextProvider.TYPE, FACTORY);
  }

  public static void main(String[] args) {
  }
}
