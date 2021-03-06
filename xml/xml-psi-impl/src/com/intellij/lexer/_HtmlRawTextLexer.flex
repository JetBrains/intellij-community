/* It's an automatically generated code. Do not modify it. */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;

%%

%unicode

%{
  public _HtmlRawTextLexer() {
    this((java.io.Reader)null);
  }
%}

%class _HtmlRawTextLexer
%public
%implements FlexLexer
%function advance
%type IElementType

ALPHA=[:letter:]
DIGIT=[0-9]
WHITE_SPACE_CHARS=[ \n\r\t\f\u2028\u2029\u0085]+

TAG_NAME=({ALPHA}|"_"|":")({ALPHA}|{DIGIT}|"_"|":"|"."|"-")*

%%

{WHITE_SPACE_CHARS} { return XmlTokenType.XML_REAL_WHITE_SPACE; }

"&lt;" |
"&gt;" |
"&apos;" |
"&quot;" |
"&nbsp;" |
"&amp;" |
"&#"{DIGIT}+";" |
"&#"[xX]({DIGIT}|[a-fA-F])+";" { return XmlTokenType.XML_CHAR_ENTITY_REF; }
"&"{TAG_NAME}";" { return XmlTokenType.XML_ENTITY_REF_TOKEN; }

[^] { return XmlTokenType.XML_DATA_CHARACTERS; }
