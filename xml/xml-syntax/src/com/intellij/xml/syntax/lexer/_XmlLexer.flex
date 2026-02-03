 /* It's an automatically generated code. Do not modify it. */
package com.intellij.xml.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.lexer.FlexLexer
import com.intellij.xml.syntax.XmlSyntaxTokenType
import com.intellij.xml.syntax.XmlSyntaxElementType
%%

%{
  private var elTokenType = XmlSyntaxTokenType.XML_DATA_CHARACTERS
  private var elTokenType2 = XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_TOKEN
  private var javaEmbeddedTokenType = XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_TOKEN
  private var myConditionalCommentsSupport: Boolean = false

  fun setConditionalCommentsSupport(b: Boolean) {
    myConditionalCommentsSupport = b
  }

  public fun setElTypes(_elTokenType: SyntaxElementType, _elTokenType2: SyntaxElementType) {
    elTokenType = _elTokenType;
    elTokenType2 = _elTokenType2;
  }

  public fun setJavaEmbeddedType(_tokenType: SyntaxElementType) {
    javaEmbeddedTokenType = _tokenType;
  }

  private var myPrevState = YYINITIAL

  fun yyprevstate() = myPrevState

  private fun popState(): Int {
    val prev = myPrevState
    myPrevState = YYINITIAL
    return prev
  }

  fun pushState(state: Int){
    myPrevState = state
  }
%}

%unicode
%class __XmlLexer
%public
%implements FlexLexer
%function advance
%type SyntaxElementType

%state TAG
%state PROCESSING_INSTRUCTION
%state PI_ANY
%state END_TAG
%xstate COMMENT
%state ATTR_LIST
%state ATTR
%state ATTR_VALUE_START
%state ATTR_VALUE_DQ
%state ATTR_VALUE_SQ
%state DTD_MARKUP
%state DOCTYPE
%xstate CDATA
%state C_COMMENT_START
/* this state should be last, number of states should be less than 16 */
%state C_COMMENT_END

ALPHA=[:letter:]
DIGIT=[0-9]
WS=[\ \n\r\t\f\u2028\u2029\u0085]
S={WS}+

EL_EMBEDMENT_START="${" | "#{"
NAME=({ALPHA}|"_"|":")({ALPHA}|{DIGIT}|"_"|"."|"-")*(":"({ALPHA}|"_")?({ALPHA}|{DIGIT}|"_"|"."|"-")*)?

END_COMMENT="-->"
CONDITIONAL_COMMENT_CONDITION=({ALPHA})({ALPHA}|{S}|{DIGIT}|"."|"("|")"|"|"|"!"|"&")*

%%
"<![CDATA[" {yybegin(CDATA); return XmlSyntaxTokenType.XML_CDATA_START; }
<CDATA>{
 "]]>"  {yybegin(YYINITIAL); return XmlSyntaxTokenType.XML_CDATA_END; }
 [^] {return XmlSyntaxTokenType.XML_DATA_CHARACTERS; }
}

"<!--" { yybegin(COMMENT); return XmlSyntaxTokenType.XML_COMMENT_START; }
<COMMENT> "[" { if (myConditionalCommentsSupport) {
    yybegin(C_COMMENT_START);
    return XmlSyntaxTokenType.XML_CONDITIONAL_COMMENT_START;
  } else return XmlSyntaxTokenType.XML_COMMENT_CHARACTERS; }
<COMMENT> "<![" { if (myConditionalCommentsSupport) {
    yybegin(C_COMMENT_END);
    return XmlSyntaxTokenType.XML_CONDITIONAL_COMMENT_END_START;
  } else return XmlSyntaxTokenType.XML_COMMENT_CHARACTERS; }
<COMMENT> {END_COMMENT} { yybegin(YYINITIAL); return XmlSyntaxTokenType.XML_COMMENT_END; }
<COMMENT> [^\-]|(-[^\-]) { return XmlSyntaxTokenType.XML_COMMENT_CHARACTERS; }
<COMMENT> [^] { return XmlSyntaxTokenType.XML_BAD_CHARACTER; }

<C_COMMENT_START,C_COMMENT_END> {CONDITIONAL_COMMENT_CONDITION} { return XmlSyntaxTokenType.XML_COMMENT_CHARACTERS; }
<C_COMMENT_START> [^] { yybegin(COMMENT); return XmlSyntaxTokenType.XML_COMMENT_CHARACTERS; }
<C_COMMENT_START> "]>" { yybegin(COMMENT); return XmlSyntaxTokenType.XML_CONDITIONAL_COMMENT_START_END; }
<C_COMMENT_START,C_COMMENT_END> {END_COMMENT} { yybegin(YYINITIAL); return XmlSyntaxTokenType.XML_COMMENT_END; }
<C_COMMENT_END> "]" { yybegin(COMMENT); return XmlSyntaxTokenType.XML_CONDITIONAL_COMMENT_END; }
<C_COMMENT_END> [^] { yybegin(COMMENT); return XmlSyntaxTokenType.XML_COMMENT_CHARACTERS; }

"&lt;" |
"&gt;" |
"&apos;" |
"&quot;" |
"&nbsp;" |
"&amp;" |
"&#"{DIGIT}+";" |
"&#x"({DIGIT}|[a-fA-F])+";" { return XmlSyntaxTokenType.XML_CHAR_ENTITY_REF; }
"&"{NAME}";" { return XmlSyntaxTokenType.XML_ENTITY_REF_TOKEN; }

<YYINITIAL> "<!DOCTYPE" { yybegin(DOCTYPE); return XmlSyntaxTokenType.XML_DOCTYPE_START; }
<DOCTYPE> "SYSTEM" { return XmlSyntaxTokenType.XML_DOCTYPE_SYSTEM;  }
<DOCTYPE> "PUBLIC" { return XmlSyntaxTokenType.XML_DOCTYPE_PUBLIC;  }
<DOCTYPE> {NAME} { return XmlSyntaxTokenType.XML_NAME;  }
<DOCTYPE> "\"" [^\"]* "\""? { return XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DOCTYPE> "'" [^']* "'"? { return XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DOCTYPE> "[" (([^\]\"]*)|(\"[^\"]*\"))* "]"? { return XmlSyntaxElementType.XML_MARKUP_DECL;}
<DOCTYPE> ">" { yybegin(YYINITIAL); return XmlSyntaxTokenType.XML_DOCTYPE_END; }

<YYINITIAL> "<?" { yybegin(PROCESSING_INSTRUCTION); return XmlSyntaxTokenType.XML_PI_START; }
<PROCESSING_INSTRUCTION> "xml" { yybegin(ATTR_LIST); pushState(PROCESSING_INSTRUCTION); return XmlSyntaxTokenType.XML_NAME; }
<PROCESSING_INSTRUCTION> {NAME} { yybegin(PI_ANY); return XmlSyntaxTokenType.XML_NAME; }
<PI_ANY, PROCESSING_INSTRUCTION> "?>" { yybegin(YYINITIAL); return XmlSyntaxTokenType.XML_PI_END; }
<PI_ANY> {S} { return XmlSyntaxTokenType.XML_WHITE_SPACE; }
<PI_ANY> [^] { return XmlSyntaxTokenType.XML_TAG_CHARACTERS; }

<YYINITIAL> {EL_EMBEDMENT_START} [^<\}]* "}"? {
  return elTokenType;
}

<YYINITIAL> "<" { yybegin(TAG); return XmlSyntaxTokenType.XML_START_TAG_START; }
<TAG> {NAME} { yybegin(ATTR_LIST); pushState(TAG); return XmlSyntaxTokenType.XML_NAME; }
<TAG> "/>" { yybegin(YYINITIAL); return XmlSyntaxTokenType.XML_EMPTY_ELEMENT_END; }
<TAG> ">" { yybegin(YYINITIAL); return XmlSyntaxTokenType.XML_TAG_END; }

<YYINITIAL> "</" { yybegin(END_TAG); return XmlSyntaxTokenType.XML_END_TAG_START; }
<END_TAG> {NAME} { return XmlSyntaxTokenType.XML_NAME; }
<END_TAG> ">" { yybegin(YYINITIAL); return XmlSyntaxTokenType.XML_TAG_END; }

<ATTR_LIST> {NAME} {yybegin(ATTR); return XmlSyntaxTokenType.XML_NAME;}
<ATTR> "=" { return XmlSyntaxTokenType.XML_EQ;}
<ATTR> "'" { yybegin(ATTR_VALUE_SQ); return XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;}
<ATTR> "\"" { yybegin(ATTR_VALUE_DQ); return XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;}
<ATTR> [^\ \n\r\t\f] {yybegin(ATTR_LIST); yypushback(yylength()); }

<ATTR_VALUE_DQ>{
  "\"" { yybegin(ATTR_LIST); return XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;}
  "&" { return XmlSyntaxTokenType.XML_BAD_CHARACTER; }
  {EL_EMBEDMENT_START} [^\}\"]* "}"? { return elTokenType2; }
  "%=" [^%\"]* "%" { return javaEmbeddedTokenType; }
  [^] { return XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
}

<ATTR_VALUE_SQ>{
  "&" { return XmlSyntaxTokenType.XML_BAD_CHARACTER; }
  "'" { yybegin(ATTR_LIST); return XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;}
  {EL_EMBEDMENT_START} [^\}\']* "}"? { return elTokenType2; }
  "%=" [^%\']* "%" { return javaEmbeddedTokenType; }
  [^] { return XmlSyntaxTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
}

<YYINITIAL> {S} { return XmlSyntaxTokenType.XML_REAL_WHITE_SPACE; }
<ATTR_LIST,ATTR,TAG,END_TAG,DOCTYPE> {S} { return XmlSyntaxTokenType.XML_WHITE_SPACE; }
<YYINITIAL> ([^<&\$# \n\r\t\f]|(\\\$)|(\\#))* { return XmlSyntaxTokenType.XML_DATA_CHARACTERS; }
<YYINITIAL> [^<&\ \n\r\t\f]|(\\\$)|(\\#) { return XmlSyntaxTokenType.XML_DATA_CHARACTERS; }

[^] { if(yystate() == YYINITIAL){
        return XmlSyntaxTokenType.XML_BAD_CHARACTER;
      }
      else yybegin(popState()); yypushback(yylength());}
