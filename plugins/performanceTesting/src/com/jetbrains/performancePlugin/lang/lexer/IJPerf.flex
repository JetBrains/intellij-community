package com.jetbrains.performancePlugin.lang.lexer;

import com.jetbrains.performancePlugin.lang.psi.IJPerfElementTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import com.intellij.lexer.FlexLexer;

%%

%class IJPerfLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

CRLF=\R
WHITE_SPACE=[\ \n\t\f]
COMMAND_PREFIX=("%")
ASSIGNMENT=("=")
OPTIONS_SEPARATOR=(","|[\ \t]+)
PIPE=("\|")
QUOTE=("\"")
COMMAND=({COMMAND_PREFIX}?{COMMAND_PREFIX}?{IDENTIFIER})
COMMENT=({COMMAND_PREFIX}{COMMAND_PREFIX}{ASSIGNMENT})[^\r\n]*
PATH_PREFIX = (\\\\)?([:jletterdigit:]+[:\\\/]+)?
GENERAL_PATH_PART = [[:jletterdigit:]\.\\/\-\_]+
GENERAL_PATH_PART_WITH_SPACES = [[:jletterdigit:]\.\\/\-\_\ \t]+
PATH_WITH_SPACES=({QUOTE}{PATH_PREFIX}{GENERAL_PATH_PART_WITH_SPACES}{QUOTE})
PATH_WITHOUT_SPACES=({PATH_PREFIX}{GENERAL_PATH_PART})
FILE_PATH=({PATH_WITH_SPACES}|{PATH_WITHOUT_SPACES})+
TEXT=([:jletterdigit:]|[^\n])+
IDENTIFIER=[^%,:=|\ \n\t\f\\/[:digit:]\"]([^%,:=|\ \n\t\f\\/\.])*
NUMBER=[:digit:]+

%state WAITING_INPUT
%state WAITING_TEXT
%state WAITING_LAST_OPTION
%%

<YYINITIAL> {COMMENT}                                       { yybegin(YYINITIAL); return IJPerfElementTypes.COMMENT; }
<YYINITIAL> {COMMAND}                                       { yybegin(WAITING_INPUT); return IJPerfElementTypes.COMMAND; }
<YYINITIAL> {CRLF}({CRLF}|{WHITE_SPACE})+                   { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<WAITING_INPUT> {CRLF}                                      { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }
<WAITING_INPUT> [\ \t]+                                     { yybegin(WAITING_LAST_OPTION); return TokenType.WHITE_SPACE; }
<WAITING_INPUT> {IDENTIFIER}                                { yybegin(WAITING_LAST_OPTION); return IJPerfElementTypes.IDENTIFIER; }
<WAITING_INPUT> {NUMBER}                                    { yybegin(WAITING_LAST_OPTION); return IJPerfElementTypes.NUMBER; }
<WAITING_INPUT> {FILE_PATH}                                 { yybegin(WAITING_LAST_OPTION); return IJPerfElementTypes.FILE_PATH; }

<WAITING_LAST_OPTION> {CRLF}({CRLF}|{WHITE_SPACE})+         { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }
<WAITING_LAST_OPTION> {OPTIONS_SEPARATOR}                   { yybegin(WAITING_LAST_OPTION); return IJPerfElementTypes.OPTIONS_SEPARATOR; }
<WAITING_LAST_OPTION> {NUMBER}                              { yybegin(WAITING_LAST_OPTION); return IJPerfElementTypes.NUMBER; }
<WAITING_LAST_OPTION> {IDENTIFIER}                          { yybegin(WAITING_LAST_OPTION); return IJPerfElementTypes.IDENTIFIER; }
<WAITING_LAST_OPTION> {PIPE}                                { yybegin(WAITING_TEXT); return IJPerfElementTypes.PIPE; }
<WAITING_LAST_OPTION> {ASSIGNMENT}                          { yybegin(WAITING_LAST_OPTION); return IJPerfElementTypes.ASSIGNMENT_OPERATOR; }
<WAITING_LAST_OPTION> {FILE_PATH}                           { yybegin(WAITING_LAST_OPTION); return IJPerfElementTypes.FILE_PATH; }

<WAITING_TEXT> {TEXT}                                       { yybegin(YYINITIAL); return IJPerfElementTypes.TEXT; }

({CRLF}|{WHITE_SPACE})+                                     { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

[^]                                                         { return TokenType.BAD_CHARACTER; }