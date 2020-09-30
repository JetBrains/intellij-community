/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.xml.psi.XmlPsiBundle;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.compact.RncElementTypes;

import static org.intellij.plugins.relaxNG.compact.RncTokenTypes.*;

public abstract class DeclarationParsing extends AbstractParsing {

  public DeclarationParsing(PsiBuilder builder) {
    super(builder);
  }

  protected void parseTopLevel() {
    while (!myBuilder.eof() && LA_DECL.contains(currentToken())) {
      parseDecl(myBuilder);
    }

    if (LA_GRAMMAR_CONTENT.contains(currentToken())) {
      final PsiBuilder.Marker marker = myBuilder.mark();
      parseGrammarContents();
      marker.done(RncElementTypes.GRAMMAR_PATTERN);
    } else if (currentToken() == KEYWORD_GRAMMAR) {
      parsePattern();
    } else {
      final PsiBuilder.Marker marker = myBuilder.mark();
      while (!myBuilder.eof()) {
        if (!parsePattern()) {
          break;
        }
      }
      marker.done(RncElementTypes.GRAMMAR_PATTERN);
    }
  }

  protected abstract boolean parsePattern();

  protected void parseGrammarContents() {
    while (LA_GRAMMAR_CONTENT.contains(currentToken())) {
      parseGrammarContent(true);
    }
  }

  private void parseIncludeContents() {
    while (LA_INCLUDE_CONTENT.contains(currentToken())) {
      parseGrammarContent(false);
    }
  }

  private void parseGrammarContent(boolean allowInclude) {
    final IElementType t = currentToken();
    if (t == KEYWORD_START) {
      parseStart();
    } else if (t == KEYWORD_DIV) {
      parseDiv();
    } else if (allowInclude && t == KEYWORD_INCLUDE) {
      parseInclude();
    } else if (IDENTIFIERS.contains(t)) {
      parseDefine();
    } else {
      error(XmlPsiBundle.message("xml.parsing.unexpected.token"));
      advance();
    }
  }

  private void parseDefine() {
    final PsiBuilder.Marker marker = begin();
    match(ASSIGN_METHOD, RelaxngBundle.message("relaxng.parse.error.equals-orequals-or-andequals-expected"));
    if (!parsePattern()) {
      error(RelaxngBundle.message("relaxng.parse.error.pattern-expected"));
    }
    marker.done(RncElementTypes.DEFINE);
  }

  private void parseInclude() {
    final PsiBuilder.Marker marker = begin();

    parseAnyUriLiteral();

    parseInherit();

    if (matches(LBRACE)) {
      parseIncludeContents();
      match(RBRACE, RelaxngBundle.message("relaxng.parse.error.rbrace-expected"));
    }
    marker.done(RncElementTypes.INCLUDE);
  }

  protected final void parseAnyUriLiteral() {
    match(LITERAL, RelaxngBundle.message("relaxng.parse.error.uri-literal-expected"));
  }

  protected final void parseInherit() {
    if (matches(KEYWORD_INHERIT)) {
      match(EQ, RelaxngBundle.message("relaxng.parse.error.equals-expected"));
      match(IDENTIFIER_OR_KEYWORD, RelaxngBundle.message("relaxng.parse.error.identifier-expected"));
    }
  }

  private void parseDiv() {
    final PsiBuilder.Marker marker = begin();
    parseBracedGrammarContents();
    marker.done(RncElementTypes.DIV);
  }

  private void parseStart() {
    final PsiBuilder.Marker marker = begin();
    match(ASSIGN_METHOD, RelaxngBundle.message("relaxng.parse.error.equals-orequals-or-andequals-expected"));
    if (!parsePattern()) {
      error(RelaxngBundle.message("relaxng.parse.error.pattern-expected"));
    }
    marker.done(RncElementTypes.START);
  }

  protected void parseBracedGrammarContents() {
    match(LBRACE, RelaxngBundle.message("relaxng.parse.error.lbrace-expected"));
    parseGrammarContents();
    match(RBRACE, RelaxngBundle.message("relaxng.parse.error.rbrace-expected"));
  }

  protected void parseDecl(PsiBuilder builder) {
    final IElementType t = builder.getTokenType();
    if (t == KEYWORD_NAMESPACE) {
      parseNamespaceDecl(false);
    } else if (t == KEYWORD_DEFAULT) {
      parseNamespaceDecl(true);
    } else if (t == KEYWORD_DATATYPES) {
      parseDataTypesDecl();
    }
  }

  private void parseDataTypesDecl() {
    final PsiBuilder.Marker marker = begin();
    match(IDENTIFIER_OR_KEYWORD, RelaxngBundle.message("relaxng.parse.error.identifier-expected"));
    match(EQ, RelaxngBundle.message("relaxng.parse.error.equals-expected"));
    parseNsUriLiteral();
    marker.done(RncElementTypes.DATATYPES_DECL);
  }

  private void parseNamespaceDecl(boolean isDefault) {
    final PsiBuilder.Marker marker = begin();
    if (isDefault) {
      match(KEYWORD_NAMESPACE, RelaxngBundle.message("relaxng.parse.error.namespace-expected"));
      matches(IDENTIFIER_OR_KEYWORD);
    } else {
      match(IDENTIFIER_OR_KEYWORD, RelaxngBundle.message("relaxng.parse.error.identifier-expected"));
    }
    match(EQ, RelaxngBundle.message("relaxng.parse.error.equals-expected"));
    parseNsUriLiteral();
    marker.done(RncElementTypes.NS_DECL);
  }

  private void parseNsUriLiteral() {
    match(NS_URI_LITERAL, RelaxngBundle.message("relaxng.parse.error.namespace-uri-or-inherit-expected"));
  }
}
