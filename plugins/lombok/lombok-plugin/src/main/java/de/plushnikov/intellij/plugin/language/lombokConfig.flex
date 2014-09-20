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

CRLF= \n|\r|\r\n
WHITE_SPACE=[\ \t\f]
END_OF_LINE_COMMENT=("#")[^\r\n]*

CLEAN="clean"
KEY_CHARACTER=[a-zA-Z0-9\.]
SEPARATOR="="|"-="|"+="
VALUE_CHARACTER=[a-zA-Z0-9\.@_]

%state WAITING_VALUE

%%

<YYINITIAL> {END_OF_LINE_COMMENT}                           { yybegin(YYINITIAL); return LombokConfigTypes.COMMENT; }

<YYINITIAL> {CLEAN}                                       { yybegin(YYINITIAL); return LombokConfigTypes.CLEAN; }

<YYINITIAL> {KEY_CHARACTER}+                                { yybegin(YYINITIAL); return LombokConfigTypes.KEY; }

<YYINITIAL> {SEPARATOR}                                     { yybegin(WAITING_VALUE); return LombokConfigTypes.SEPARATOR; }

<WAITING_VALUE> {CRLF}                                     { yybegin(YYINITIAL); return LombokConfigTypes.CRLF; }

<WAITING_VALUE> {WHITE_SPACE}+                              { yybegin(WAITING_VALUE); return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {VALUE_CHARACTER}+                          { yybegin(YYINITIAL); return LombokConfigTypes.VALUE; }

{CRLF}                                                     { yybegin(YYINITIAL); return LombokConfigTypes.CRLF; }

{WHITE_SPACE}+                                              { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

.                                                           { return TokenType.BAD_CHARACTER; }