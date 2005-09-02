package com.intellij.lang.properties.parsing;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

%%

%class _PropertiesLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

CRLF= \n | \r | \r\n
WHITE_SPACE_CHAR=[\ \n\r\t\f]
VALUE_CHARACTER=[^\n\r\f\\] | "\\"{CRLF} | "\\".
END_OF_LINE_COMMENT=("#"|"!")[^\r\n]*
KEY_SEPARATOR=[\ \t]*[:=][\ \t]* | [\ \t]+
KEY_CHARACTER=[^:=\ \n\r\t\f\\] | "\\"{CRLF} | "\\".

%state IN_VALUE
%state IN_KEY_VALUE_SEPARATOR

%%

<YYINITIAL> {END_OF_LINE_COMMENT}        { yybegin(YYINITIAL); return PropertiesTokenTypes.END_OF_LINE_COMMENT; }

<YYINITIAL> {KEY_CHARACTER}+             { yybegin(IN_KEY_VALUE_SEPARATOR); return PropertiesTokenTypes.KEY_CHARACTERS; }
<IN_KEY_VALUE_SEPARATOR> {KEY_SEPARATOR} { yybegin(IN_VALUE); return PropertiesTokenTypes.KEY_VALUE_SEPARATOR; }
<IN_VALUE> {VALUE_CHARACTER}+            { yybegin(YYINITIAL); return PropertiesTokenTypes.VALUE_CHARACTERS; }

<IN_KEY_VALUE_SEPARATOR> {CRLF}{WHITE_SPACE_CHAR}*  { yybegin(YYINITIAL); return PropertiesTokenTypes.WHITE_SPACE; }
<IN_VALUE> {CRLF}{WHITE_SPACE_CHAR}*     { yybegin(YYINITIAL); return PropertiesTokenTypes.WHITE_SPACE; }
{WHITE_SPACE_CHAR}+                      { return PropertiesTokenTypes.WHITE_SPACE; }
.                                        { return PropertiesTokenTypes.BAD_CHARACTER; }