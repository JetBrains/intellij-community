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
BARE_KEY_OR_DATE={DATE}

NUMBER=[-+]?
 (0|[1-9](_?[0-9])*) // no leading zeros
 (\.[0-9](_?[0-9])*)?
 ([eE][-+]?[1-9](_?[0-9])*)?

DATE=[0-9]{4}-[0-9]{2}-[0-9]{2}
TIME=[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]+)?
OFFSET=[Zz]|[+-][0-9]{2}:[0-9]{2}
DATE_TIME= ({DATE} ([Tt]{TIME})? | {TIME}) {OFFSET}?

BARE_KEY=[0-9_\-a-zA-Z]+


ESCAPE = \\[^]
BASIC_STRING=\"
  ([^\r\n\"] | {ESCAPE})*
(\")?
MULTILINE_BASIC_STRING=(\"\"\")
  ([^\"] | {ESCAPE} | \"[^\"] | \"\"[^\"])*
(\"\"\")?
LITERAL_STRING=\'
  ([^\r\n\'] | {ESCAPE})*
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
  {BARE_KEY_OR_DATE}   { return BARE_KEY_OR_DATE; }
  {NUMBER}    { return NUMBER; }
  {BARE_KEY}  { return BARE_KEY; }
  {DATE_TIME} { return DATE_TIME; }

  {BASIC_STRING}   { return BASIC_STRING; }
  {LITERAL_STRING} { return LITERAL_STRING; }
  {MULTILINE_BASIC_STRING}   { return MULTILINE_BASIC_STRING; }
  {MULTILINE_LITERAL_STRING} { return MULTILINE_LITERAL_STRING; }

  "=" { return EQ; }
  "," { return COMMA; }
  "." { return DOT; }
  "[" { return L_BRACKET; }
  "]" { return R_BRACKET; }
  "{" { return L_CURLY; }
  "}" { return R_CURLY; }

  [^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}
