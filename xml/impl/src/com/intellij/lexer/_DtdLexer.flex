 /* It's an automatically generated code. Do not modify it. */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;

%%

%{
   private boolean isHighlightModeOn = false;

   public _DtdLexer(boolean highlightModeOn) {
     this((java.io.Reader)null);
     isHighlightModeOn = highlightModeOn;
   }
%}

%unicode
%class _DtdLexer
%public
%implements FlexLexer
%function advance
%type IElementType
%eof{  return;
%eof}

%state DECL
%state DECL_ATTR
%state DECL_ATTR_VALUE_DQ
%state DECL_ATTR_VALUE_SQ
%state TAG_NAME
%state TAG_ATTRIBUTES
%state COMMENT
%state ATTRIBUTE_VALUE_START
%state ATTRIBUTE_VALUE_DQ
%state ATTRIBUTE_VALUE_SQ
%state PROCESSING_INSTRUCTION
%state DOCTYPE_MARKUP_STARTED

%state DOCTYPE
%state DOCTYPE_EXTERNAL_ID
%state DOCTYPE_MARKUP
%state DOCTYPE_MARKUP_DQ
%state DOCTYPE_MARKUP_SQ
%state CDATA
%state CONDITIONAL
%state DOCTYPE_COMMENT

ALPHA=[:letter:]
DIGIT=[0-9]
WS=[\ \n\r\t\f]
S={WS}+

TAG_NAME=({ALPHA}|"_"|":")({ALPHA}|{DIGIT}|"_"|":"|"."|"-")*
NAME=({ALPHA}|"_"|":")({ALPHA}|{DIGIT}|"_"|":"|"."|"-")*
NMTOKEN=({ALPHA}|{DIGIT}|"_"|":"|"."|"-")+

%%
"<![CDATA[" {yybegin(CDATA); return XmlTokenType.XML_CDATA_START; }
<CDATA> "]]>"  {yybegin(YYINITIAL); return XmlTokenType.XML_CDATA_END; }
<CDATA> [^] {return XmlTokenType.XML_DATA_CHARACTERS; }
<CDATA> "<!--" { return XmlTokenType.XML_DATA_CHARACTERS; }

"<![" {yybegin(CONDITIONAL); return XmlTokenType.XML_CONDITIONAL_SECTION_START; }

<CONDITIONAL> "INCLUDE" { return XmlTokenType.XML_CONDITIONAL_INCLUDE; }
<CONDITIONAL> "IGNORE" { return XmlTokenType.XML_CONDITIONAL_IGNORE; }
"]]>" { yybegin(YYINITIAL); return XmlTokenType.XML_CONDITIONAL_SECTION_END; }

"&"{NAME}";" { return XmlTokenType.XML_ENTITY_REF_TOKEN; }
"%"{NAME}";" { return XmlTokenType.XML_ENTITY_REF_TOKEN; }

"&#"{DIGIT}+";" { return XmlTokenType.XML_CHAR_ENTITY_REF; }
"&#x"({DIGIT}|[a-fA-F])+";" { return XmlTokenType.XML_CHAR_ENTITY_REF; }

<YYINITIAL> "<?xml " { yybegin(DECL); return XmlTokenType.XML_DECL_START; }

<DECL> {NAME} { yybegin(DECL_ATTR); return XmlTokenType.XML_NAME; }
<DECL_ATTR> "=" { return XmlTokenType.XML_EQ;}
<DECL_ATTR> "'" { yybegin(DECL_ATTR_VALUE_SQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;}
<DECL_ATTR> "\"" { yybegin(DECL_ATTR_VALUE_DQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER;}
<DECL_ATTR_VALUE_DQ> [^\"]* { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DECL_ATTR_VALUE_SQ> [^']* { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DECL_ATTR_VALUE_DQ> "\"" { yybegin(DECL); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;}
<DECL_ATTR_VALUE_SQ> "'" { yybegin(DECL); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER;}
<DECL> "?>" { yybegin(YYINITIAL); return XmlTokenType.XML_DECL_END;}
<DECL> ">" { yybegin(YYINITIAL); return XmlTokenType.XML_BAD_CHARACTER;}

<YYINITIAL> "<!DOCTYPE" { yybegin(DOCTYPE); return XmlTokenType.XML_DOCTYPE_START; }
<DOCTYPE> "SYSTEM" { yybegin(DOCTYPE_EXTERNAL_ID); return XmlTokenType.XML_DOCTYPE_SYSTEM; }
<DOCTYPE> "PUBLIC" { yybegin(DOCTYPE_EXTERNAL_ID); return XmlTokenType.XML_DOCTYPE_PUBLIC; }
<DOCTYPE> {NAME} { return XmlTokenType.XML_NAME; }
<DOCTYPE_EXTERNAL_ID> "\""[^\"]*"\"" { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DOCTYPE_EXTERNAL_ID> "'"[^']*"'" { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;}
<DOCTYPE, DOCTYPE_EXTERNAL_ID,CONDITIONAL> "[" { yybegin(DOCTYPE_MARKUP); return XmlTokenType.XML_MARKUP_START;}
<DOCTYPE_MARKUP, YYINITIAL> "<!ELEMENT" { yybegin(DOCTYPE_MARKUP); return XmlTokenType.XML_ELEMENT_DECL_START;}
<DOCTYPE_MARKUP, YYINITIAL> "<!NOTATION" { yybegin(DOCTYPE_MARKUP); return XmlTokenType.XML_NOTATION_DECL_START;}
<DOCTYPE_MARKUP, YYINITIAL> "<!ATTLIST" { yybegin(DOCTYPE_MARKUP); return XmlTokenType.XML_ATTLIST_DECL_START;}
<DOCTYPE_MARKUP, YYINITIAL> "<!ENTITY" { yybegin(DOCTYPE_MARKUP); return XmlTokenType.XML_ENTITY_DECL_START;}
<DOCTYPE_MARKUP> "SYSTEM" { return XmlTokenType.XML_DOCTYPE_SYSTEM; }
<DOCTYPE_MARKUP> "PUBLIC" { return XmlTokenType.XML_DOCTYPE_PUBLIC; }
<DOCTYPE_MARKUP> "#PCDATA" { return XmlTokenType.XML_PCDATA;}
<DOCTYPE_MARKUP> "EMPTY" { return XmlTokenType.XML_CONTENT_EMPTY;}
<DOCTYPE_MARKUP> "ANY" { return XmlTokenType.XML_CONTENT_ANY;}
<DOCTYPE_MARKUP> "(" { return XmlTokenType.XML_LEFT_PAREN;}
<DOCTYPE_MARKUP> ")" { return XmlTokenType.XML_RIGHT_PAREN;}
<DOCTYPE_MARKUP> "?" { return XmlTokenType.XML_QUESTION;}
<DOCTYPE_MARKUP> "+" { return XmlTokenType.XML_PLUS;}
<DOCTYPE_MARKUP> "*" { return XmlTokenType.XML_STAR;}
<DOCTYPE_MARKUP> "%" { return XmlTokenType.XML_PERCENT;}
<DOCTYPE_MARKUP> "--" ([^\-]|(\-[^\-]))* "--" { yybegin(DOCTYPE_MARKUP_STARTED); yypushback(yylength()); }
<DOCTYPE_MARKUP_STARTED> "--" { yybegin(DOCTYPE_COMMENT); return XmlTokenType.XML_COMMENT_START;}
<DOCTYPE_COMMENT> ([^\-]|(\-[^\-]))* { return XmlTokenType.XML_COMMENT_CHARACTERS; }
<DOCTYPE_COMMENT> "--" { yybegin(DOCTYPE_MARKUP); return XmlTokenType.XML_COMMENT_END;}
<DOCTYPE_MARKUP> \| { return XmlTokenType.XML_BAR;}
<DOCTYPE_MARKUP> "," { return XmlTokenType.XML_COMMA;}
<DOCTYPE_MARKUP> "&" { return XmlTokenType.XML_AMP;}
<DOCTYPE_MARKUP> ";" { return XmlTokenType.XML_SEMI;}
<DOCTYPE_MARKUP> "#IMPLIED" { return XmlTokenType.XML_ATT_IMPLIED;}
<DOCTYPE_MARKUP> "#REQUIRED" { return XmlTokenType.XML_ATT_REQUIRED;}
<DOCTYPE_MARKUP> "#FIXED" { return XmlTokenType.XML_ATT_FIXED;}
<DOCTYPE_MARKUP> {NMTOKEN} { return XmlTokenType.XML_NAME;}
<DOCTYPE_MARKUP> ">" { return XmlTokenType.XML_TAG_END;}
<DOCTYPE_MARKUP> "]" { yybegin(DOCTYPE); return XmlTokenType.XML_MARKUP_END;}
<DOCTYPE_MARKUP> "\"" { yybegin(DOCTYPE_MARKUP_DQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; }
<DOCTYPE_MARKUP> "'" { yybegin(DOCTYPE_MARKUP_SQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; }
<DOCTYPE_MARKUP_DQ> "\"" { yybegin(DOCTYPE_MARKUP); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; }
<DOCTYPE_MARKUP_SQ> "'" { yybegin(DOCTYPE_MARKUP); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; }
<DOCTYPE_MARKUP_DQ> [^\"]+ { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; }
<DOCTYPE_MARKUP_SQ> [^']+ { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; }
<DOCTYPE,DOCTYPE_EXTERNAL_ID> ">" { yybegin(YYINITIAL); return XmlTokenType.XML_DOCTYPE_END; }

<YYINITIAL> "<?" { yybegin(PROCESSING_INSTRUCTION); return XmlTokenType.XML_PI_START; }
<PROCESSING_INSTRUCTION> "?>" { yybegin(YYINITIAL); return XmlTokenType.XML_PI_END; }
<PROCESSING_INSTRUCTION> [^] { return XmlTokenType.XML_PI_TARGET; }

<YYINITIAL,TAG_NAME,TAG_ATTRIBUTES,ATTRIBUTE_VALUE_START,ATTRIBUTE_VALUE_DQ,ATTRIBUTE_VALUE_SQ>"<" { yybegin(TAG_NAME); return XmlTokenType.XML_START_TAG_START; }
<YYINITIAL,TAG_NAME,TAG_ATTRIBUTES,ATTRIBUTE_VALUE_START,ATTRIBUTE_VALUE_DQ,ATTRIBUTE_VALUE_SQ>"</" { yybegin(TAG_NAME); return XmlTokenType.XML_END_TAG_START; }


<TAG_NAME> {TAG_NAME} { yybegin(TAG_ATTRIBUTES); return isHighlightModeOn ? XmlTokenType.XML_TAG_NAME:XmlTokenType.XML_NAME; }

<TAG_ATTRIBUTES> ">" { yybegin(YYINITIAL); return XmlTokenType.XML_TAG_END; }
<TAG_ATTRIBUTES> "/>" { yybegin(YYINITIAL); return XmlTokenType.XML_EMPTY_ELEMENT_END; }
<TAG_ATTRIBUTES> {NAME} { return XmlTokenType.XML_NAME; }
<TAG_ATTRIBUTES> "=" { yybegin(ATTRIBUTE_VALUE_START); return XmlTokenType.XML_EQ; }

<ATTRIBUTE_VALUE_START> ">" { yybegin(YYINITIAL); return XmlTokenType.XML_TAG_END; }
<ATTRIBUTE_VALUE_START> "/>" { yybegin(YYINITIAL); return XmlTokenType.XML_EMPTY_ELEMENT_END; }
<ATTRIBUTE_VALUE_START> "\"" { yybegin(ATTRIBUTE_VALUE_DQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; }
<ATTRIBUTE_VALUE_START> "'" { yybegin(ATTRIBUTE_VALUE_SQ); return XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; }
<ATTRIBUTE_VALUE_DQ> "\"" { yybegin(TAG_ATTRIBUTES); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; }
<ATTRIBUTE_VALUE_SQ> "'" { yybegin(TAG_ATTRIBUTES); return XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; }
<ATTRIBUTE_VALUE_DQ> [^\"] { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; }
<ATTRIBUTE_VALUE_SQ> [^'] { return XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; }

"<!--" { yybegin(COMMENT); return XmlTokenType.XML_COMMENT_START; }
<COMMENT> "-->" { yybegin(YYINITIAL); return XmlTokenType.XML_COMMENT_END; }
<COMMENT> ([^\-]|(-[^\-]))* { return XmlTokenType.XML_COMMENT_CHARACTERS; }

<YYINITIAL,DECL,DECL_ATTR,TAG_NAME,TAG_ATTRIBUTES,COMMENT,ATTRIBUTE_VALUE_START,
 DOCTYPE,DOCTYPE_EXTERNAL_ID,DOCTYPE_MARKUP,CONDITIONAL>{S} { return XmlTokenType.XML_WHITE_SPACE; }

<YYINITIAL> [^<&] { return XmlTokenType.XML_DATA_CHARACTERS; }

[^] { return XmlTokenType.XML_BAD_CHARACTER; }
