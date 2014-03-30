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
%state SEMICOLON


%%

<YYINITIAL>{
{COMMENT}                               { yybegin(YYINITIAL); return COMMENT; }
{LBRACKET}                              { yybegin(IN_SECTION_NAME); return LBRACKET; }
{FIRST_KEY_CHARACTER}{KEY_CHARACTER}*   { yybegin(IN_KEY_VALUE_SEPARATOR); return KEY_CHARACTERS; }
{SPACE}*{CRLF}+                         { return WHITESPACE; }
{SPACE}+{VALUE_CHARACTER}+              { return VALUE_CHARACTERS;}
}

<IN_SECTION_NAME>{
{SECTION_NAME_CHAR}+    {return SECTION_NAME; }
{RBRACKET}              {yybegin(YYINITIAL); return RBRACKET; }
{SPACE}+                {return WHITESPACE;}
{CRLF}                  {yybegin(YYINITIAL); return WHITESPACE; }
}

<IN_KEY_VALUE_SEPARATOR> {KEY_SEPARATOR}   { yybegin(IN_VALUE); return KEY_VALUE_SEPARATOR; }
<IN_KEY_VALUE_SEPARATOR> {CRLF}            { yybegin(YYINITIAL); return WHITESPACE; }
<IN_VALUE> {VALUE_CHARACTER}+              {
                                             CharSequence matched = yytext();
                                             int ind = matched.toString().indexOf(" ;");
                                             if (ind>=0) {
                                               yypushback(matched.length() - ind);
                                               yybegin(SEMICOLON);
                                             } else {
                                               yybegin(YYINITIAL);
                                             }
                                             return VALUE_CHARACTERS; }
<IN_VALUE> {SPACE}*{CRLF}                  { yybegin(YYINITIAL); return WHITESPACE;}
<SEMICOLON> " ;"[^\r\n]*{CRLF}?              { yybegin(YYINITIAL); return COMMENT;} 

.                                        { return ERROR; }