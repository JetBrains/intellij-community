package com.intellij.python.requirements.parser;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.ArrayDeque;
import java.util.Deque;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.intellij.python.requirements.parser.psi.RequirementsTypes.*;

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

%class RequirementsLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%eof{  return;
%eof}

COLON=":"
SEMICOLON=";"
COMMA=","
QUESTION_MARK="?"
EXCLAMATION_MARK="!"
AT="@"
DOLLAR_SIGN="$"
SHARP="#"
SLASH="/"
BACKSLASH="\\"
DOT="."
EQUAL="="
LSBRACE="["
RSBRACE="]"
LPARENTHESIS="("
RPARENTHESIS=")"

ENV_VARIABLE_START="${"

ENV_MARKER_NAME=[a-z_]+
AND="and"
OR="or"
IN_OP="in"
NOTIN_OP="not" {WHITE_SPACE}+ "in"

VERSION_CMP="<=" | "<" | "!=" | "==" | ">=" | ">" | "~=" | "==="
VERSION=[a-zA-Z0-9\-_.*+!]+

URI_SCHEME=[a-z]+ ("+" [a-z]+)? "://"
RAW_GIT_URI_SCHEME="git+"[^\s:]+ "@"
BZR_LAUNCHPAD_SCHEME="bzr+lp:"
URI_UNRESERVED=[a-zA-Z0-9\-._~]+
URI_SUB_DELIMITER=[!$&'()*+,=]  // semicolon deliberately missing as it starts an environment marker
PCT_ENCODED="%" [0-9]{2}
URI_UNRESERVED=[a-zA-Z0-9\-._~]
URI_FRAGMENT_ARG=[a-zA-Z]+

LETTER=[a-zA-Z]
DIGIT=[0-9]
EOL=("\n" | "\r" | "\r\n")
WHITE_SPACE=[ \t]+
SHORT_OPTION_IDENTIFIER="-" [a-z]
LONG_OPTION_IDENTIFIER="--" [a-z\-]+
PACKAGE_NAME=[a-zA-Z0-9][a-zA-Z0-9\-_.]*
DQUOTE="\""
SQUOTE="'"

%state VERSION_CMP_STATE
%state OPTION_STATE
%state OPTION_VALUE_STATE
%state URI_STATE
%state URI_FRAGMENT_STATE
%state PATH_STATE
%state EXTRAS_STATE
%state ENV_MARKER_STATE
%state ENV_VARIABLE_STATE
%state DQUOTE_STATE
%state SQUOTE_STATE

%%

<YYINITIAL> {
    "-e" | "--editable"       { return EDITABLE_OPTION_IDENTIFIER; }
    {LONG_OPTION_IDENTIFIER}  { yypush(OPTION_STATE); return LONG_OPTION_IDENTIFIER; }
    {SHORT_OPTION_IDENTIFIER} { yypush(OPTION_STATE); return SHORT_OPTION_IDENTIFIER; }
    {URI_SCHEME}              { yypush(URI_STATE); yypushback(3); return URI_SCHEME; }
    {RAW_GIT_URI_SCHEME}      { yypush(URI_STATE); yypushback(1); return GIT_URI_SCHEME; }
    {BZR_LAUNCHPAD_SCHEME}    { return BZR_LAUNCHPAD_SCHEME; }
    {LETTER}{COLON}           { yypush(PATH_STATE); return DRIVE_LETTER; }
    {SLASH} | {DOT}           { yypushback(yylength()); yypush(PATH_STATE); }
    {PACKAGE_NAME}            { return PACKAGE_NAME_TOKEN; }
    {SEMICOLON}               { yypush(ENV_MARKER_STATE); return SEMICOLON; }
    {AT}                      { return AT; }
    {COMMA}                   { return COMMA; }
    {LPARENTHESIS}            { return LPARENTHESIS; }
    {RPARENTHESIS}            { return RPARENTHESIS; }
    {VERSION_CMP}             { yypush(VERSION_CMP_STATE); return VERSION_CMP_TOKEN; }
    {LSBRACE}                 { yypush(EXTRAS_STATE); return LSBRACE; }
    {ENV_VARIABLE_START}      { yypush(ENV_VARIABLE_STATE); return ENV_VARIABLE_START; }
    {SHARP}.*                 { yyinitial(); return COMMENT; }
}

<VERSION_CMP_STATE> {
    {VERSION} { yypop(); return VERSION_TOKEN; }
}

<OPTION_STATE> {
    {EQUAL}       { yypop(); yypush(OPTION_VALUE_STATE); return EQUAL; }
    {WHITE_SPACE} { yypop(); yypush(OPTION_VALUE_STATE); return WHITE_SPACE; }
    [\S]          { yypop(); yypushback(1); yypush(OPTION_VALUE_STATE); }
}

<OPTION_VALUE_STATE> {
    [\S]+    { yypop(); return OPTION_VALUE_TOKEN; }
    {DQUOTE} { yypop(); yypush(DQUOTE_STATE); return DQUOTE; }
    {SQUOTE} { yypop(); yypush(SQUOTE_STATE); return SQUOTE; }
}

<URI_STATE> {
    // While the semicolon is a legal URI character, in requirements it starts an environment marker
    {DIGIT}                { return DIGIT; }
    {LSBRACE}              { return LSBRACE; }
    {RSBRACE}              { return RSBRACE; }
    {PCT_ENCODED}          { return PCT_ENCODED; }
    {COLON}                { return COLON; }
    {DOT}                  { return DOT; }
    {AT}                   { return AT; }
    {SLASH}                { return SLASH; }
    {QUESTION_MARK}        { return QUESTION_MARK; }
    {SEMICOLON}            { yypop(); yypush(ENV_MARKER_STATE); return SEMICOLON; }
    {URI_SUB_DELIMITER}    { return URI_SUB_DELIMITER; }
    {URI_UNRESERVED}       { return URI_UNRESERVED; }
    {WHITE_SPACE}{SHARP}.* { yyinitial(); return COMMENT; }
    {SHARP}                { return SHARP; }
}

<PATH_STATE> {
    {SLASH} | {BACKSLASH} { return PATH_SEPARATOR; }
    {LSBRACE}             { yypop(); yypush(EXTRAS_STATE); return LSBRACE; }
    [^\s/\\\[\]]+         { return PATH_SEGMENT; }
    {WHITE_SPACE}         { yypop(); return WHITE_SPACE; }
}

<EXTRAS_STATE> {
    {PACKAGE_NAME} { return PACKAGE_NAME_TOKEN; }
    {COMMA}        { return COMMA; }
    {RSBRACE}      { yypop(); return RSBRACE; }
}

<ENV_MARKER_STATE> {
  {VERSION_CMP}     { return VERSION_CMP_TOKEN; }
  {IN_OP}           { return IN_OP; }
  {NOTIN_OP}        { return NOTIN_OP; }
  {AND}             { return AND; }
  {OR}              { return OR; }
  {LPARENTHESIS}    { return LPARENTHESIS; }
  {RPARENTHESIS}    { return RPARENTHESIS; }
  {ENV_MARKER_NAME} { return ENV_MARKER_NAME; }
  {DQUOTE}          { yypush(DQUOTE_STATE); return DQUOTE; }
  {SQUOTE}          { yypush(SQUOTE_STATE); return SQUOTE; }
}

<ENV_VARIABLE_STATE> {
    [^}]+ { return ENV_VARIABLE_NAME; }
    "}"   { yypop(); return ENV_VARIABLE_END; }
}

<DQUOTE_STATE> {
    {DQUOTE} { yypop(); return DQUOTE; }
    [^\"]+   { return QUOTED_STRING_TOKEN; }
}

<SQUOTE_STATE> {
    {SQUOTE} { yypop(); return SQUOTE; }
    [^']+    { return QUOTED_STRING_TOKEN; }
}

{WHITE_SPACE}{SHARP}.* { yyinitial(); return COMMENT; }
{WHITE_SPACE}          { return WHITE_SPACE; }
{BACKSLASH}{EOL}       { return WHITE_SPACE; }
{EOL}                  { yyinitial(); return EOL; }
[^]                    { yyinitial(); return BAD_CHARACTER; }
