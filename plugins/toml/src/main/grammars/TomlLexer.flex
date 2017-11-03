package org.toml.lang.parse;
import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import static org.toml.lang.psi.TomlElementTypes.*;

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

WHITE_SPACE=([\ \t\f]|("\r"|"\n"|"\r\n"))+
COMMENT=#[^\n\r]*

BOOLEAN=true|false

BARE_KEY_OR_NUMBER=-?[0-9]+
NUMBER=[-+]?
 (0|[1-9](_?[0-9])*) // no leading zeros
 (\.[0-9](_?[0-9])*)?
 ([eE][-+]?[1-9](_?[0-9])*)?
BARE_KEY=[0-9_\-a-zA-Z]+

DATE=[0-9]{4}-[0-9]{2}-[0-9]{2}
  ([Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?)?
  ([Zz]|[+-][0-9]{2}:[0-9]{2})?

ESCAPE = \\[^]
BASIC_STRING=\"
  ([^\r\n\"] | {ESCAPE})*
(\")?
MULTILINE_BASIC_STRING=(\"\"\")
  ([^\"] | {ESCAPE} | \"[^\"] | \"\"[^\"])*
(\"\"\")?
LITERAL_STRING=\'
  ([^\r\n\"] | {ESCAPE})*
(\')?
MULTILINE_LITERAL_STRING=(\'\'\')
  ([^\'] | {ESCAPE} | \'[^\'] | \'\'[^\'])*
(\'\'\')?


%%
<YYINITIAL> {
  {WHITE_SPACE} { return com.intellij.psi.TokenType.WHITE_SPACE; }
  {COMMENT} { return COMMENT; }

  {BOOLEAN} { return BOOLEAN; }

  {BARE_KEY_OR_NUMBER} { return BARE_KEY_OR_NUMBER; }
  {NUMBER} { return NUMBER; }
  {BARE_KEY} { return BARE_KEY; }

  {DATE} { return DATE; }

  {BASIC_STRING}   { return BASIC_STRING; }
  {LITERAL_STRING} { return LITERAL_STRING; }
  {MULTILINE_BASIC_STRING}   { return MULTILINE_BASIC_STRING; }
  {MULTILINE_LITERAL_STRING} { return MULTILINE_LITERAL_STRING; }

  "=" { return EQ; }


//  "."                   { return DOT; }
//  ","                   { return COMMA; }
//  "["                   { return LBRACKET; }
//  "]"                   { return RBRACKET; }
//  "{"                   { return LBRACE; }
//  "}"                   { return RBRACE; }

  [^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}
