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
%caseless
%ignorecase

%xstate double_quoted_string
%xstate line_comment

%xstate directive
%xstate directive_close
//%xstate double_quoted_string_inside_directive


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


%xstate sequence

%xstate sequence_id
%xstate sequence_alias
%xstate sequence_message
%xstate sequence_links
%xstate sequence_links_values


%xstate class_diagram

%xstate struct
%xstate generic
%xstate simple_direction_value
%xstate annotation
%xstate class_name
%xstate class_in_relation
%xstate description


%xstate state_diagram

%xstate state_statement
%xstate note_statement
%xstate note_content

%xstate entity_relationship
%xstate entity_attributes
%xstate relationship_description

%%

<YYINITIAL> {
  "%%{" { yypushstate(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r]+ { return EOL; }
  "%%" { yypushstate(line_comment); return LINE_COMMENT; }
  "pie" { yybegin(pie); return Pie.PIE; }
  "journey" { yybegin(journey); return Journey.JOURNEY; }
  "flowchart" { yybegin(flowchart); return Flowchart.FLOWCHART; }
  "sequenceDiagram" { yybegin(sequence); return Sequence.SEQUENCE; }
  "classDiagram" { yybegin(class_diagram); return ClassDiagram.CLASS_DIAGRAM; }
  "stateDiagram-v2" |
  "stateDiagram" { yybegin(state_diagram); return StateDiagram.STATE_DIAGRAM; }
  "erDiagram" { yybegin(entity_relationship); return EntityRelationship.ENTITY_RELATIONSHIP; }
  ";" { return SEMICOLON; }
}
<directive> {
  "}%%" { yypopstate(); return CLOSE_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r]+ { return EOL; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  "{" { return OPEN_CURLY; }
  "}" { return CLOSE_CURLY; }
  "," { return COMMA; }
  ":" { return COLON; }
  [^\s:,\{\}\%\"]+ { return DIRECTIVE_TEXT; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
//<double_quoted_string_inside_directive> {
//  [\"] { yybegin(directive); return DOUBLE_QUOTE; }
//  [^\"]* { return STRING_VALUE; }
//  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
//}

<pie, journey, flowchart, flowchart_body, sequence, class_diagram, class_name, struct, state_diagram, state_statement, entity_relationship, entity_attributes, note_content> {
  "%%{" { yypushstate(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  "%%" { yypushstate(line_comment); return LINE_COMMENT; }
}
<pie, journey, flowchart_body, sequence, state_diagram, state_statement, class_diagram, class_name, struct, note_content, entity_relationship, entity_attributes> {
  [\n\r]+ { return EOL; }
  ";" { return SEMICOLON; }
}

<pie> {
	"title" { yybegin(pie_title); return TITLE; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  "showData" { return Pie.SHOW_DATA; }
  ":" { yybegin(value); return COLON; }
}
<journey> {
	"title" { yybegin(journey_title); return TITLE; }
  "section" { yybegin(section); return Journey.SECTION; }
  ":" { yybegin(journey_task); return COLON; }
  [^\s#:;][^#:\n;]*/: { yybegin(journey_task); return Journey.TASK_NAME; }
}
<flowchart, direction_value, simple_direction_value> {
	"LR" |
	"RL" |
	"TB" |
	"BT" { return DIR; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<direction_value, simple_direction_value> {
  [\n\r]+ { yypopstate(); return EOL; }
}
<flowchart> {
  [\n\r]+ { yybegin(flowchart_body); return EOL; }
}
<flowchart, direction_value> {
	"TD" |
	"BR" |
	"<" |
	">" |
	"^" |
	"v" { return DIR; }
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
  "end" { return END; }

  "direction" { yypushstate(direction_value); return DIRECTION; }

  "linkStyle" { yybegin(link_style); return Flowchart.LINK_STYLE; }
  "style" { yybegin(style); return Flowchart.STYLE; }
	"classDef" { yybegin(style); return Flowchart.CLASS_DEF; }

  "class" { yybegin(flowchart_class); return CLASS; }

	[^\s\n\r;:\[({><\^\|\-\=\.]+/[xo<]?\-\-|[xo<]?\=\=|[xo<]?\-\. { return ID; }
	[^\s\n\r;:\[({><\^\|\-\=\.]+ { return ID; }
	[\-\=\.] { return ID; }
	:|:: { return ID; }

	" & " { return Flowchart.AMPERSAND; }
  ":::" { yybegin(flowchart_class_val); return STYLE_SEPARATOR; }

	"[" { yybegin(node_text); return OPEN_SQUARE; }
  "(" { yybegin(node_text); return OPEN_ROUND; }
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

	[xo<]?\-\-+[-xo>] { return ARROW; }
  [xo<]?\=\=+[=xo>] { return ARROW; }
  [xo<]?\-?\.+\-[xo>]? { return ARROW; }
  [xo<]?\-\- { yybegin(link_text); return Flowchart.START_ARROW; }
  [xo<]?\=\= { yybegin(link_text); return Flowchart.START_ARROW; }
  [xo<]?\-\. { yybegin(link_text); return Flowchart.START_ARROW; }

  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<node_text> {
	[\"] { yybegin(node_quoted_text); return DOUBLE_QUOTE; }
	[^\s\n\r;\])\"\}]+[^\s\n\r;\])\"\}/\\] { return ALIAS; }
  "]" { yybegin(flowchart_body); return CLOSE_SQUARE; }
  ")" { yybegin(flowchart_body); return CLOSE_ROUND; }
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
  [^\"]* { return ALIAS; }
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
	[^\s\n\r,;\[({><\^\|]+ { return ID; }
  "," { return COMMA; }

  [^\S\n\r]+ { yybegin(flowchart_class_val); return WHITE_SPACE; }
}
<flowchart_class_val> {
	[^\s;\n\r]+ { return Flowchart.STYLE_TARGET; }
  \s { yybegin(flowchart_body); return WHITE_SPACE; }
  ";" { yybegin(flowchart_body); return SEMICOLON; }
  \n\r { yybegin(flowchart_body); return EOL; }
}

//---sequence---------------------------------------------------------------------
<sequence> {
	"participant" { yybegin(sequence_id); return Sequence.PARTICIPANT; }
  "actor" { yybegin(sequence_id); return Sequence.ACTOR; }
  "activate" { return Sequence.ACTIVATE; }
  "deactivate" { return Sequence.DEACTIVATE; }
  "Note" { return NOTE; }
  "right of" { return RIGHT_OF; }
  "left of" { return LEFT_OF; }
  "over" { return Sequence.OVER; }
	"loop" { yybegin(sequence_message); return Sequence.LOOP; }
  "alt" { yybegin(sequence_message); return Sequence.ALT; }
  "else" { yybegin(sequence_message); return Sequence.ELSE; }
  "opt" { yybegin(sequence_message); return Sequence.OPT; }
  "par" { yybegin(sequence_message); return Sequence.PAR; }
  "and" { yybegin(sequence_message); return Sequence.AND; }
  "rect" { yybegin(sequence_message); return Sequence.RECT; }
  "end" { return END; }
  "autonumber" { return Sequence.AUTONUMBER; }
  "link" { return Sequence.LINK; }
  "links" { yybegin(sequence_links); return Sequence.LINKS; }

	[^\+\->:\s,;]+ { return ID; }

  ":" { yybegin(sequence_message); return COLON; }
  "+" { return PLUS; }
  "-" { return MINUS; }
  "," { return COMMA; }

  "->>" { return Sequence.SOLID_ARROW; }
  "-->>" { return Sequence.DOTTED_ARROW; }
  "->" { return Sequence.SOLID_OPEN_ARROW; }
  "-->" { return Sequence.DOTTED_OPEN_ARROW; }
  \-[x] { return Sequence.SOLID_CROSS; }
  \-\-[x] { return Sequence.DOTTED_CROSS; }
  \-[\)] { return Sequence.SOLID_POINT; }
  \-\-[\)] { return Sequence.DOTTED_POINT; }
}
<sequence_id, sequence_alias, sequence_message, sequence> {
	\s?(#[^\n\r]*)/[\n\r]? { yybegin(sequence); return IGNORED; }
  \n { yybegin(sequence); return EOL; }
  ";" { yybegin(sequence); return SEMICOLON; }
}
<sequence_id> {
	"as" { yybegin(sequence_alias); return AS; }
	[^\+\->:\s,;]+ { return ID; }
	[^\S\n\r]+ { return WHITE_SPACE; }
}
<sequence_alias> {
  [^#\s;]* { return ALIAS; }
	[^\S\n\r]+ { return WHITE_SPACE; }
}
<sequence_message> {
	[^#\n;]* { return Sequence.MESSAGE; }
}
<sequence_links> {
	[^\+\->:\s,;]+ { return ID; }
  ":" { yybegin(sequence_links_values); return COLON; }
	[^\S\n\r]+ { return WHITE_SPACE; }
}
<sequence_links_values> {
  "{" { return OPEN_CURLY; }
  "}" { yybegin(sequence); return CLOSE_CURLY; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  ":" { return COLON; }
  "," { return COMMA; }
	[^\S\n\r]+ { return WHITE_SPACE; }
}

//---class------------------------------------------------------------------------
<class_diagram> {
	"class" { yypushstate(class_name); return CLASS; }
  "direction" { yypushstate(direction_value); return DIRECTION; }
  [\w_]+ { yypushstate(class_name); return ID; }
}
<struct> {
  "}" { yybegin(class_diagram); return CLOSE_CURLY; }
}
<struct, class_name> {
  [\w]+ { return ID; }
}
<class_diagram, class_name> {
  "{" { yybegin(struct); return OPEN_CURLY; }
  ":::" { return STYLE_SEPARATOR; }
}
<class_diagram, struct, class_name> {
  [~] { yypushstate(generic); return TILDA; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  "<<" { yypushstate(annotation); return ANNOTATION_START; }

  ":" { return COLON; }
  "+" { return PLUS; }
  "-" { return MINUS; }
  "#" { return POUND; }
  "*" { return STAR; }
  "$" { return DOLLAR; }
  "(" { return OPEN_ROUND; }
  ")" { return CLOSE_ROUND; }
  "," { return COMMA; }
}
<generic> {
	"~" { yypopstate(); return TILDA; }
  [^~]* { return ClassDiagram.GENERIC_TYPE; }
}
<annotation> {
	\w+ { return ANNOTATION_VALUE; }
	">>" { yypopstate(); return ANNOTATION_END; }
}
<class_name> {
  [\w]+ { return ID; }

  "<|" { return ClassDiagram.EXTENSION_START; }
  "<" { return ClassDiagram.DEPENDENCY_START; }
  [\*]/[\-\.] { return ClassDiagram.COMPOSITION; }
  "o"/[\-\.] { return ClassDiagram.AGGREGATION; }
  "--" { yybegin(class_in_relation); return ClassDiagram.LINE; }
  ".." { yybegin(class_in_relation); return ClassDiagram.DOTTED_LINE; }
}
<class_in_relation> {
  ":" { yypushstate(description); return COLON; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }

  "|>" { return ClassDiagram.EXTENSION_END; }
  ">" { return ClassDiagram.DEPENDENCY_END; }
  [\*] { return ClassDiagram.COMPOSITION; }
  "o" { return ClassDiagram.AGGREGATION; }
  [\w_]+ { return ID; }

  [\n\r] { yybegin(class_diagram); return EOL; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<description> {
	[^\n\r]+ { yypopstate(); return LABEL; }
}

//---state------------------------------------------------------------------------
<state_statement> {
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  "{" { return OPEN_CURLY; }
  "}" { return CLOSE_CURLY; }
  "as" { return AS; }
}
<state_diagram, state_statement> {
  "<<" { yypushstate(annotation); return ANNOTATION_START; }

  "state" { yybegin(state_statement); return StateDiagram.STATE; }
  "note" { yypushstate(note_statement); return NOTE; }
  "direction" { yypushstate(simple_direction_value); return DIRECTION; }

  ":" { yypushstate(description); return COLON; }
  "["/"*" { return OPEN_SQUARE; }
  "*"/"]" { return STAR; }
  "]" { return CLOSE_SQUARE; }
  [^\-\{\[\s\":,\.,<>][^\-\{\}\s\":\.,<>]* { return ID; }
	"-->" { return ARROW; }
	"-" { return MINUS; }
}
<note_statement> {
  "right of" { return RIGHT_OF; }
  "left of" { return LEFT_OF; }
  [^\-\{\[\s\":\.,<>][^\-\{\}\s\":\.,<>]* { yybegin(note_content); return ID; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<note_content> {
  "end note" { yypopstate(); return END; }
	":" { return COLON; }
  ([^\s:][^\n\r]?)* { return NOTE_CONTENT; }
}

//---entity-relationship----------------------------------------------------------
<entity_relationship> {
	[a-zA-Z][\w\-]* { return ID; }
  \|o { return EntityRelationship.ZERO_OR_ONE_LEFT; }
  \}o { return EntityRelationship.ZERO_OR_MORE_LEFT; }
  \}\| { return EntityRelationship.ONE_OR_MORE_LEFT; }
  \|\| {return EntityRelationship.ONLY_ONE; }
  o\| { return EntityRelationship.ZERO_OR_ONE_RIGHT; }
  o\{ { return EntityRelationship.ZERO_OR_MORE_RIGHT; }
  \|\{ { return EntityRelationship.ONE_OR_MORE_RIGHT; }
  \-\- { return EntityRelationship.IDENTIFYING; }
  \.\. { return EntityRelationship.NON_IDENTIFYING;}
  \.\- { return EntityRelationship.NON_IDENTIFYING; }
  \-\. { return EntityRelationship.NON_IDENTIFYING; }

  ":" { yypushstate(relationship_description); return COLON; }
  "{" { yybegin(entity_attributes); return OPEN_CURLY; }
}
<entity_attributes> {
	"FK" | "PK" { return EntityRelationship.ATTR_KEY; }
	[a-zA-Z][\w\-]* { return ID; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  "}" { yybegin(entity_relationship); return CLOSE_CURLY; }
}
<relationship_description> {
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [^\s\"]+ { return LABEL; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r] { yybegin(entity_relationship); return EOL; }
}

//--------------------------------------------------------------------------------
<line_comment> {
  [^\n\r]+ { yypopstate(); return COMMENT_TEXT; }
  [\n\r] { yypopstate(); return EOL; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

<double_quoted_string> {
  [\"] { yypopstate(); return DOUBLE_QUOTE; }
  [^\"]* { return STRING_VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

<value> {
  [^\S\n\r]+ { return WHITE_SPACE; }
  [\d]+(:?\.[\d]+)? { yybegin(pie); return Pie.VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

[^] { return BAD_CHARACTER; }
