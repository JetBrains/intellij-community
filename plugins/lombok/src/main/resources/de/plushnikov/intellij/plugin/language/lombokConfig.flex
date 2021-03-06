package de.plushnikov.intellij.plugin.language;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;

import de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes;

%%

%class LombokConfigLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

CRLF= \n | \r | \r\n
WHITE_SPACE_CHAR=[\ \n\r\t\f]
END_OF_LINE_COMMENT=("#")[^\r\n]*
CLEAR="clear"
KEY_CHARACTER=[^:=\ \n\r\t\f\\] | "\\"{CRLF} | "\\".
SEPARATOR=[\ \t]* [=] [\ \t]*
SIGN=[\ \t]* [\-\+]
VALUE_CHARACTER=[^:=\n\r\f\\] | "\\"{CRLF} | "\\".

%state IN_VALUE
%state IN_KEY_VALUE_SEPARATOR

%%

<YYINITIAL> {END_OF_LINE_COMMENT}        { yybegin(YYINITIAL); return LombokConfigTypes.COMMENT; }

<YYINITIAL> {CLEAR}                      { yybegin(YYINITIAL); return LombokConfigTypes.CLEAR; }

<YYINITIAL> {KEY_CHARACTER}+             { yybegin(IN_KEY_VALUE_SEPARATOR); return LombokConfigTypes.KEY; }
<IN_KEY_VALUE_SEPARATOR> {SIGN}          { yybegin(IN_KEY_VALUE_SEPARATOR); return LombokConfigTypes.SIGN; }
<IN_KEY_VALUE_SEPARATOR> {SEPARATOR}     { yybegin(IN_VALUE); return LombokConfigTypes.SEPARATOR; }
<IN_VALUE> {VALUE_CHARACTER}+            { yybegin(YYINITIAL); return LombokConfigTypes.VALUE; }

<IN_KEY_VALUE_SEPARATOR> {CRLF}{WHITE_SPACE_CHAR}*  { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }
<IN_VALUE> {CRLF}{WHITE_SPACE_CHAR}*     { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }
{WHITE_SPACE_CHAR}+                      { return TokenType.WHITE_SPACE; }
.                                        { return TokenType.BAD_CHARACTER; }