package org.toml.lang.core.lexer;
import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import static org.toml.lang.core.psi.TomlTypes.*;

%%

%{
  public _TomlLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _TomlLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL="\r"|"\n"|"\r\n"
LINE_WS=[\ \t\f]
WHITE_SPACE=({LINE_WS}|{EOL})+

COMMENT=#[^\n\r]*
ANY_ESCAPE_SEQUENCE = \\[^]

THREE_DQ = (\"\"\")
ONE_TWO_DQ = (\"[^\"]) | (\"\\[^]) | (\"\"[^\"]) | (\"\"\\[^])
DQ_STRING_CHAR = [^\"] | {ANY_ESCAPE_SEQUENCE} | {ONE_TWO_DQ}
MULTILINE_STRING = {THREE_DQ} {DQ_STRING_CHAR}* {THREE_DQ}?

STRING=(\"[^\r\n\"]*\"?)

THREE_SQ = (\'\'\')
ONE_TWO_SQ = ('[^']) | ('\\[^]) | (''[^']) | (''\\[^])
SQ_STRING_CHAR = [^\\'] | {ANY_ESCAPE_SEQUENCE} | {ONE_TWO_SQ}
MULTILINE_STRING_SQ = {THREE_SQ} {SQ_STRING_CHAR}* {THREE_SQ}?

STRING_SQ=('[^\r\n\']*'?)

SIMPLE_NUMBER=-?[0-9]+
NUMBER=[-+]?[1-9](_?[0-9])*(\.[0-9](_?[0-9])*)?([eE][-+]?[1-9](_?[0-9])*)?

DATE=[0-9]{4}-[0-9]{2}-[0-9]{2}([Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?)?([Zz]|[+-][0-9]{2}:[0-9]{2})?

BOOLEAN=true|false

SIMPLE_KEY=[0-9_\-a-zA-Z]+

%%
<YYINITIAL> {
  {WHITE_SPACE}         { return com.intellij.psi.TokenType.WHITE_SPACE; }

  {COMMENT}             { return COMMENT; }

  {MULTILINE_STRING}    { return STRING; }
  {STRING}              { return STRING; }
  {MULTILINE_STRING_SQ} { return STRING; }
  {STRING_SQ}           { return STRING; }

  {SIMPLE_NUMBER}       { return SIMPLE_NUMBER; }
  {NUMBER}              { return NUMBER; }
  {DATE}                { return DATE; }

  {BOOLEAN}             { return BOOLEAN; }
  {SIMPLE_KEY}          { return SIMPLE_KEY; }

  "."                   { return DOT; }
  ","                   { return COMMA; }
  "="                   { return EQ; }
  "["                   { return LBRACKET; }
  "]"                   { return RBRACKET; }
  "{"                   { return LBRACE; }
  "}"                   { return RBRACE; }

  [^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}
