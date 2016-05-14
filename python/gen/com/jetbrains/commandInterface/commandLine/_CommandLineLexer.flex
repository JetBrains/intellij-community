package com.jetbrains.commandInterface.commandLine;
import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import static com.jetbrains.commandInterface.commandLine.CommandLineElementTypes.*;

%%

%{
  public _CommandLineLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _CommandLineLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL="\r"|"\n"|"\r\n"
LINE_WS=[\ \t\f]
WHITE_SPACE=({LINE_WS}|{EOL})+

LITERAL_STARTS_FROM_LETTER=[:letter:]([a-zA-Z_0-9]|:|\\|"/"|\.|-)*
LITERAL_STARTS_FROM_DIGIT=[:digit:]([a-zA-Z_0-9]|:|\\|"/"|\.|-)*
LITERAL_STARTS_FROM_SYMBOL=([/\~\.]([a-zA-Z_0-9]|:|\\|"/"|\.|-)*)
SHORT_OPTION_NAME_TOKEN=-[:letter:]
LONG_OPTION_NAME_TOKEN=--[:letter:](-|[a-zA-Z_0-9])*

%%
<YYINITIAL> {
  {WHITE_SPACE}                     { return com.intellij.psi.TokenType.WHITE_SPACE; }

  "="                               { return EQ; }

  {LITERAL_STARTS_FROM_LETTER}      { return LITERAL_STARTS_FROM_LETTER; }
  {LITERAL_STARTS_FROM_DIGIT}       { return LITERAL_STARTS_FROM_DIGIT; }
  {LITERAL_STARTS_FROM_SYMBOL}      { return LITERAL_STARTS_FROM_SYMBOL; }
  {SHORT_OPTION_NAME_TOKEN}         { return SHORT_OPTION_NAME_TOKEN; }
  {LONG_OPTION_NAME_TOKEN}          { return LONG_OPTION_NAME_TOKEN; }

  [^] { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}
