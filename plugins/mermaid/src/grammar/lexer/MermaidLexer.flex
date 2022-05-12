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


%xstate journey

%xstate journey_title
%xstate journey_title_value
%xstate journey_task
%xstate section
%xstate section_title

%%

<YYINITIAL> {
  "%%{" { yypushstate(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r]+ { return EOL; }
  "%%" { yybegin(line_comment); return LINE_COMMENT; }
  "pie" { yybegin(pie); return Pie.PIE; }
  "journey" { yybegin(journey); return Journey.JOURNEY; }
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

<pie, journey> {
  "%%{" { yypushstate(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  "%%" { yybegin(line_comment); return LINE_COMMENT; }
  [\n\r]+ { return EOL; }
  ";" { return SEMICOLON; }
}

<pie> {
	"title" { yybegin(pie_title); return TITLE; }
  [\"] { yybegin(double_quoted_string); return DOUBLE_QUOTE; }
  "showData" { return Pie.SHOW_DATA; }
  ":" { yybegin(value); return COLON; }
}
<journey> {
	"title" { yybegin(journey_title); return TITLE; }
  "section" { yybegin(section); return Journey.SECTION; }
  ":" { yybegin(journey_task); return COLON; }
  [^\s#:;][^#:\n;]*/: { yybegin(journey_task); return Journey.TASK_NAME; }
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

//---journey----------------------------------------------------------------------
<journey_title> {
  [^\S\n\r]+ { yybegin(journey_title_value); return WHITE_SPACE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<journey_title_value> {
	\s?(#[^\n\r]*)/[\n\r]? { return IGNORED; }
  [\n\r] { yybegin(journey); return EOL; }
  [^\n\r#;]+ { return TITLE_VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<section> {
  [^\S\n\r]+ { yybegin(section_title); return WHITE_SPACE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<section_title> {
	\s?(#[^\n\r]*)/[\n\r]? { return IGNORED; }
  [\n\r] { yybegin(journey); return EOL; }
  [^\n\r#:;]+ { return Journey.SECTION_TITLE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<journey_task> {
	(#[^\n\r]*)/[\n\r]? { return IGNORED; }
  ":" { return COLON; }
	[^\S\n\r]+ { return WHITE_SPACE; }
	[^\s#:;][^#:\n;]* { return Journey.TASK_DATA; }
  [\n\r] { yybegin(journey); return EOL; }
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
