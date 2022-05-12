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


%xstate flowchart

%xstate flowchart_body
%xstate node_text
%xstate node_quoted_text
%xstate link_text
%xstate link_quoted_text
%xstate direction_value
%xstate style
%xstate link_style
%xstate link_style_target
%xstate style_opt
%xstate style_value
%xstate flowchart_class
%xstate flowchart_class_target
%xstate flowchart_class_val

%%

<YYINITIAL> {
  "%%{" { yypushstate(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r]+ { return EOL; }
  "%%" { yybegin(line_comment); return LINE_COMMENT; }
  "pie" { yybegin(pie); return Pie.PIE; }
  "journey" { yybegin(journey); return Journey.JOURNEY; }
  "flowchart" { yybegin(flowchart); return Flowchart.FLOWCHART; }
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

<pie, journey, flowchart, flowchart_body> {
  "%%{" { yypushstate(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  "%%" { yybegin(line_comment); return LINE_COMMENT; }
}
<pie, journey, flowchart_body> {
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
<flowchart, direction_value> {
	"LR" |
	"RL" |
	"TB" |
	"BT" |
	"TD" |
	"BR" |
	"<" |
	">" |
	"^" |
	"v" { return Flowchart.DIR; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r]+ { yybegin(flowchart_body); return EOL; }
}
<flowchart> {
  ";" { yybegin(flowchart_body); return SEMICOLON; }
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

//---flowchart--------------------------------------------------------------------
<flowchart_body> {
  "subgraph" { return Flowchart.SUBGRAPH; }
  "end" { return Flowchart.END; }

  "direction" { yybegin(direction_value); return Flowchart.DIRECTION; }

  "linkStyle" { yybegin(link_style); return Flowchart.LINK_STYLE; }
  "style" { yybegin(style); return Flowchart.STYLE; }
	"classDef" { yybegin(style); return Flowchart.CLASS_DEF; }

  "class" { yybegin(flowchart_class); return Flowchart.CLASS; }

	[^\s\n\r;:\[({><\^\|\-\=\.]+/[xo<]?\-\-|[xo<]?\=\=|[xo<]?\-\. { return Flowchart.NODE_ID; }
	[^\s\n\r;:\[({><\^\|\-\=\.]+ { return Flowchart.NODE_ID; }
	[\-\=\.] { return Flowchart.NODE_ID; }
	:|:: { return Flowchart.NODE_ID; }

	" & " { return Flowchart.AMPERSAND; }
  ":::" { yybegin(flowchart_class_val); return Flowchart.STYLE_SEPARATOR; }

	"[" { yybegin(node_text); return Flowchart.SQUARE_START; }
  "(" { yybegin(node_text); return Flowchart.ROUND_START; }
  "([" { yybegin(node_text); return Flowchart.STADIUM_START; }
	"[[" { yybegin(node_text); return Flowchart.SUBROUTINE_START; }
  "[(" { yybegin(node_text); return Flowchart.CYLINDER_START; }
  "((" { yybegin(node_text); return Flowchart.CIRCLE_START; }
  ">" { yybegin(node_text); return Flowchart.ASYMMETRIC_START; }
  "{" { yybegin(node_text); return Flowchart.DIAMOND_START; }
  "{{" { yybegin(node_text); return Flowchart.HEXAGON_START; }
  "[/" { yybegin(node_text); return Flowchart.TRAP_START; }
  "[\\" { yybegin(node_text); return Flowchart.INV_TRAP_START; }
  "(((" { yybegin(node_text); return Flowchart.DOUBLE_CIRCLE_START; }

	"|" { yybegin(link_text); return Flowchart.SEP; }

	[xo<]?\-\-+[-xo>] { return Flowchart.LINK; }
  [xo<]?\=\=+[=xo>] { return Flowchart.LINK; }
  [xo<]?\-?\.+\-[xo>]? { return Flowchart.LINK; }
  [xo<]?\-\- { yybegin(link_text); return Flowchart.START_LINK; }
  [xo<]?\=\= { yybegin(link_text); return Flowchart.START_LINK; }
  [xo<]?\-\. { yybegin(link_text); return Flowchart.START_LINK; }

  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<node_text> {
	[\"] { yybegin(node_quoted_text); return DOUBLE_QUOTE; }
	[^\s\n\r;\])\"]+ { return Flowchart.NODE_TEXT; }
  "]" { yybegin(flowchart_body); return Flowchart.SQUARE_END; }
  ")" { yybegin(flowchart_body); return Flowchart.ROUND_END; }
  "])" { yybegin(flowchart_body); return Flowchart.STADIUM_END; }
  "]]" { yybegin(flowchart_body); return Flowchart.SUBROUTINE_END; }
  ")]" { yybegin(flowchart_body); return Flowchart.CYLINDER_END; }
  "))" { yybegin(flowchart_body); return Flowchart.CIRCLE_END; }
  "}" { yybegin(flowchart_body); return Flowchart.DIAMOND_END; }
  "}}" { yybegin(flowchart_body); return Flowchart.HEXAGON_END; }
  "\\]" { yybegin(flowchart_body); return Flowchart.TRAP_END; }
  "/]" { yybegin(flowchart_body); return Flowchart.INV_TRAP_END; }
  ")))" { yybegin(flowchart_body); return Flowchart.DOUBLE_CIRCLE_END; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<node_quoted_text> {
	[\"] { yybegin(node_text); return DOUBLE_QUOTE; }
  [^\"]* { return Flowchart.NODE_TEXT; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<link_text> {
	[^\n\r;\[\]()\"|{}\-\=\.]+/\-\-+[-xo>]|\=\=+[=xo>]|\-?\.+\-[xo>] { yybegin(flowchart_body); return Flowchart.LINK_TEXT; }
  [^\n\r;\[\]()\"|{}\-\=\.]+ { return Flowchart.LINK_TEXT; }
  [^\n\r;\[\]()\"|{}\-\=\.]+/\| { return Flowchart.LINK_TEXT; }
  [\-\=\.] { return Flowchart.LINK_TEXT; }
	"\"" { yybegin(link_quoted_text); return DOUBLE_QUOTE; }
  "|" { yybegin(flowchart_body); return Flowchart.SEP; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<link_quoted_text> {
	[\"] { yybegin(link_text); return DOUBLE_QUOTE; }
  [^\"]* { return Flowchart.LINK_TEXT; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<style> {
	"default" { yybegin(style_opt); return Flowchart.DEFAULT; }
	[^\s;\n]+ { yybegin(style_opt); return Flowchart.STYLE_TARGET; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<link_style> {
  [^\S\r\n]+ { yybegin(link_style_target); return WHITE_SPACE; }
}
<link_style_target> {
	"default" { return Flowchart.STYLE_TARGET; }
	\d+ { return Flowchart.STYLE_TARGET; }
  "," { return COMMA; }
  [^\S\r\n]+ { yybegin(style_opt); return WHITE_SPACE; }
}
<style_opt> {
	[^\s,:;][^,:\n\r;]*/: { return Flowchart.STYLE_OPT; }
  ":" { yybegin(style_value); return COLON; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<style_value> {
	[^\S\n\r]+ { return WHITE_SPACE; }
	[^\s,:;][^,:\n\r;]*/[,;\n\r] { return Flowchart.STYLE_VAL; }
  "," { yybegin(style_opt); return COMMA; }
  ";" { yybegin(flowchart_body); return SEMICOLON; }
  [\n\r] { yybegin(flowchart_body); return EOL; }
}
<flowchart_class> {
  [^\S\n\r]+ { yybegin(flowchart_class_target); return WHITE_SPACE; }
}
<flowchart_class_target> {
	[^\s\n\r,;\[({><\^\|]+ { return Flowchart.NODE_ID; }
  "," { return COMMA; }

  [^\S\n\r]+ { yybegin(flowchart_class_val); return WHITE_SPACE; }
}
<flowchart_class_val> {
	[^\s;\n\r]+ { return Flowchart.STYLE_TARGET; }
  \s { yybegin(flowchart_body); return WHITE_SPACE; }
  ";" { yybegin(flowchart_body); return SEMICOLON; }
  \n\r { yybegin(flowchart_body); return EOL; }
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
