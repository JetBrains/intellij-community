package com.intellij.tasks.jira.jql;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

%%

%class _JqlLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%ignorecase
%eof{  return;
%eof}

WHITESPACE_CHAR = [\ \t]
DIGIT = [0-9]
HEX_DIGIT = [0-9a-fA-F]
// RESERVED_CHARS = [/+.;?*%\^$#@\[\]{}]
// OPERATORS = [<>=~!()]
// this symbols unused but also disallowed to include in identifier
// UNUSED_OPERATORS = [&|]

VALID_ESCAPE = \\['\"tnr\\\ ] | \\u{HEX_DIGIT}{4}
// JIRA allows to include virtually any symbol in field name except reserved ones
// VALID_CHAR = !({WHITESPACE_CHAR}|{RESERVED_CHARS}|{OPERATORS}|\\)+
VALID_CHAR = [^'\"\n\r\ \t+.,;?*%\^$#@\[\]{}<>=~!()\\|\&]
UNQUOTED_STRING = ({VALID_ESCAPE}|{VALID_CHAR})+
QUOTED_STRING = \"({VALID_ESCAPE}|[^\\\"])*\"
SQUOTED_STRING = '({VALID_ESCAPE}|[^\\'])*'
// only minus sign is allowed as prefix and no floating point numbers
STRING_LITERAL = {UNQUOTED_STRING} | {QUOTED_STRING} | {SQUOTED_STRING}
NUMBER_LITERAL = -?{DIGIT}+
CUSTOM_FIELD = cf\[{DIGIT}+\]

%%
"and"                 { return JqlTokenTypes.AND_KEYWORD; }
"or"                  { return JqlTokenTypes.OR_KEYWORD; }
"not"                 { return JqlTokenTypes.NOT_KEYWORD; }
"empty"               { return JqlTokenTypes.EMPTY_KEYWORD; }
"null"                { return JqlTokenTypes.NULL_KEYWORD; }
"order"               { return JqlTokenTypes.ORDER_KEYWORD; }
"by"                  { return JqlTokenTypes.BY_KEYWORD; }
"was"                 { return JqlTokenTypes.WAS_KEYWORD; }
"changed"             { return JqlTokenTypes.CHANGED_KEYWORD; }
"is"                  { return JqlTokenTypes.IS_KEYWORD; }
"in"                  { return JqlTokenTypes.IN_KEYWORD; }
"asc"                 { return JqlTokenTypes.ASC_KEYWORD; }
"desc"                { return JqlTokenTypes.DESC_KEYWORD; }
"changed"             { return JqlTokenTypes.CHANGED_KEYWORD; }
"from"                { return JqlTokenTypes.FROM_KEYWORD; }
"to"                  { return JqlTokenTypes.TO_KEYWORD; }
"on"                  { return JqlTokenTypes.ON_KEYWORD; }
"during"              { return JqlTokenTypes.DURING_KEYWORD; }
"before"              { return JqlTokenTypes.BEFORE_KEYWORD; }
"after"               { return JqlTokenTypes.AFTER_KEYWORD; }

"="                   { return JqlTokenTypes.EQ; }
"!="                  { return JqlTokenTypes.NE; }
"<"                   { return JqlTokenTypes.LT; }
">"                   { return JqlTokenTypes.GT; }
"<="                  { return JqlTokenTypes.LE; }
">="                  { return JqlTokenTypes.GE; }
"~"                   { return JqlTokenTypes.CONTAINS; }
"!~"                  { return JqlTokenTypes.NOT_CONTAINS; }
"("                   { return JqlTokenTypes.LPAR; }
")"                   { return JqlTokenTypes.RPAR; }
"&&"                  { return JqlTokenTypes.AMP_AMP; }
"&"                   { return JqlTokenTypes.AMP; }
"||"                  { return JqlTokenTypes.PIPE_PIPE; }
"|"                   { return JqlTokenTypes.PIPE; }
"!"                   { return JqlTokenTypes.BANG; }
","                   { return JqlTokenTypes.COMMA; }

{CUSTOM_FIELD}        { return JqlTokenTypes.CUSTOM_FIELD; }
{NUMBER_LITERAL}      { return JqlTokenTypes.NUMBER_LITERAL; }
{STRING_LITERAL}      { return JqlTokenTypes.STRING_LITERAL; }

{WHITESPACE_CHAR}+    { return JqlTokenTypes.WHITE_SPACE; }

[^]                   { return JqlTokenTypes.BAD_CHARACTER; }
