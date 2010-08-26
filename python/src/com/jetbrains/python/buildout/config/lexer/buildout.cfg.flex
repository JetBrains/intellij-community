package com.jetbrains.python.buildout.config.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.buildout.config.BuildoutCfgTokenTypes;

/* Auto generated File */
%%

%class _BuildoutCfgFlexLexer
%implements FlexLexer, BuildoutCfgTokenTypes
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}



CRLF= \n|\r|\r\n
SPACE=[\ \t]
NOTSPACE=[^\ \t]
WHITESPACE_CHAR=[\ \n\r\t\f]
VALUE_CHARACTER=[^\n\r\f]
LBRACKET="["
RBRACKET="]"
SECTION_NAME_CHAR=[^\]\ \t\n\r]
COMMENT=("#"|";")[^\r\n]*{CRLF}?

KEY_SEPARATOR=[:=]
FIRST_KEY_CHARACTER=[^:=\[\ \n\r\t\f]
KEY_CHARACTER=[^:=\n\r\f]

%state IN_SECTION_NAME
%state IN_KEY_VALUE_SEPARATOR
%state IN_VALUE
%state IN_MULTILINE_VALUE
%state IN_MULTILINE_BEGIN
%state IN_MULTILINE_END

%%

<YYINITIAL>{
{COMMENT}                               { yybegin(YYINITIAL); return COMMENT; }
{LBRACKET}                              { yybegin(IN_SECTION_NAME); return LBRACKET; }
{FIRST_KEY_CHARACTER}{KEY_CHARACTER}*   { yybegin(IN_KEY_VALUE_SEPARATOR); return KEY_CHARACTERS; }
{WHITESPACE_CHAR}+                      { return WHITESPACE; }
}

<IN_SECTION_NAME>{
{SECTION_NAME_CHAR}+    {return SECTION_NAME; }
{RBRACKET}              {yybegin(YYINITIAL); return RBRACKET; }
{SPACE}+                {return WHITESPACE;}
{CRLF}                  {yybegin(YYINITIAL); return WHITESPACE; }
}

<IN_KEY_VALUE_SEPARATOR> {KEY_SEPARATOR}   { yybegin(IN_VALUE); return KEY_VALUE_SEPARATOR; }
<IN_KEY_VALUE_SEPARATOR> {CRLF}            { yybegin(YYINITIAL); return WHITESPACE; }
<IN_VALUE> {VALUE_CHARACTER}+              { yybegin(YYINITIAL);
                                             CharSequence matched = yytext();
                                             int ind = matched.toString().indexOf(" ;");
                                             if (ind>=0) {
                                               yypushback(matched.length() - ind);
                                             }
                                             return VALUE_CHARACTERS; }
<IN_VALUE> {SPACE}*{CRLF}                  { yybegin(IN_MULTILINE_BEGIN); return WHITESPACE;}
<IN_MULTILINE_BEGIN> {SPACE}+              { yybegin(IN_MULTILINE_VALUE); return WHITESPACE;}
<IN_MULTILINE_BEGIN> {NOTSPACE}              { yybegin(YYINITIAL); yypushback(1); }
<IN_MULTILINE_VALUE> {VALUE_CHARACTER}+    { yybegin(IN_MULTILINE_END); return MULTILINE_VALUE_CHARACTERS; }
<IN_MULTILINE_END> {SPACE}*{CRLF}          { yybegin(IN_MULTILINE_BEGIN); return WHITESPACE;}

.                                        { return ERROR; }