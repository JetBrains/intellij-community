 /* It's an automatically generated code. Do not modify it. */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;

%%

%{
  private IElementType elTokenType = XmlTokenType.XML_DATA_CHARACTERS;
  private IElementType elTokenType2 = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
  private IElementType javaEmbeddedTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
  private boolean myConditionalCommentsSupport;

  public void setConditionalCommentsSupport(final boolean b) {
    myConditionalCommentsSupport = b;
  }

  public void setElTypes(IElementType _elTokenType,IElementType _elTokenType2) {
    elTokenType = _elTokenType;
    elTokenType2 = _elTokenType2;
  }

  public void setJavaEmbeddedType(IElementType _tokenType) {
    javaEmbeddedTokenType = _tokenType;
  }

  private int myPrevState = YYINITIAL;

  public int yyprevstate() {
    return myPrevState;
  }

  private int popState(){
    final int prev = myPrevState;
    myPrevState = YYINITIAL;
    return prev;
  }

  protected void pushState(int state){
    myPrevState = state;
  }
%}

%unicode
%class __XmlLexer
%public
%implements FlexLexer
%function advance
%type IElementType

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
"<![CDATA[" {yybegin(CDATA); return XmlTokenType.XML_CDATA_START; }
<CDATA>{
 "]]>"  {yybegin(YYINITIAL); return XmlTokenType.XML_CDATA_END; }
 [^] {return XmlTokenType.XML_DATA_CHARACTERS; }
}

"<!--" { yybegin(COMMENT); return XmlTokenType.XML_COMMENT_START; }
<COMMENT> "[" { if (myConditionalCommentsSupport) {
    yybegin(C_COMMENT_START);
    return XmlTokenType.XML_CONDITIONAL_COMMENT_START;
  } else return XmlTokenType.XML_COMMENT_CHARACTERS; }
<COMMENT> "<![" { if (myConditionalCommentsSupport) {
    yybegin(C_COMMENT_END);
    return XmlTokenType.XML_CONDITIONAL_COMMENT_END_START;
  } else return XmlTokenType.XML_COMMENT_CHARACTERS; }
<COMMENT> {END_COMMENT} { yybegin(YYINITIAL); return XmlTokenType.XML_COMMENT_END; }
<COMMENT> [^\-]|(-[^\-]) { return XmlTokenType.XML_COMMENT_CHARACTERS; }
<COMMENT> [^] { return XmlTokenType.XML_BAD_CHARACTER; }

<C_COMMENT_START,C_COMMENT_END> {CONDITIONAL_COMMENT_CONDITION} { return XmlTokenType.XML_COMMENT_CHARACTERS; }
<C_COMMENT_START> [^] { yybegin(COMMENT); return XmlTokenType.XML_COMMENT_CHARACTERS; }
<C_COMMENT_START> "]>" { yybegin(COMMENT); return XmlTokenType.XML_CONDITIONAL_COMMENT_START_END; }
<C_COMMENT_START,C_COMMENT_END> {END_COMMENT} { yybegin(YYINITIAL); return XmlTokenType.XML_COMMENT_END; }
<C_COMMENT_END> "]" { yybegin(COMMENT); return XmlTokenType.XML_CONDITIONAL_COMMENT_END; }
<C_COMMENT_END> [^] { yybegin(COMMENT); return XmlTokenType.XML_COMMENT_CHARACTERS; }

"&lt;" |
"&gt;" |
"&apos;" |
"&quot;" |
"&nbsp;" |
"&amp;" |
"&#"{DIGIT}+";" |
"&#x"({DIGIT}|[a-fA-F])+";" { return XmlTokenType.XML_CHAR_ENTITY_REF; }
"&"{NAME}";" { return XmlTokenType.XML_ENTITY_REF_TOKEN; }

<YYINITIAL> "<!DOCTYPE" { yybegin(DOCTYPE); return XmlTokenType.XML_DOCTYPE_START; }
<DOCTYPE> "SYSTEM" { return XmlTokenType.XML_DOCTYPE_SYSTEM;  }
<DOCTYPE> "PUBLIC" { return XmlTokenType.XML_DOCTYPE_PUBLIC;  }
<DOCTYPE> {NAME} { return XmlTokenType.XML_NAME;  }
<DOCTYPE> "\""[^\"]*"\"" { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DOCTYPE> "'"[^']*"'" { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DOCTYPE> "["(([^\]\"]*)|(\"[^\"]*\"))*"]" { return XmlElementType.XML_MARKUP_DECL;}
<DOCTYPE> ">" { yybegin(YYINITIAL); return XmlTokenType.XML_DOCTYPE_END; }

<YYINITIAL> "<?" { yybegin(PROCESSING_INSTRUCTION); return XmlTokenType.XML_PI_START; }
<PROCESSING_INSTRUCTION> "xml" { yybegin(ATTR_LIST); pushState(PROCESSING_INSTRUCTION); return XmlTokenType.XML_NAME; }
<PROCESSING_INSTRUCTION> {NAME} { yybegin(PI_ANY); return XmlTokenType.XML_NAME; }
<PI_ANY, PROCESSING_INSTRUCTION> "?>" { yybegin(YYINITIAL); return XmlTokenType.XML_PI_END; }
<PI_ANY> {S} { return XmlTokenType.XML_WHITE_SPACE; }
<PI_ANY> [^] { return XmlTokenType.XML_TAG_CHARACTERS; }

<YYINITIAL> {EL_EMBEDMENT_START} [^<\}]* "}" {
  return elTokenType;
}

<YYINITIAL> "<" { yybegin(TAG); return XmlTokenType.XML_START_TAG_START; }
<TAG> {NAME} { yybegin(ATTR_LIST); pushState(TAG); return XmlTokenType.XML_NAME; }
<TAG> "/>" { yybegin(YYINITIAL); return XmlTokenType.XML_EMPTY_ELEMENT_END; }
<TAG> ">" { yybegin(YYINITIAL); return XmlTokenType.XML_TAG_END; }

<YYINITIAL> "</" { yybegin(END_TAG); return XmlTokenType.XML_END_TAG_START; }
<END_TAG> {NAME} { return XmlTokenType.XML_NAME; }
<END_TAG> ">" { yybegin(YYINITIAL); return XmlTokenType.XML_TAG_END; }

<ATTR_LIST> {NAME} {yybegin(ATTR); return XmlTokenType.XML_NAME;}
<ATTR> "=" { return XmlTokenType.XML_EQ;}
<ATTR> "'" { yybegin(ATTR_VALUE_SQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;}
<ATTR> "\"" { yybegin(ATTR_VALUE_DQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;}
<ATTR> [^\ \n\r\t\f] {yybegin(ATTR_LIST); yypushback(yylength()); }

<ATTR_VALUE_DQ>{
  "\"" { yybegin(ATTR_LIST); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;}
  "&" { return XmlTokenType.XML_BAD_CHARACTER; }
  {EL_EMBEDMENT_START} [^\}\"]* "}" { return elTokenType2; }
  "%=" [^%\"]* "%" { return javaEmbeddedTokenType; }
  [^] { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
}

<ATTR_VALUE_SQ>{
  "&" { return XmlTokenType.XML_BAD_CHARACTER; }
  "'" { yybegin(ATTR_LIST); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;}
  {EL_EMBEDMENT_START} [^\}\']* "}" { return elTokenType2; }
  "%=" [^%\']* "%" { return javaEmbeddedTokenType; }
  [^] { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
}

<YYINITIAL> {S} { return XmlTokenType.XML_REAL_WHITE_SPACE; }
<ATTR_LIST,ATTR,TAG,END_TAG,DOCTYPE> {S} { return XmlTokenType.XML_WHITE_SPACE; }
<YYINITIAL> ([^<&\$# \n\r\t\f]|(\\\$)|(\\#))* { return XmlTokenType.XML_DATA_CHARACTERS; }
<YYINITIAL> [^<&\ \n\r\t\f]|(\\\$)|(\\#) { return XmlTokenType.XML_DATA_CHARACTERS; }

[^] { if(yystate() == YYINITIAL){
        return XmlTokenType.XML_BAD_CHARACTER;
      }
      else yybegin(popState()); yypushback(yylength());}
