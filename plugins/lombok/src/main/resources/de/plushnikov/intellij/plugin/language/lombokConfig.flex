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

EOL= \n | \r | \r\n
WHITESPACE=[\ \t\f]+
MULTILINE_WHITESPACE=[\ \n\r\t\f]*{EOL}

NOT_WHITESPACE=[^\n\r\f\t\ ]

STRING_TAIL=[^\r\n]*
COMMENT=#{STRING_TAIL}

CLEAR="clear"
KEY_CHARACTER=([:letter:] | [:digit:] | ".")+
SEPARATOR="="
SIGN="+"|"-"
VALUE_CHARACTER={NOT_WHITESPACE} | {NOT_WHITESPACE}{STRING_TAIL}{NOT_WHITESPACE}

%state IN_VALUE
%state IN_KEY_VALUE_SEPARATOR

%%

{COMMENT}        { return LombokConfigTypes.COMMENT; }
{MULTILINE_WHITESPACE} { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<YYINITIAL> {
                {CLEAR}            { yybegin(YYINITIAL); return LombokConfigTypes.CLEAR; }
                {KEY_CHARACTER}    { yybegin(IN_KEY_VALUE_SEPARATOR); return LombokConfigTypes.KEY; }
            }

<IN_KEY_VALUE_SEPARATOR> {
                {SIGN}          { yybegin(IN_KEY_VALUE_SEPARATOR); return LombokConfigTypes.SIGN; }
                {SEPARATOR}     { yybegin(IN_VALUE); return LombokConfigTypes.SEPARATOR; }
            }

<IN_VALUE> {
                {VALUE_CHARACTER} { yybegin(YYINITIAL); return LombokConfigTypes.VALUE; }
}

{WHITESPACE}                      { return TokenType.WHITE_SPACE; }
[^]                               { return TokenType.BAD_CHARACTER; }