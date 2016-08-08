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
package org.intellij.lang.xpath.context.functions;

import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.lang.xpath.XPath2TokenTypes;
import org.intellij.lang.xpath.XPathLexer;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.XPath2SequenceType;
import org.intellij.lang.xpath.psi.XPath2Type;
import org.intellij.lang.xpath.psi.XPathType;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 14.01.11
*/
public class FunctionDeclarationParsing {
  public static final String FUNCTION_NAMESPACE = "http://www.w3.org/2005/xpath-functions";

  private FunctionDeclarationParsing() {
  }

  public static Pair<String, ? extends Function> parseFuntionDeclaration(String decl) {
    final Lexer lexer = new FilterLexer(XPathLexer.create(true),
            new FilterLexer.SetFilter(TokenSet.create(XPathTokenTypes.WHITESPACE)));
    lexer.start(decl);

    String prefix = "";
    if (lexer.getTokenType() == XPathTokenTypes.EXT_PREFIX) {
      prefix = lexer.getTokenText();
      lexer.advance();
      match(lexer, XPathTokenTypes.COL);
    }

    final String name = match(lexer, XPathTokenTypes.FUNCTION_NAME);
    match(lexer, XPathTokenTypes.LPAREN);

    final List<Parameter> parameters = new ArrayList<>();
    while (lexer.getTokenType() != XPathTokenTypes.RPAREN) {
      if (lexer.getTokenType() == XPathTokenTypes.DOTDOT) {
        lexer.advance();
        match(lexer, XPathTokenTypes.DOT);

        parameters.add(new Parameter(XPathType.ANY, Parameter.Kind.VARARG));
      } else {
        match(lexer, XPathTokenTypes.DOLLAR);
        match(lexer, XPathTokenTypes.VARIABLE_NAME);

        match(lexer, XPath2TokenTypes.AS);

        final String type = parseType(lexer);

        final XPath2SequenceType.Cardinality indicator = parseCardinality(lexer);

        parameters.add(new Parameter(mapType(type, indicator), Parameter.Kind.REQUIRED));
      }

      if (lexer.getTokenType() == XPathTokenTypes.COMMA) {
        lexer.advance();
      }
    }
    lexer.advance();

    match(lexer, XPath2TokenTypes.AS);

    final String ret = parseType(lexer);
    final XPath2SequenceType.Cardinality indicator = parseCardinality(lexer);

    final XPathType returnType = mapType(ret, indicator);

    return Pair.create(prefix, new FunctionImpl(name, returnType, parameters.toArray(new Parameter[parameters.size()])));
  }

  public static XPathType mapType(String type, XPath2SequenceType.Cardinality c) {
    if ("none".equals(type)) {
      return XPathType.UNKNOWN;
    }

    XPathType r = null;
    if ("numeric".equals(type)) {
      r = XPath2Type.NUMERIC;
    } else if (type.startsWith("xs:")) {
      final String base = type.substring(3);
      r = XPath2Type.fromName(new QName(XPath2Type.XMLSCHEMA_NS, base));
      if (r == null) {
        r = XPathType.fromString(base);
      }
    } else {
      if (type.endsWith("()")) {
        r = XPath2Type.fromName(new QName("", type));
      }
    }

    if (r != null) {
      if (c != null) {
        r = XPath2SequenceType.create(r, c);
      }
      return r;
    }
    return XPathType.fromString(type);
  }

  @Nullable
  public static XPath2SequenceType.Cardinality parseCardinality(Lexer lexer) {
    if (lexer.getTokenType() == XPath2TokenTypes.QUEST) {
      lexer.advance();
      return XPath2SequenceType.Cardinality.OPTIONAL;
    } else if (lexer.getTokenType() == XPathTokenTypes.MULT || lexer.getTokenType() == XPathTokenTypes.STAR) {
      lexer.advance();
      return XPath2SequenceType.Cardinality.ZERO_OR_MORE;
    } else if (lexer.getTokenType() == XPathTokenTypes.PLUS) {
      lexer.advance();
      return XPath2SequenceType.Cardinality.ONE_OR_MORE;
    }
    return null;
  }

  public static String match(Lexer lexer, IElementType token) {
    assert lexer.getTokenType() == token : lexer.getTokenType() + ": " + lexer.getTokenText();
    final String s = lexer.getTokenText();
    lexer.advance();
    return s;
  }

  @Nullable
  public static String parseType(Lexer lexer) {
    String type = parseQName(lexer);
    if (type == null) {
      if (lexer.getTokenType() == XPath2TokenTypes.ITEM || lexer.getTokenType() == XPathTokenTypes.FUNCTION_NAME || lexer.getTokenType() == XPathTokenTypes.NODE_TYPE) {
        type = lexer.getTokenText();
        lexer.advance();
        match(lexer, XPathTokenTypes.LPAREN);
        match(lexer, XPathTokenTypes.RPAREN);
        type += "()";
      } else {
        assert false : "unexpected token: " + lexer.getTokenType();
      }
    }
    return type;
  }

  @Nullable
  public static String parseQName(Lexer lexer) {
    String name;
    if (lexer.getTokenType() == XPathTokenTypes.NCNAME) {
      name = lexer.getTokenText();
      lexer.advance();
      if (lexer.getTokenType() == XPathTokenTypes.COL) {
        lexer.advance();
        assert lexer.getTokenType() == XPathTokenTypes.NCNAME;
        name += (":" + lexer.getTokenText());
        lexer.advance();
      }
    } else {
      name = null;
    }
    return name;
  }

  public static void addFunction(Map<Pair<QName, Integer>, Function> decls, String s) {
    final Pair<String, ? extends Function> pair = parseFuntionDeclaration(s);
    final Function func = pair.second;

    final boolean fn = pair.first.equals("fn") || pair.first.length() == 0;
    final boolean xs = pair.first.equals("xs");
    final Pair<QName, Integer> key = Pair.create(new QName(fn ? FUNCTION_NAMESPACE : (xs ? XPath2Type.XMLSCHEMA_NS : null), func.getName()), func.getParameters().length);
    assert !decls.containsKey(key) : key;

    decls.put(key, func);

    if (fn) {
      decls.put(Pair.create(new QName(null, func.getName()), func.getParameters().length), func);
    }
  }
}