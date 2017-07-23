package com.jetbrains.rest.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.rest.RestTokenTypes;

/* Auto generated File */
%%

%class _RestFlexLexer
%implements FlexLexer, RestTokenTypes
%unicode
%public
%ignorecase
%function advance
%type IElementType

CRLF=\R
SPACE=[\ \t]

ADORNMENT_SYMBOL="="|"-"|"`"|":"|"."|"'"|\"|"~"|"^"|"_"|"*"|"+"|"#"|">"
ADORNMENT=("="+|"-"+|"`"+|":"+|"."+|"'"+|\"+|"~"+|"^"+|"_"+|"*"+|"+"+|"#"+)" "*{CRLF}
SEPARATOR=[\n .:,()\{\}\[\]\-;!\\/'\"]
USUAL_TYPES="attention"|"caution"|"danger"|"error"|"hint"|"important"|"note"|"tip"|"warning"|"admonition"|"image"|"figure"|"topic"|"sidebar"|"parsed-literal"|"rubric"|"epigraph"|"highlights"|"pull-quote"|"compound"|"container"|"table"|"csv-table"|"list-table"|"contents"|"sectnum"|"section-autonumbering"|"header"|"footer"|"target-notes"|"footnotes"|"citations"|"meta"|"replace"|"unicode"|"date"|"include"|"raw"|"class"|"role"|"default-role"|"title"|"restructuredtext-test-directive"
HIGHLIGHT_TYPES= "highlight" | "sourcecode" | "code-block"
NOT_BACKQUOTE = [^`]
LINK = [0-9A-Za-z][0-9A-Za-z\-:+_]*"_""_"?

%state IN_EXPLISIT_MARKUP
%state IN_COMMENT
%state IN_TITLE_TEXT
%state IN_BODY
%state IN_INLINE
%state HYPERLINK_TEXT
%state INDENTED
%state PRE_INDENTED
%state QUOTED
%state PRE_QUOTED
%state IN_LINE
%state IN_HIGHLIGHT
%state IN_VALUE
%state IN_FOOTNOTE
%state IN_LINEBEGIN
%state FIELD_IN_INLINE
%state FIELD_LINE
%state INIT

%{
  int myState = 0;    //python=1;django=2;initial=0;
  int myIndent = 0;
  private IElementType chooseType () {if (myState == 2)
                                        return DJANGO_LINE;
                                      else if (myState == 3)
                                        return JAVASCRIPT_LINE;
                                      else if (myState == 1)
                                        return PYTHON_LINE;
                                      else
                                        return INLINE_LINE;
                                    }
%}

%%
<YYINITIAL> {
":"[^:\n\r ]([^:\n\r] | "\\:")*[^:\n\r ]":"[ `\n]   { yypushback(1); return FIELD;}
.                                                   { yypushback(1); yybegin(INIT); }
}

<INIT> {
//TITLES
^{ADORNMENT}?.*{CRLF}{ADORNMENT}                    { return TITLE;}
{CRLF}{2,5}{ADORNMENT}{CRLF}+                       { return TITLE;}

//EXPLICIT MARKUP
".."" "+                                            { yybegin(IN_EXPLISIT_MARKUP); return EXPLISIT_MARKUP_START;}
"::"{CRLF}{CRLF}                                    { yybegin(IN_INLINE);return LITERAL_BLOCK_START;}

// ESCAPING
"\\"                                                { yybegin(IN_LINE); return SPEC_SYMBOL;}

// IMPLICIT MARKUP

"``"[^`\n\r ][^`\n\r]*[^`\n\r ]"``"                 { return FIXED;}
"**"[^*\n\r ][^*\n\r]*[^*\n\r ]"**"                 { return BOLD;}
"*"[^*\n\r ][^*\n\r]*[^*\n\r ]"*"                   { return ITALIC;}
"|"[^*|\n\r ][^*|\n\r]*[^*|\n\r ]"|"                { return SUBSTITUTION;}

"http"s?"://"[^\n\r ]+                              {return DIRECT_HYPERLINK;}

"`"[^`\n\r ][^`\n\r]*"`"                            { return INTERPRETED;}
"`"{NOT_BACKQUOTE}+"`_""_"?{SEPARATOR}              {yypushback(1); return REFERENCE_NAME;}
{LINK}{SEPARATOR}                                   {yypushback(1); return REFERENCE_NAME;}
[\"']{LINK}[\"']                                    {yypushback(yylength()-1); return LINE;}

":"[^:\n\r ]([^:\n\r] | "\\:")*[^:\n\r ]":"[`]      { yypushback(1); yybegin(INIT); return FIELD;}
{CRLF}                                              { yybegin(IN_LINEBEGIN); return WHITESPACE;}
.                                                   { yypushback(1); yybegin(IN_LINE); }
{SPACE}+                                            { yybegin(IN_LINEBEGIN); return WHITESPACE;}
}

<IN_LINEBEGIN> {
"["([0-9]* | #?[0-9A-Za-z]* | "*")"]_"{SEPARATOR}   { yypushback(1); yybegin(INIT); return REFERENCE_NAME;}
{SPACE}                                             { return LINE;}
{CRLF}                                              { return WHITESPACE;}
"__"                                                { yybegin(IN_VALUE); return ANONYMOUS_HYPERLINK;}
":"[^:\n\r ]([^:\n\r] | "\\:")*[^:\n\r ]":"[ `\n]   { yypushback(1); yybegin(INIT); return FIELD;}
.                                                   { yypushback(1); yybegin(INIT);}
}

<IN_FOOTNOTE> {
"["([0-9]* | #?[0-9A-Za-z]* | "*")"]_"{SEPARATOR}   { yypushback(1); yybegin(INIT); return REFERENCE_NAME;}
{SPACE}                                             { return LINE;}
{CRLF}                                              { return WHITESPACE;}
.                                                   { yypushback(1); yybegin(INIT);}
}

<IN_LINE> {
{CRLF}                                              { return WHITESPACE;}
[^`*:\n\r\[ |({]+                                   { yybegin(INIT); return LINE;}
{SPACE}                                             { yybegin(IN_FOOTNOTE); return LINE;}
"(" | "[" | "{"                                     { yybegin(IN_FOOTNOTE); return LINE;}
"`" | "_" | ":" | "*" | "[" | "|"                   { yybegin(INIT); return LINE;}
}

<IN_INLINE> {
//Two posibilities -- quoted-block, indented block

{CRLF}                                              { return WHITESPACE;}
{SPACE}+":"[^:\n\r ]([^:\n\r] | "\\:")*[^:\n\r ]":"[ `\n]        { yypushback(yylength()-1); yybegin(FIELD_IN_INLINE); return WHITESPACE;}

{SPACE}+                                            { yybegin(PRE_INDENTED); myIndent = yylength(); return chooseType();}
{ADORNMENT_SYMBOL}                                  { yybegin(PRE_QUOTED); return SPEC_SYMBOL;}

//{CRLF}{2}~{CRLF}{2}                                 { yybegin(INIT); return LINE;}
}

<FIELD_IN_INLINE> {
{SPACE}+                                            { return WHITESPACE;}
":"[^:\n\r ]([^:\n\r] | "\\:")*[^:\n\r ]":"[ `\n]   {yypushback(1); yybegin(FIELD_LINE); return FIELD;}
}

<FIELD_LINE> {
.*                                                  {yybegin(IN_INLINE); return LINE;}
}

<PRE_QUOTED> {
.+                                                  { return chooseType();}
{CRLF}                                              { yybegin(QUOTED);  return chooseType();}
}

<QUOTED> {
{ADORNMENT_SYMBOL}                                  { yybegin(PRE_QUOTED); return chooseType();}
.                                                   { yypushback(1); myState = 0; yybegin(INIT);}
}

<PRE_INDENTED> {
.+                                                  { yybegin(INDENTED);  return chooseType();}
{CRLF}                                              { return chooseType();}
}

<INDENTED> {
{SPACE}+                                            { if (yylength() >= myIndent) {
                                                        yybegin(PRE_INDENTED); return chooseType();}
                                                     else {
                                                      myIndent = 0; yypushback(yylength()); yybegin(INIT);
                                                     }  }
{SPACE}*{CRLF}+                                     { return chooseType();}
{CRLF}+                                             { return chooseType();}
.                                                   { yypushback(1); myIndent = 0; myState = 0; yybegin(INIT);}
}

<IN_EXPLISIT_MARKUP> {
"["([0-9]* | #[0-9A-Za-z]* | "*")"]"                { yybegin(INIT); return FOOTNOTE;}
"["[0-9A-Za-z]*"]"                                  { yybegin(INIT); return CITATION;}

"__"                                                { yybegin(IN_VALUE); return ANONYMOUS_HYPERLINK;}
"_"[^_\n\r:][^\n\r:]+":"                            { yybegin(INIT); return HYPERLINK;}

{USUAL_TYPES}"::"                                   { yybegin(IN_VALUE); return DIRECTIVE;}
{HIGHLIGHT_TYPES}"::"                               { yybegin(IN_HIGHLIGHT); return CUSTOM_DIRECTIVE;}
[0-9A-Za-z\-:]*"::"                                 { yybegin(IN_VALUE); return CUSTOM_DIRECTIVE;}
"|"[^|]*"|"                                         { return SUBSTITUTION;}
[0-9A-Za-z_\[|.]+                                   { yybegin(IN_COMMENT); return COMMENT;}
{CRLF}{2}                                           { yybegin(INIT); return COMMENT;}
{SPACE}*{CRLF}+                                     { return WHITESPACE; }
{SPACE}+                                            { return WHITESPACE;}
}

<IN_VALUE> {
.+                                                  { return LINE;}
{CRLF}                                              { yybegin(INIT); return WHITESPACE; }
}

<IN_HIGHLIGHT> {
{SPACE}+                                            { return WHITESPACE;}
{CRLF}                                              { yybegin(INIT); return WHITESPACE; }
[A-Za-z+]+{CRLF}                                    { String value = yytext().toString().trim();
                                                      if ("python".equalsIgnoreCase(value)) {
                                                        myState = 1;
                                                        yybegin(IN_INLINE);
                                                      }
                                                      else if ("django".equalsIgnoreCase(value) ||
                                                              "html+django".equalsIgnoreCase(value)) {
                                                        myState = 2;
                                                        yybegin(IN_INLINE);
                                                      }
                                                      else if ("javascript".equalsIgnoreCase(value)) {
                                                        myState = 3;
                                                        yybegin(IN_INLINE);
                                                      }
                                                      else {
                                                        yybegin(INIT);
                                                      }
                                                     return LINE;
                                                    }
.                                                   { yypushback(1); yybegin(INIT);}
}

<IN_COMMENT> {
{CRLF}".. "                                         { yybegin(IN_EXPLISIT_MARKUP); return EXPLISIT_MARKUP_START;}
{SPACE}+                                            { return WHITESPACE;}
{CRLF}{2}                                           { yybegin(INIT); return COMMENT;}
.                                                   { return COMMENT;}
{CRLF}                                              { return WHITESPACE;}
}

{CRLF}                                              { yybegin(INIT); return WHITESPACE; }
.                                                   { yybegin(INIT); return ERROR;}