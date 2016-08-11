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
package org.intellij.lang.xpath.context.functions;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import org.intellij.lang.xpath.context.ContextType;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.psi.XPathType;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultFunctionContext extends AbstractFunctionContext {
  private static final Map<Pair<QName, Integer>, Function> DEFAULT_FUNCTIONS_V1;
  private static final Map<Pair<QName, Integer>, Function> DEFAULT_FUNCTIONS_V2;

  public DefaultFunctionContext(ContextType contextType) {
    super(contextType);
  }

  static {

    // XPath 1.0

    final Map<Pair<QName, Integer>, Function> decls1 = new HashMap<>();

    addFunction(decls1, new FunctionImpl("last", XPathType.NUMBER));
    addFunction(decls1, new FunctionImpl("position", XPathType.NUMBER));
    addFunction(decls1, new FunctionImpl("count", XPathType.NUMBER,
            new Parameter(XPathType.NODESET, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("id", XPathType.NODESET,
            new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("local-name", XPathType.STRING,
            new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)));
    addFunction(decls1, new FunctionImpl("namespace-uri", XPathType.STRING,
            new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)));
    addFunction(decls1, new FunctionImpl("name", XPathType.STRING,
            new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)));

    addFunction(decls1, new FunctionImpl("string", XPathType.STRING,
            new Parameter(XPathType.ANY, Parameter.Kind.OPTIONAL)));
    addFunction(decls1, new FunctionImpl("concat", XPathType.STRING,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.VARARG)));
    addFunction(decls1, new FunctionImpl("starts-with", XPathType.BOOLEAN,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("contains", XPathType.BOOLEAN,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("substring-before", XPathType.STRING,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("substring-after", XPathType.STRING,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("substring", XPathType.STRING,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.NUMBER, Parameter.Kind.OPTIONAL)));
    addFunction(decls1, new FunctionImpl("string-length", XPathType.NUMBER,
            new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL)));
    addFunction(decls1, new FunctionImpl("normalize-space", XPathType.STRING,
            new Parameter(XPathType.STRING, Parameter.Kind.OPTIONAL)));
    addFunction(decls1, new FunctionImpl("translate", XPathType.STRING,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

    addFunction(decls1, new FunctionImpl("boolean", XPathType.BOOLEAN,
            new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("not", XPathType.BOOLEAN,
            new Parameter(XPathType.BOOLEAN, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("true", XPathType.BOOLEAN));
    addFunction(decls1, new FunctionImpl("false", XPathType.BOOLEAN));
    addFunction(decls1, new FunctionImpl("lang", XPathType.BOOLEAN,
            new Parameter(XPathType.STRING, Parameter.Kind.REQUIRED)));

    addFunction(decls1, new FunctionImpl("number", XPathType.NUMBER,
            new Parameter(XPathType.ANY, Parameter.Kind.OPTIONAL)));
    addFunction(decls1, new FunctionImpl("sum", XPathType.NUMBER,
            new Parameter(XPathType.NODESET, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("floor", XPathType.NUMBER,
            new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("ceiling", XPathType.NUMBER,
            new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED)));
    addFunction(decls1, new FunctionImpl("round", XPathType.NUMBER,
            new Parameter(XPathType.NUMBER, Parameter.Kind.REQUIRED)));

    // XSLT 1.0
    addFunction(decls1, new FunctionImpl("document", XPathType.NODESET,
            new Parameter(XPathType.ANY, Parameter.Kind.REQUIRED),
            new Parameter(XPathType.NODESET, Parameter.Kind.OPTIONAL)));

    DEFAULT_FUNCTIONS_V1 = Collections.unmodifiableMap(decls1);

    // XPath 2.0

    final Map<Pair<QName, Integer>, Function> decls2 = new HashMap<>();

    addFunction(decls2, "fn:base-uri() as xs:anyURI?");
    addFunction(decls2, "fn:base-uri($arg as node()?) as xs:anyURI?");

    addFunction(decls2, "fn:node-name($arg as node()?) as xs:QName?");
    addFunction(decls2, "fn:nilled($arg as node()?) as xs:boolean?");
    addFunction(decls2, "fn:string() as xs:string");
    addFunction(decls2, "fn:string($arg as item()?) as xs:string");
    addFunction(decls2, "fn:data($arg as item()*) as xs:anyAtomicType* ");
    addFunction(decls2, "fn:document-uri($arg as node()?) as xs:anyURI? ");

    addFunction(decls2, "fn:abs($arg as numeric?) as numeric? ");
    addFunction(decls2, "fn:ceiling($arg as numeric?) as numeric? ");
    addFunction(decls2, "fn:floor($arg as numeric?) as numeric? ");
    addFunction(decls2, "fn:round($arg as numeric?) as numeric?");
    addFunction(decls2, "fn:round-half-to-even($arg as numeric?) as numeric? ");
    addFunction(decls2, "fn:round-half-to-even($arg as numeric?, $precision as xs:integer) as numeric?");

    addFunction(decls2, "fn:error() as none");
    addFunction(decls2, "fn:error($error as xs:QName) as none");
    addFunction(decls2, "fn:error($error as xs:QName?, $description as xs:string) as none");
    addFunction(decls2, "fn:error($error as xs:QName?, $description as xs:string, $error-object as item()*) as none ");
    addFunction(decls2, "fn:codepoints-to-string($arg as xs:integer*) as xs:string");
    addFunction(decls2, "fn:string-to-codepoints($arg as xs:string?) as xs:integer* ");

    addFunction(decls2, "fn:trace($value as item()*, $label as xs:string) as item()*");

    addFunction(decls2, "fn:dateTime($arg1 as xs:date?, $arg2 as xs:time?) as xs:dateTime?");

    addFunction(decls2, "fn:compare($comparand1 as xs:string?, $comparand2 as xs:string?) as xs:integer?");
    addFunction(decls2, "fn:compare($comparand1 as xs:string?, $comparand2 as xs:string?, $collation as xs:string) as xs:integer? ");
    addFunction(decls2, "fn:codepoint-equal($comparand1 as xs:string?, $comparand2 as xs:string?) as xs:boolean?");
    addFunction(decls2, "fn:concat($arg1 as xs:anyAtomicType?, $arg2 as xs:anyAtomicType?, ...) as xs:string ");
    addFunction(decls2, "fn:string-join($arg1 as xs:string*, $arg2 as xs:string) as xs:string");
    addFunction(decls2, "fn:substring($sourceString as xs:string?, $startingLoc as xs:double) as xs:string");
    addFunction(decls2, "fn:substring($sourceString as xs:string?, $startingLoc as xs:double, $length as xs:double) as xs:string");
    addFunction(decls2, "fn:string-length() as xs:integer");
    addFunction(decls2, "fn:string-length($arg as xs:string?) as xs:integer");
    addFunction(decls2, "fn:normalize-space() as xs:string");
    addFunction(decls2, "fn:normalize-space($arg as xs:string?) as xs:string ");
    addFunction(decls2, "fn:normalize-unicode($arg as xs:string?) as xs:string ");
    addFunction(decls2, "fn:normalize-unicode($arg as xs:string?, $normalizationForm as xs:string) as xs:string");
    addFunction(decls2, "fn:upper-case($arg as xs:string?) as xs:string");
    addFunction(decls2, "fn:lower-case($arg as xs:string?) as xs:string");
    addFunction(decls2, "fn:translate($arg as xs:string?, $mapString as xs:string, $transString as xs:string) as xs:string");
    addFunction(decls2, "fn:encode-for-uri($uri-part as xs:string?) as xs:string");
    addFunction(decls2, "fn:iri-to-uri($iri as xs:string?) as xs:string");
    addFunction(decls2, "fn:escape-html-uri($uri as xs:string?) as xs:string ");
    addFunction(decls2, "fn:contains($arg1 as xs:string?, $arg2 as xs:string?) as xs:boolean");
    addFunction(decls2, "fn:contains($arg1 as xs:string?, $arg2 as xs:string?, $collation as xs:string) as xs:boolean");
    addFunction(decls2, "fn:starts-with($arg1 as xs:string?, $arg2 as xs:string?) as xs:boolean ");
    addFunction(decls2, "fn:starts-with($arg1 as xs:string?, $arg2 as xs:string?, $collation as xs:string) as xs:boolean ");
    addFunction(decls2, "fn:ends-with($arg1 as xs:string?, $arg2 as xs:string?) as xs:boolean ");
    addFunction(decls2, "fn:ends-with($arg1 as xs:string?, $arg2 as xs:string?, $collation as xs:string) as xs:boolean");
    addFunction(decls2, "fn:substring-before($arg1 as xs:string?, $arg2 as xs:string?) as xs:string");
    addFunction(decls2, "fn:substring-before($arg1 as xs:string?, $arg2 as xs:string?, $collation as xs:string) as xs:string");
    addFunction(decls2, "fn:substring-after($arg1 as xs:string?, $arg2 as xs:string?) as xs:string ");
    addFunction(decls2, "fn:substring-after($arg1 as xs:string?, $arg2 as xs:string?, $collation as xs:string) as xs:string ");
    addFunction(decls2, "fn:matches($input as xs:string?, $pattern as xs:string) as xs:boolean");
    addFunction(decls2, "fn:matches($input as xs:string?, $pattern as xs:string, $flags as xs:string) as xs:boolean");
    addFunction(decls2, "fn:replace($input as xs:string?, $pattern as xs:string, $replacement as xs:string) as xs:string");
    addFunction(decls2, "fn:replace($input as xs:string?, $pattern as xs:string, $replacement as xs:string, $flags as xs:string) as xs:string");
    addFunction(decls2, "fn:tokenize($input as xs:string?, $pattern as xs:string) as xs:string* ");
    addFunction(decls2, "fn:tokenize($input as xs:string?, $pattern as xs:string, $flags as xs:string) as xs:string* ");

    addFunction(decls2, "fn:resolve-uri($relative as xs:string?) as xs:anyURI? ");
    addFunction(decls2, "fn:resolve-uri($relative as xs:string?, $base as xs:string) as xs:anyURI?");

    addFunction(decls2, "fn:adjust-dateTime-to-timezone($arg as xs:dateTime?) as xs:dateTime?");
    addFunction(decls2, "fn:adjust-dateTime-to-timezone($arg as xs:dateTime?, $timezone as xs:dayTimeDuration?) as xs:dateTime?");
    addFunction(decls2, "fn:adjust-date-to-timezone($arg as xs:date?) as xs:date? ");
    addFunction(decls2, "fn:adjust-date-to-timezone($arg as xs:date?, $timezone as xs:dayTimeDuration?) as xs:date? ");
    addFunction(decls2, "fn:adjust-time-to-timezone($arg as xs:time?) as xs:time? ");
    addFunction(decls2, "fn:adjust-time-to-timezone($arg as xs:time?, $timezone as xs:dayTimeDuration?) as xs:time?");
    addFunction(decls2, "fn:true() as xs:boolean");
    addFunction(decls2, "fn:false() as xs:boolean");

    addFunction(decls2, "fn:not($arg as item()*) as xs:boolean");

    addFunction(decls2, "fn:years-from-duration($arg as xs:duration?) as xs:integer?");
    addFunction(decls2, "fn:months-from-duration($arg as xs:duration?) as xs:integer?");
    addFunction(decls2, "fn:days-from-duration($arg as xs:duration?) as xs:integer?");
    addFunction(decls2, "fn:hours-from-duration($arg as xs:duration?) as xs:integer?");
    addFunction(decls2, "fn:minutes-from-duration($arg as xs:duration?) as xs:integer? ");
    addFunction(decls2, "fn:seconds-from-duration($arg as xs:duration?) as xs:decimal? ");
    addFunction(decls2, "fn:year-from-dateTime($arg as xs:dateTime?) as xs:integer?");
    addFunction(decls2, "fn:month-from-dateTime($arg as xs:dateTime?) as xs:integer? ");
    addFunction(decls2, "fn:day-from-dateTime($arg as xs:dateTime?) as xs:integer? ");
    addFunction(decls2, "fn:hours-from-dateTime($arg as xs:dateTime?) as xs:integer? ");
    addFunction(decls2, "fn:minutes-from-dateTime($arg as xs:dateTime?) as xs:integer? ");
    addFunction(decls2, "fn:seconds-from-dateTime($arg as xs:dateTime?) as xs:decimal?");
    addFunction(decls2, "fn:timezone-from-dateTime($arg as xs:dateTime?) as xs:dayTimeDuration?");
    addFunction(decls2, "fn:year-from-date($arg as xs:date?) as xs:integer? ");
    addFunction(decls2, "fn:month-from-date($arg as xs:date?) as xs:integer?");
    addFunction(decls2, "fn:day-from-date($arg as xs:date?) as xs:integer?");
    addFunction(decls2, "fn:timezone-from-date($arg as xs:date?) as xs:dayTimeDuration? ");
    addFunction(decls2, "fn:hours-from-time($arg as xs:time?) as xs:integer? ");
    addFunction(decls2, "fn:minutes-from-time($arg as xs:time?) as xs:integer? ");
    addFunction(decls2, "fn:seconds-from-time($arg as xs:time?) as xs:decimal? ");
    addFunction(decls2, "fn:timezone-from-time($arg as xs:time?) as xs:dayTimeDuration? ");

    addFunction(decls2, "fn:resolve-QName($qname as xs:string?, $element as element()) as xs:QName?");
    addFunction(decls2, "fn:QName($paramURI as xs:string?, $paramQName as xs:string) as xs:QName");

    addFunction(decls2, "fn:prefix-from-QName($arg as xs:QName?) as xs:NCName? ");
    addFunction(decls2, "fn:local-name-from-QName($arg as xs:QName?) as xs:NCName? ");
    addFunction(decls2, "fn:namespace-uri-from-QName($arg as xs:QName?) as xs:anyURI? ");
    addFunction(decls2, "fn:namespace-uri-for-prefix($prefix as xs:string?, $element as element()) as xs:anyURI?");
    addFunction(decls2, "fn:in-scope-prefixes($element as element()) as xs:string* ");

    addFunction(decls2, "fn:name() as xs:string ");
    addFunction(decls2, "fn:name($arg as node()?) as xs:string ");
    addFunction(decls2, "fn:local-name() as xs:string ");
    addFunction(decls2, "fn:local-name($arg as node()?) as xs:string");
    addFunction(decls2, "fn:namespace-uri() as xs:anyURI ");
    addFunction(decls2, "fn:namespace-uri($arg as node()?) as xs:anyURI");
    addFunction(decls2, "fn:number() as xs:double");
    addFunction(decls2, "fn:number($arg as xs:anyAtomicType?) as xs:double");
    addFunction(decls2, "fn:lang($testlang as xs:string?) as xs:boolean ");
    addFunction(decls2, "fn:lang($testlang as xs:string?, $node as node()) as xs:boolean");
    addFunction(decls2, "fn:root() as node() ");
    addFunction(decls2, "fn:root($arg as node()?) as node()? ");

    addFunction(decls2, "fn:boolean($arg as item()*) as xs:boolean ");
    addFunction(decls2, "fn:index-of($seqParam as xs:anyAtomicType*, $srchParam as xs:anyAtomicType) as xs:integer*");
    addFunction(decls2, "fn:index-of($seqParam as xs:anyAtomicType*, $srchParam as xs:anyAtomicType, $collation as xs:string) as xs:integer*");
    addFunction(decls2, "fn:empty($arg as item()*) as xs:boolean ");
    addFunction(decls2, "fn:exists($arg as item()*) as xs:boolean");
    addFunction(decls2, "fn:distinct-values($arg as xs:anyAtomicType*) as xs:anyAtomicType*");
    addFunction(decls2, "fn:distinct-values($arg as xs:anyAtomicType*, $collation as xs:string) as xs:anyAtomicType*");
    addFunction(decls2, "fn:insert-before($target as item()*, $position as xs:integer, $inserts as item()*) as item()*");
    addFunction(decls2, "fn:remove($target as item()*, $position as xs:integer) as item()*");
    addFunction(decls2, "fn:reverse($arg as item()*) as item()*");
    addFunction(decls2, "fn:subsequence($sourceSeq as item()*, $startingLoc as xs:double) as item()*");
    addFunction(decls2, "fn:subsequence($sourceSeq as item()*, $startingLoc as xs:double, $length as xs:double) as item()*");
    addFunction(decls2, "fn:unordered($sourceSeq as item()*) as item()*");

    addFunction(decls2, "fn:zero-or-one($arg as item()*) as item()?");
    addFunction(decls2, "fn:one-or-more($arg as item()*) as item()+");
    addFunction(decls2, "fn:exactly-one($arg as item()*) as item()");

    addFunction(decls2, "fn:deep-equal($parameter1 as item()*, $parameter2 as item()*) as xs:boolean");
    addFunction(decls2, "fn:deep-equal($parameter1 as item()*, $parameter2 as item()*, $collation as string) as xs:boolean");

    addFunction(decls2, "fn:count($arg as item()*) as xs:integer");
    addFunction(decls2, "fn:avg($arg as xs:anyAtomicType*) as xs:anyAtomicType? ");
    addFunction(decls2, "fn:max($arg as xs:anyAtomicType*) as xs:anyAtomicType?");
    addFunction(decls2, "fn:max($arg as xs:anyAtomicType*, $collation as string) as xs:anyAtomicType? ");
    addFunction(decls2, "fn:min($arg as xs:anyAtomicType*) as xs:anyAtomicType?");
    addFunction(decls2, "fn:min($arg as xs:anyAtomicType*, $collation as string) as xs:anyAtomicType?");
    addFunction(decls2, "fn:sum($arg as xs:anyAtomicType*) as xs:anyAtomicType ");
    addFunction(decls2, "fn:sum($arg as xs:anyAtomicType*, $zero as xs:anyAtomicType?) as xs:anyAtomicType? ");

    addFunction(decls2, "fn:id($arg as xs:string*) as element()*");
    addFunction(decls2, "fn:id($arg as xs:string*, $node as node()) as element()*");
    addFunction(decls2, "fn:idref($arg as xs:string*) as node()*");
    addFunction(decls2, "fn:idref($arg as xs:string*, $node as node()) as node()* ");
    addFunction(decls2, "fn:doc($uri as xs:string?) as document-node()?");
    addFunction(decls2, "fn:doc-available($uri as xs:string?) as xs:boolean ");
    addFunction(decls2, "fn:collection() as node()*");
    addFunction(decls2, "fn:collection($arg as xs:string?) as node()*");
    addFunction(decls2, "fn:element-with-id($arg as xs:string*) as element()*");
    addFunction(decls2, "fn:element-with-id($arg as xs:string*, $node as node()) as element()* ");

    addFunction(decls2, "fn:position() as xs:integer");
    addFunction(decls2, "fn:last() as xs:integer");
    addFunction(decls2, "fn:current-dateTime() as xs:dateTime");
    addFunction(decls2, "fn:current-date() as xs:date");
    addFunction(decls2, "fn:current-time() as xs:time ");
    addFunction(decls2, "fn:implicit-timezone() as xs:dayTimeDuration");
    addFunction(decls2, "fn:default-collation() as xs:string");
    addFunction(decls2, "fn:static-base-uri() as xs:anyURI? ");

    // constructor functions

    addFunction(decls2, "xs:string($arg as xs:anyAtomicType?) as xs:string?");
    addFunction(decls2, "xs:date($arg as xs:anyAtomicType?) as xs:date?");
    addFunction(decls2, "xs:boolean($arg as xs:anyAtomicType?) as xs:boolean?");
    addFunction(decls2, "xs:decimal($arg as xs:anyAtomicType?) as xs:decimal?");
    addFunction(decls2, "xs:float($arg as xs:anyAtomicType?) as xs:float?");
    addFunction(decls2, "xs:double($arg as xs:anyAtomicType?) as xs:double?");
    addFunction(decls2, "xs:duration($arg as xs:anyAtomicType?) as xs:duration?");
    addFunction(decls2, "xs:dateTime($arg as xs:anyAtomicType?) as xs:dateTime?");
    addFunction(decls2, "xs:time($arg as xs:anyAtomicType?) as xs:time?");
    addFunction(decls2, "xs:gYearMonth($arg as xs:anyAtomicType?) as xs:gYearMonth?");
    addFunction(decls2, "xs:gYear($arg as xs:anyAtomicType?) as xs:gYear?");
    addFunction(decls2, "xs:gMonthDay($arg as xs:anyAtomicType?) as xs:gMonthDay?");
    addFunction(decls2, "xs:gDay($arg as xs:anyAtomicType?) as xs:gDay?");
    addFunction(decls2, "xs:gMonth($arg as xs:anyAtomicType?) as xs:gMonth?");
    addFunction(decls2, "xs:hexBinary($arg as xs:anyAtomicType?) as xs:hexBinary?");
    addFunction(decls2, "xs:base64Binary($arg as xs:anyAtomicType?) as xs:base64Binary?");
    addFunction(decls2, "xs:anyURI($arg as xs:anyAtomicType?) as xs:anyURI?");
    addFunction(decls2, "xs:QName($arg as xs:anyAtomicType) as xs:QName?");
    addFunction(decls2, "xs:normalizedString($arg as xs:anyAtomicType?) as xs:normalizedString?");
    addFunction(decls2, "xs:token($arg as xs:anyAtomicType?) as xs:token?");
    addFunction(decls2, "xs:language($arg as xs:anyAtomicType?) as xs:language?");
    addFunction(decls2, "xs:NMTOKEN($arg as xs:anyAtomicType?) as xs:NMTOKEN?");
    addFunction(decls2, "xs:Name($arg as xs:anyAtomicType?) as xs:Name?");
    addFunction(decls2, "xs:NCName($arg as xs:anyAtomicType?) as xs:NCName?");
    addFunction(decls2, "xs:ID($arg as xs:anyAtomicType?) as xs:ID?");
    addFunction(decls2, "xs:IDREF($arg as xs:anyAtomicType?) as xs:IDREF?");
    addFunction(decls2, "xs:ENTITY($arg as xs:anyAtomicType?) as xs:ENTITY?");
    addFunction(decls2, "xs:integer($arg as xs:anyAtomicType?) as xs:integer?");
    addFunction(decls2, "xs:nonPositiveInteger($arg as xs:anyAtomicType?) as xs:nonPositiveInteger?");
    addFunction(decls2, "xs:negativeInteger($arg as xs:anyAtomicType?) as xs:negativeInteger?");
    addFunction(decls2, "xs:long($arg as xs:anyAtomicType?) as xs:long?");
    addFunction(decls2, "xs:int($arg as xs:anyAtomicType?) as xs:int?");
    addFunction(decls2, "xs:short($arg as xs:anyAtomicType?) as xs:short?");
    addFunction(decls2, "xs:byte($arg as xs:anyAtomicType?) as xs:byte?");
    addFunction(decls2, "xs:nonNegativeInteger($arg as xs:anyAtomicType?) as xs:nonNegativeInteger?");
    addFunction(decls2, "xs:unsignedLong($arg as xs:anyAtomicType?) as xs:unsignedLong?");
    addFunction(decls2, "xs:unsignedInt($arg as xs:anyAtomicType?) as xs:unsignedInt?");
    addFunction(decls2, "xs:unsignedShort($arg as xs:anyAtomicType?) as xs:unsignedShort?");
    addFunction(decls2, "xs:unsignedByte($arg as xs:anyAtomicType?) as xs:unsignedByte?");
    addFunction(decls2, "xs:positiveInteger($arg as xs:anyAtomicType?) as xs:positiveInteger?");
    addFunction(decls2, "xs:yearMonthDuration($arg as xs:anyAtomicType?) as xs:yearMonthDuration?");
    addFunction(decls2, "xs:dayTimeDuration($arg as xs:anyAtomicType?) as xs:dayTimeDuration?");
    addFunction(decls2, "xs:untypedAtomic($arg as xs:anyAtomicType?) as xs:untypedAtomic?");

    DEFAULT_FUNCTIONS_V2 = Collections.unmodifiableMap(decls2);
  }

  @Override
  protected Map<Pair<QName, Integer>, Function> createFunctionMap(ContextType type) {
    final XPathVersion version = type.getVersion();
    if (version == XPathVersion.V1) {
      return DEFAULT_FUNCTIONS_V1;
    } else if (version == XPathVersion.V2) {
      return DEFAULT_FUNCTIONS_V2;
    } else {
      throw new IllegalStateException("Unsupprted version: " + version);
    }
  }

  public static void addFunction(Map<Pair<QName, Integer>, Function> decls, String value) {
    FunctionDeclarationParsing.addFunction(decls, value);
  }

  public static void addFunction(Map<Pair<QName, Integer>, Function> decls, Function value) {
    decls.put(Pair.create(new QName(null, value.getName()), value.getParameters().length), value);
  }

  public static void addFunction(Map<Pair<QName, Integer>, Function> decls, String namespace, Function value) {
    decls.put(Pair.create(new QName(namespace, value.getName()), value.getParameters().length), value);
  }

  public boolean allowsExtensions() {
    return false;
  }

  public static FunctionContext getInstance(final ContextType type) {
    return AbstractFunctionContext.getInstance(type, () -> new DefaultFunctionContext(type));
  }
}
