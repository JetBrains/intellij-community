package com.github.firsttimeinforever.mermaid.lang.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.FlexLexer;
import java.util.Stack;
import static com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.*;
import static com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Pie;

%%

%{
  public _MermaidLexer() {
    this((java.io.Reader)null);
  }

  private Stack<Integer> stack = new Stack<Integer>();

  public void yypushstate(int newState) {
    stack.push(yystate());
    yybegin(newState);
  }

  public void yypopstate() {
    yybegin(stack.pop());
  }
%}

%public
%class _MermaidLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

%xstate double_quoted_string
%xstate line_comment

%xstate directive
%xstate directive_close
%xstate double_quoted_string_inside_directive


%xstate pie

%xstate pie_title
%xstate pie_title_value
%xstate value

%%

<YYINITIAL> {
  "%%{" { yypushstate(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r]+ { return EOL; }
  "%%" { yybegin(line_comment); return LINE_COMMENT; }
  "pie" { yybegin(pie); return Pie.PIE; }
  ";" { return SEMICOLON; }
}
<directive> {
  "}%%" { yypopstate(); return CLOSE_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r]+ { return EOL; }
  [\"] { yybegin(double_quoted_string_inside_directive); return DOUBLE_QUOTE; }
  "{" { return OPEN_CURLY; }
  "}" { return CLOSE_CURLY; }
  "," { return COMMA; }
  ":" { return COLON; }
  [^\s:,\{\}\%\"]+ { return DIRECTIVE_TEXT; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<double_quoted_string_inside_directive> {
  [\"] { yybegin(directive); return DOUBLE_QUOTE; }
  [^\"]* { return STRING_VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

<pie> {
  "%%{" { yypushstate(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  "%%" { yybegin(line_comment); return LINE_COMMENT; }
  [\n\r]+ { return EOL; }
  ";" { return SEMICOLON; }
	"title" { yybegin(pie_title); return TITLE; }
  [\"] { yybegin(double_quoted_string); return DOUBLE_QUOTE; }
  "showData" { return Pie.SHOW_DATA; }
  ":" { yybegin(value); return COLON; }
}

//---pie--------------------------------------------------------------------------
<pie_title> {
  [^\S\n\r]+ { yybegin(pie_title_value); return WHITE_SPACE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<pie_title_value> {
  [\n\r] { yybegin(pie); return EOL; }
  [^\n\r]* { return TITLE_VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

//--------------------------------------------------------------------------------
<line_comment> {
  [^\n\r]+ { yybegin(YYINITIAL); return COMMENT_TEXT; }
  [\n\r] { yybegin(YYINITIAL); return EOL; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

<double_quoted_string> {
  [\"] { yybegin(pie); return DOUBLE_QUOTE; }
  [^\"]* { return STRING_VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

<value> {
  [^\S\n\r]+ { return WHITE_SPACE; }
  [\d]+(:?\.[\d]+)? { yybegin(pie); return Pie.VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

[^] { return BAD_CHARACTER; }
