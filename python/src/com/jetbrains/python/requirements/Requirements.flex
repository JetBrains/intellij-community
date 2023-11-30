package com.jetbrains.python.requirements;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayDeque;
import java.util.Deque;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.jetbrains.python.requirements.psi.RequirementsTypes.*;

%%

%{
  public RequirementsLexer() {
      this((java.io.Reader)null);
  }

  Deque<Integer> stack = new ArrayDeque<>();

  public final void yypush(int newState) {
      yybegin(newState);
      stack.push(newState);
  }

  public final int yypop() {
      if (stack.size() == 0) {
          yyinitial();
          return YYINITIAL;
      }

      int state = stack.pop();

      if (stack.peek() == null) {
          yyinitial();
      } else {
          yybegin(stack.peek());
      }

      return state;
  }

  public final void yyinitial() {
      stack.clear();
      yypush(YYINITIAL);
  }
%}

%public
%class RequirementsLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%eof{  return;
%eof}

PLUS="+"
MINUS="-"
ASTERISK="*"
COLON=":"
SEMICOLON=";"
COMMA=","
QUESTION_MARK="?"
LSBRACE="["
RSBRACE="]"
EXCLAMATION_MARK="!"
AT="@"
DOLLAR_SIGN="$"
SHARP="#"
PERCENT_SIGN="%"
SLASH="/"
BACKSLASH="\\"
DOT="."
UNDERSCORE="_"
LBRACE="{"
RBRACE="}"
EQUAL="="
AMPERSAND="&"
TILDA="~"

COMMENT={SHARP}.*
LETTER=[a-zA-Z]
DIGIT=[0-9]
EOL=[\n|\r|\r\n]
WHITE_SPACE=[^\S\r\n]+
IDENTIFIER=(
             ({LETTER} | {DIGIT})
             (
               ({LETTER} | {DIGIT} | {MINUS} | {UNDERSCORE} | {DOT})*
               ({LETTER} | {DIGIT})
             )*
           )
VERSION_CMP=("<"{EQUAL} | "<" | "!" {EQUAL}
            | {EQUAL}{EQUAL}{EQUAL} | {EQUAL}{EQUAL}
            | ">"{EQUAL} | ">" | {TILDA}{EQUAL})

VERSION=(
          ({LETTER} | {DIGIT} | {MINUS} | {UNDERSCORE}
          | {DOT} | {ASTERISK} | {PLUS} | {EXCLAMATION_MARK}
          )+
        )

LPARENTHESIS = "("
RPARENTHESIS = ")"

ENV_VAR=("python_version"
        | "python_full_version"
        | "os_name"
        | "sys_platform"
        | "platform_release"
        | "platform_system"
        | "platform_version"
        | "platform_machine"
        | "platform_python_implementation"
        | "implementation_name"
        | "implementation_version"
        | "extra" // ONLY when defined by a containing layer
        )

PYTHON_STR_C=({WHITE_SPACE} | {LETTER} | {DIGIT} | {LPARENTHESIS}
             | {RPARENTHESIS}
             | {DOT} | {LBRACE} | {RBRACE} | {MINUS} | {UNDERSCORE}
             | {ASTERISK} | {SHARP} | {COLON}
             | {SEMICOLON} | {COMMA} | {SLASH} | {QUESTION_MARK}
             | {LSBRACE}
             | {RSBRACE} | {EXCLAMATION_MARK} | {TILDA} | "`" | {AT}
             | {DOLLAR_SIGN} | {PERCENT_SIGN} | "^" | {AMPERSAND}
             | {EQUAL} | {PLUS} | "|" | "<" | ">" )
DQUOTE = "\""
SQUOTE = "'"

SUB_DELIMS=({EXCLAMATION_MARK}
           | {AMPERSAND}
           | {SQUOTE}
           | {LPARENTHESIS}
           | {RPARENTHESIS}
           | {ASTERISK}
           | {PLUS}
           | {COMMA}
           | {SEMICOLON}
           | {EQUAL})

HEX = [a-fA-F0-9]+

HASH = "hash"

%state WAITING_VERSION
%state DQUOTE_STR
%state SQUOTE_STR
%state QUOTED_MARK
%state URI
%state REQ
%state SHORT_OPTION_STATE
%state LONG_OPTION_STATE
%state BINARY
%state WAITING_HASH
%state WAITING_EQUAL
%state WAITING_ALG
%state WAITING_COLON
%state WAITING_HASH_VALUE
%state WAITING_IDENTIFIER

%%

<YYINITIAL> {
    {COMMENT}                { return COMMENT; }
    "-"                      { yypush(SHORT_OPTION_STATE); return SHORT_OPTION; }
    "--"                     { yypush(LONG_OPTION_STATE); return LONG_OPTION; }
    {IDENTIFIER}             { yypush(REQ); yypushback(yylength()); }
    {DOT}                    { yypush(REQ); yypushback(yylength()); }
}

<SHORT_OPTION_STATE> {
    "i"                      { yypush(URI); return INDEX_URL; }
    "c"                      { yypush(URI); return CONSTRAINT; }
    "r"                      { yypush(URI); return REFER; }
    "e"                      { yypush(URI); return EDITABLE; }
    "f"                      { yypush(URI); return FIND_LINKS; }
}

<LONG_OPTION_STATE> {
    "index-url"              { yypush(URI); return INDEX_URL; }
    "extra-index-url"        { yypush(URI); return EXTRA_INDEX_URL; }
    "no-index"               { return NO_INDEX; }
    "constraint"             { yypush(URI); return CONSTRAINT; }
    "requirement"            { yypush(URI); return REFER; }
    "editable"               { yypush(URI); return EDITABLE; }
    "find-links"             { yypush(URI); return FIND_LINKS; }
    "no-binary"              { yypush(BINARY); return NO_BINARY; }
    "only-binary"            { yypush(BINARY); return ONLY_BINARY; }
    "prefer-binary"          { return PREFER_BINARY; }
    "require-hashes"         { return REQUIRE_HASHES; }
    "pre"                    { return PRE; }
    "trusted-host"           { yypush(URI); return TRUSTED_HOST; }
    "use-feature"            { yypush(WAITING_IDENTIFIER); return USE_FEATURE; }
}

<BINARY> {
    ":all:"                  { return BINARY_ALL; }
    ":none:"                 { return BINARY_NONE; }
    {IDENTIFIER}             { return IDENTIFIER; }
    {COMMA}                  { return COMMA; }
}

<WAITING_IDENTIFIER> {
    {IDENTIFIER}             { yyinitial(); return IDENTIFIER; }
}

<WAITING_HASH> {
    {HASH}                   { yypush(WAITING_EQUAL); return HASH; }
}

<WAITING_EQUAL> {
    {EQUAL}                  { yypush(WAITING_ALG); return EQUAL; }
}

<WAITING_ALG> {
    {IDENTIFIER}             { yypush(WAITING_COLON); return IDENTIFIER; }
}

<WAITING_COLON> {
    {COLON}                  { yypush(WAITING_HASH_VALUE); return COLON; }
}

<WAITING_HASH_VALUE> {
    {HEX}                    { yypush(REQ); return HEX; }
    {WHITE_SPACE}            { yypush(REQ); return WHITE_SPACE; }
    {COMMENT}                { yypush(REQ); return COMMENT; }
}

<REQ> {
    {IDENTIFIER}{PLUS}       { yypush(URI); yypushback(yylength()); }
    {IDENTIFIER}{COLON}      { yypush(URI); yypushback(yylength()); }
    {IDENTIFIER}{SLASH}      { yypush(URI); yypushback(yylength()); }
    {IDENTIFIER}             { return IDENTIFIER; }
    {VERSION_CMP}            { yypush(WAITING_VERSION); return VERSION_CMP; }
    {AT}                     { yypush(URI); return AT; }
    {COMMA}                  { return COMMA; }
    {LPARENTHESIS}           { return LPARENTHESIS; }
    {RPARENTHESIS}           { return RPARENTHESIS; }
    {LSBRACE}                { return LSBRACE; }
    {RSBRACE}                { return RSBRACE; }
    {SEMICOLON}              { yypush(QUOTED_MARK); return SEMICOLON; }
    {DOT}                    { yypush(URI); yypushback(yylength()); }

    {WHITE_SPACE}{COMMENT}   { yyinitial(); return COMMENT; }
    "--"                     { yypush(WAITING_HASH); return LONG_OPTION; }
}

<WAITING_VERSION> {
    {VERSION}                { yypop(); return VERSION; }
    {COMMENT}                { yypop(); return COMMENT; }
}

<QUOTED_MARK> {
    {ENV_VAR}                { return ENV_VAR; }

    {DQUOTE}                 { yypush(DQUOTE_STR); return DQUOTE; }
    {SQUOTE}                 { yypush(SQUOTE_STR); return SQUOTE; }
    {VERSION_CMP}            { return VERSION_CMP; }
    "in"                     { return IN; }
    "not"                    { return NOT; }
    "and"                    { return AND; }
    "or"                     { return OR; }
    {LPARENTHESIS}           { return LPARENTHESIS; }
    {RPARENTHESIS}           { return RPARENTHESIS; }

    {COMMENT}                { yyinitial(); return COMMENT; }
}

<URI> {
    {AT}                     { return AT; }
    {COLON}                  { return COLON; }
    {DIGIT}                  { return DIGIT; }
    {DOT}                    { return DOT; }
    {LETTER}                 { return LETTER; }
    {LSBRACE}                { return LSBRACE; }
    {RSBRACE}                { return RSBRACE; }
    {MINUS}                  { return MINUS; }
    {PLUS}                   { return PLUS; }
    {PERCENT_SIGN}           { return PERCENT_SIGN; }
    {QUESTION_MARK}          { return QUESTION_MARK; }
    {SHARP}                  { return SHARP; }
    {SLASH}                  { return SLASH; }
    {SUB_DELIMS}             { return SUB_DELIMS; }
    {TILDA}                  { return TILDA; }
    {UNDERSCORE}             { return UNDERSCORE; }
    {LBRACE}                 { return LBRACE; }
    {RBRACE}                 { return RBRACE; }
    {DOLLAR_SIGN}            { return DOLLAR_SIGN; }

    {WHITE_SPACE}{SEMICOLON} { yypush(QUOTED_MARK); return SEMICOLON; }
    {WHITE_SPACE}{COMMENT}   { yyinitial(); return COMMENT; }
}

<DQUOTE_STR> {
    {DQUOTE}                     { yypop(); return DQUOTE; }
    ({PYTHON_STR_C} | {SQUOTE})+ { return PYTHON_STR_C; }
}

<SQUOTE_STR> {
    {SQUOTE}                     { yypop(); return SQUOTE; }
    ({PYTHON_STR_C} | {DQUOTE})+ { return PYTHON_STR_C; }
}

{WHITE_SPACE}                { return WHITE_SPACE; }
{BACKSLASH}{EOL}             { }

{EOL}                        { yyinitial(); return EOL; }

[^]                          { yyinitial(); return BAD_CHARACTER; }
