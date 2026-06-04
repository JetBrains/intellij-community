package com.intellij.mermaid.lang.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.FlexLexer;
import java.util.Stack;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.*;
import static com.intellij.mermaid.lang.lexer.MermaidTokens.Pie;

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
//%caseless
//%ignorecase

%state double_quoted_string
%state back_quoted_string
%state md_string

%xstate directive

%state click
%state acc_title
%state acc_descr
%state acc_title_value
%state acc_descr_value
%state acc_descr_multiline_value
%states title, title_value

%xstates frontmatter

%states pie, pie_title, pie_title_value, value

%states journey,section_task, journey_section, journey_section_title

%states flowchart, flowchart_body, node_text, node_quoted_text, link_text, link_quoted_text, direction_value, style, class_def, link_style, link_style_target, style_opt, style_value, flowchart_class, flowchart_class_target, flowchart_class_val, quoted_id

%states sequence, sequence_id, sequence_alias, sequence_message, sequence_control_id, sequence_links, sequence_links_values, autonumbers

%states class_diagram, struct, simple_direction_value, annotation, class_relation_line, class_relation_start, class_relation_end, class_in_relation, description, class_style_id, class_member, namespace_body, pre_generic_member, pre_generic_class_diagram, pre_generic_struct, pre_generic_in_relation, simple_generic, complex_generic, complex_generic_inner

%states state_diagram, state_statement, note_statement, note_content, simple_note_content, state_class_def, state_style_opt, state_style_value, state_class, state_class_style, state_scale

%states entity_relationship, entity_attributes, relationship_description

%states gantt, gantt_task_data, gantt_value, gantt_today_marker_value, gantt_title, gantt_title_value, gantt_section, gantt_section_title, gantt_title, gantt_title_value

%states requirement_diagram, requirement, requirement_value, requirement_constant_value, req_element

%state gitgraph

%states c4, person, person_ext, system_ext_queue, system_ext_db, system_ext, system_queue, system_db, system, boundary, enterprise_boundary, system_boundary, container_ext_db, container_ext, container_queue, container_ext_queue, container_db, container, container_boundary, component_ext_db, component_ext, component_queue, component_ext_queue, component_db, component, node, node_l, node_r, rel, birel, rel_u, rel_d, rel_l, rel_r, rel_b, rel_index, update_el_style, update_rel_style, update_layout_config

%states mindmap, mindmap_class, icon, mindmap_node

%states timeline

%states quadrant, quadrant_point

%states sankey, sankey_quoted_text

%states xy_chart

%states block_diagram, block_diagram_node, block_diagram_arrow, block_diagram_size, block_diagram_columns, block_id

%%

"%%"/"{"[^]* { yypushstate(directive); return Directives.OPEN_DIRECTIVE; }

<YYINITIAL> {
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r] { return EOL; }
  %%([^{][^\n\r]*)? { return LINE_COMMENT; }

  "pie" { yybegin(pie); return Pie.PIE; }
  "journey" { yybegin(journey); return Journey.JOURNEY; }
  "flowchart" { yybegin(flowchart); return Flowchart.FLOWCHART; }
  "graph" { yybegin(flowchart); return Flowchart.FLOWCHART; }
  "flowchart-elk" { yybegin(flowchart); return Flowchart.FLOWCHART; }
  "sequenceDiagram" { yybegin(sequence); return Sequence.SEQUENCE; }
  "classDiagram" |
  "classDiagram-v2"  { yybegin(class_diagram); return ClassDiagram.CLASS_DIAGRAM; }
  "stateDiagram-v2" |
  "stateDiagram" { yybegin(state_diagram); return StateDiagram.STATE_DIAGRAM; }
  "erDiagram" { yybegin(entity_relationship); return EntityRelationship.ENTITY_RELATIONSHIP; }
  "gantt" { yybegin(gantt); return Gantt.GANTT; }
  "requirementDiagram" { yybegin(requirement_diagram); return Requirement.REQUIREMENT_DIAGRAM; }
  "gitGraph"/:? { yybegin(gitgraph); return GitGraph.GIT_GRAPH; }
  "C4Context" { yybegin (c4); return C4.C4_CONTEXT; }
  "C4Container" { yybegin (c4); return C4.C4_CONTAINER; }
  "C4Component" { yybegin (c4); return C4.C4_COMPONENT; }
  "C4Dynamic" { yybegin (c4); return C4.C4_DYNAMIC; }
  "C4Deployment" { yybegin (c4); return C4.C4_DEPLOYMENT; }
  "mindmap" { yybegin(mindmap); return Mindmap.MINDMAP; }
  "timeline" { yybegin(timeline); return Timeline.TIMELINE; }
  "quadrantChart" { yybegin(quadrant); return Quadrant.QUADRANT_CHART; }
  "zenuml"[^]* { return ZenUML.ZEN_UML; }
  "sankey-beta" { yybegin(sankey); return Sankey.SANKEY; }
  "xychart-beta" { yybegin(xy_chart); return XYChart.XY_CHART; }
  "block-beta" { yybegin(block_diagram); return Block.BLOCK_DIAGRAM; }

  --- { yybegin(frontmatter); return Frontmatter.FRONTMATTER_START; }

  ";" { return SEMICOLON; }
  [^\s%;{]+ { return BAD_CHARACTER; }
  [^] { yybegin(YYINITIAL); return BAD_CHARACTER; }
}
<directive> {
  "}"/"%%" { return Directives.DIRECTIVE_TEXT; }
  "%%"/[\n\r]? { yypopstate(); return Directives.CLOSE_DIRECTIVE; }

  [^\n\r]+"}"/"%%" { return Directives.DIRECTIVE_TEXT; }
  [^\n\r]+ { return Directives.DIRECTIVE_TEXT; }

  [\n\r] { return EOL; }
}
<frontmatter> {
  "---" { yybegin(YYINITIAL); return Frontmatter.FRONTMATTER_END; }
  [^\n\r]+ { return Frontmatter.FRONTMATTER_VALUE; }
  [\n\r] { return EOL; }
}

<pie, journey, flowchart, flowchart_body, sequence, class_diagram, struct, state_diagram, state_statement, entity_relationship, entity_attributes, note_content, gantt, requirement_diagram, requirement, requirement_value, req_element, gitgraph, c4, mindmap, timeline, quadrant, sankey, block_diagram> {
  %%([^{][^\n\r]*)? { return LINE_COMMENT; }
}
<pie, journey, flowchart, flowchart_body, sequence, class_diagram, struct, state_diagram, state_statement, entity_relationship, entity_attributes, note_content, gantt, requirement_diagram, requirement, requirement_value, req_element, gitgraph, c4, timeline, quadrant, xy_chart, block_diagram> {
  "accTitle" { yypushstate(acc_title); return ACC_TITLE; }
  "accDescr" { yypushstate(acc_descr); return ACC_DESCR; }
}
<flowchart_body, gantt, class_diagram> {
  "click" { yypushstate(click); return CLICK; }
}

<pie> {
  "title" { yybegin(pie_title); return TITLE; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  "showData" { return Pie.SHOW_DATA; }
  ":" { yybegin(value); return COLON; }
  [^\s:\"]+ { return BAD_CHARACTER; }
}
<journey> {
  "title" { yypushstate(title); return TITLE; }
  "section" { yypushstate(journey_section); return SECTION; }
  ":" { yypushstate(section_task); return COLON; }
  [^\s#:;]+ { return TASK_NAME; }
}
<direction_value, simple_direction_value> {
  [\n\r] { yypopstate(); return EOL; }
}
<flowchart> {
  [\n\r] { yybegin(flowchart_body); return EOL; }
}
<flowchart, direction_value> {
  "TD" |
  "BR" |
  "<" |
  ">" |
  "^" |
  "v" { return DIR; }
}
<flowchart, direction_value, simple_direction_value> {
  "LR" |
  "RL" |
  "TB" |
  "BT" { return DIR; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<flowchart> {
  ";" { yybegin(flowchart_body); return SEMICOLON; }
}

//---pie--------------------------------------------------------------------------
<pie_title> {
  [^\S\n\r]+ { yybegin(pie_title_value); return WHITE_SPACE; }
}
<pie_title_value> {
  [\n\r] { yybegin(pie); return EOL; }
  [^\s]* { return TITLE_VALUE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<value> {
  [^\S\n\r]+ { return WHITE_SPACE; }
  [\d]+(\.[\d]+)? { yybegin(pie); return Pie.VALUE; }
}

//---journey----------------------------------------------------------------------
<title> {
  [^\S\n\r]+ { yybegin(title_value); return WHITE_SPACE; }
}
<title_value> {
  \s?(#[^\n\r]*)/[\n\r]? { return IGNORED; }
  [\n\r] { yypopstate(); return EOL; }
  [^\s#;]+ { return TITLE_VALUE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<journey_section> {
  [^\S\n\r]+ { yybegin(journey_section_title); return WHITE_SPACE; }
}
<journey_section_title> {
  \s?(#[^\n\r]*)/[\n\r]? { return IGNORED; }
  [\n\r] { yypopstate(); return EOL; }
  [^\s#:;]+ { return SECTION_TITLE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<section_task> {
  (#[^\n\r]*)/[\n\r]? { return IGNORED; }
  ":" { return COLON; }
  "," { return COMMA; }
  [^\S\n\r]+ { return WHITE_SPACE; }
  [^\s#:;,]+ { return TASK_DATA; }
  [\n\r] { yypopstate(); return EOL; }
}

//---flowchart--------------------------------------------------------------------
<flowchart_body> {
  "subgraph" { return Flowchart.SUBGRAPH; }
  "end" { return END; }

  "direction" { yypushstate(direction_value); return DIRECTION; }

  "linkStyle" { yypushstate(link_style); return Flowchart.LINK_STYLE; }
  "style" { yypushstate(style); return STYLE; }
  "classDef" { yypushstate(class_def); return CLASS_DEF; }

  "class" { yypushstate(flowchart_class); return CLASS; }

  "&"/\s { return Flowchart.AMPERSAND; }

  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [\"]/` { yypushstate(md_string); return DOUBLE_QUOTE; }
  [^\s\n\r;:%\[({><\^\|\-\=\.~\"]+/[xo<]?\-\-|[xo<]?\=\=|[xo<]?\-\. { return ID; }
  [^\s\n\r;:%\[({><\^\|\-\=\.~\"]+ { return ID; }
  [\-\=\.] { return ID; }
  :|:: { return ID; }

  ":::" { yypushstate(flowchart_class_val); return STYLE_SEPARATOR; }

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
  \~\~[\~]+ { return ARROW; }
  [xo<]?\-\- { yybegin(link_text); return START_ARROW; }
  [xo<]?\=\= { yybegin(link_text); return START_ARROW; }
  [xo<]?\-\. { yybegin(link_text); return START_ARROW; }

}
<node_text> {
  [\"] { yybegin(node_quoted_text); return DOUBLE_QUOTE; }
  [\"]/` { yypushstate(md_string); return DOUBLE_QUOTE; }

  [!#$%&'*+,-\.`?:=<>\w\^]+/[\\/][\]] { return ALIAS; }
  [!#$%&'*+,-\.`?:=<>\w\^]+/[\\/] {  }
  [!#$%&'*+,-\.`?:=<>\w\^]+ { return ALIAS; }

  [^\S\n\r]+ { return WHITE_SPACE; }

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

  [\\/]/[^\S\n\r] { return ALIAS; }
  [\\/]/[)}] { return ALIAS; }
  [\\/]/[\\/][\]] { return ALIAS; }
  [\\/] {  }
}
<node_quoted_text> {
  [\"] { yybegin(node_text); return DOUBLE_QUOTE; }
  [^\s\"]* { return ALIAS; }
  [^\S\n\r]+ { return WHITE_SPACE; }
}
<link_text> {
  [^\s;\[\]()\"|{}\-\=\.]+/\-\-+[-xo>]|\=\=+[=xo>]|\-?\.+\-[xo>] { yybegin(flowchart_body); return Flowchart.LINK_TEXT; }
  [^\s;\[\]()\"|{}\-\=\.]+ { return Flowchart.LINK_TEXT; }
  [^\s;\[\]()\"|{}\-\=\.]+/\| { return Flowchart.LINK_TEXT; }
  [\-\=\.] { return Flowchart.LINK_TEXT; }
  [^\S\n\r]+ { return WHITE_SPACE; }
  [^\S\n\r]+/\-\-+[-xo>]|\=\=+[=xo>]|\-?\.+\-[xo>] { yybegin(flowchart_body); return WHITE_SPACE; }
  [\"] { yybegin(link_quoted_text); return DOUBLE_QUOTE; }
  [\"]/` { yypushstate(md_string); return DOUBLE_QUOTE; }
  "|" { yybegin(flowchart_body); return Flowchart.SEP; }
}
<link_quoted_text> {
  [\"] { yybegin(link_text); return DOUBLE_QUOTE; }
  [^\s\"]* { return Flowchart.LINK_TEXT; }
  [^\S\n\r]+ { return WHITE_SPACE; }
}
<class_def> {
  "default" { yybegin(style_opt); return DEFAULT; }
}
<style, class_def> {
  [^\s;\n]+ { yybegin(style_opt); return STYLE_TARGET; }
}
<link_style> {
  [^\S\r\n]+ { yybegin(link_style_target); return WHITE_SPACE; }
}
<link_style_target> {
  "default" { return STYLE_TARGET; }
  \d+ { return STYLE_TARGET; }
  "," { return COMMA; }
  [^\S\r\n]+ { yybegin(style_opt); return WHITE_SPACE; }
}
<style_opt> {
  [^\s,:;][^,:\n\r;]*/: { return STYLE_OPT; }
  ":" { yybegin(style_value); return COLON; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<style_value> {
  [^\S\n\r]+ { return WHITE_SPACE; }
  [^\s,:;][^,:\n\r;]* { return STYLE_VAL; }
  "," { yybegin(style_opt); return COMMA; }
  ";" { yypopstate(); return SEMICOLON; }
  [\n\r] { yypopstate(); return EOL; }
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
  [^\s;\n\r]+ { return STYLE_TARGET; }
  \s { yypopstate(); return WHITE_SPACE; }
  ";" { yypopstate(); return SEMICOLON; }
  [\n\r] { yypopstate(); return EOL; }
}

//---sequence---------------------------------------------------------------------
<sequence> {
  "title" { yypushstate(title); return TITLE; }

  "participant" { yybegin(sequence_id); return Sequence.PARTICIPANT; }
  "actor" { yybegin(sequence_id); return Sequence.ACTOR; }
  "create" { return Sequence.CREATE; }
  "destroy" { yybegin(sequence_id); return Sequence.DESTROY; }
  "activate" { return Sequence.ACTIVATE; }
  "deactivate" { return Sequence.DEACTIVATE; }
  [Nn]"ote" { return NOTE; }
  "right of" { return RIGHT_OF; }
  "left of" { return LEFT_OF; }
  "over" { return Sequence.OVER; }
  "loop" { yybegin(sequence_control_id); return Sequence.LOOP; }
  "alt" { yybegin(sequence_control_id); return Sequence.ALT; }
  "else" { yybegin(sequence_control_id); return Sequence.ELSE; }
  "opt" { yybegin(sequence_control_id); return Sequence.OPT; }
  "par" { yybegin(sequence_control_id); return Sequence.PAR; }
  "par_over" { yybegin(sequence_control_id); return Sequence.PAR_OVER; }
  "and" { yybegin(sequence_control_id); return Sequence.AND; }
  "rect" { yybegin(sequence_control_id); return Sequence.RECT; }
  "critical" { yybegin(sequence_control_id); return Sequence.CRITICAL; }
  "option" { yybegin(sequence_control_id); return Sequence.OPTION; }
  "break" { yybegin(sequence_control_id); return Sequence.BREAK; }
  "box"	{ yybegin(sequence_control_id); return Sequence.BOX; }
  "end" { return END; }
  "autonumber" { yypushstate(autonumbers); return Sequence.AUTONUMBER; }
  "link" { return LINK; }
  "links" { yybegin(sequence_links); return Sequence.LINKS; }

  "->>" { return Sequence.SOLID_ARROW; }
  "-->>" { return Sequence.DOTTED_ARROW; }
  "->" { return Sequence.SOLID_OPEN_ARROW; }
  "-->" { return Sequence.DOTTED_OPEN_ARROW; }
  \-[x] { return Sequence.SOLID_CROSS; }
  \-\-[x] { return Sequence.DOTTED_CROSS; }
  \-[\)] { return Sequence.SOLID_POINT; }
  \-\-[\)] { return Sequence.DOTTED_POINT; }

  [^\+\->:\s,;]([\-]*[^\+\->:\s,;]+)*/"-"?"->"">"?[+-]?[^\+\->:\s,;]([\-]*[^\+\->:\s,;]+)?+ { return ID; }
  [^\+\->:\s,;]([\-]*[^\+\->:\s,;]+)*/"-"?"-"[>x)][+-]?[^\S\n\r]*[^\+\->:\s,;]([\-]*[^\+\->:\s,;]+)?+ { return ID; }
  [^\+\->:\s,;]([\-]*[^\+\->:\s,;]+)* { return ID; }

  ":" { yybegin(sequence_message); return COLON; }
  "+" { return PLUS; }
  "-" { return MINUS; }
  "," { return COMMA; }
}
<autonumbers> {
  [0-9]+ { return NUM; }
  "off" { return Sequence.OFF; }

  [^\S\n\r]+ { return WHITE_SPACE; }
  [\n\r] { yypopstate(); return EOL; }
  \S+ { return BAD_CHARACTER; }
}
<sequence_message> {
  [^\S\n\r]+ { return WHITE_SPACE; }
  [^#\s;]* { return Sequence.MESSAGE; }
  "#"[^#\s;]+";" { return Sequence.MESSAGE; }
}
<sequence_id, sequence_alias, sequence_message, sequence, sequence_control_id> {
  (#[^#;\n\r]*)/[\n\r]? { yybegin(sequence); return IGNORED; }
  [\n\r] { yybegin(sequence); return EOL; }
  ";" { yybegin(sequence); return SEMICOLON; }
}
<sequence_id> {
  "as" { yybegin(sequence_alias); return AS; }
  [^\+\->:\s,;]([\-]*[^\+\->:\s,;]+)? { return ID; }
  [^\S\n\r]+ { return WHITE_SPACE; }
}
<sequence_alias> {
  [^#\s;]* { return ALIAS; }
  [^\S\n\r]+ { return WHITE_SPACE; }
}
<sequence_control_id> {
  [^\S\n\r]+ { return WHITE_SPACE; }
  [^#\s;]* { return Sequence.CONTROL_ID; }
}
<sequence_links> {
  [^\+\->:\s,;]([\-]*[^\+\->:\s,;]+)? { return ID; }
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
<class_relation_start, class_diagram, pre_generic_class_diagram> {
  "<|"/\s*("--"|"..") { yybegin(class_relation_line); return ClassDiagram.EXTENSION_START; }
  "<"/\s*("--"|"..") { yybegin(class_relation_line); return ClassDiagram.DEPENDENCY_START; }
  [\*]/\s*("--"|"..") { yybegin(class_relation_line); return ClassDiagram.COMPOSITION; }
  [oO]/\s*("--"|"..")\s*[\w_-]+ { yybegin(class_relation_line); return ClassDiagram.AGGREGATION; }
  "()"/\s*("--"|"..") { yybegin(class_relation_line); return ClassDiagram.LOLLIPOP; }
}
<class_relation_line, class_diagram, pre_generic_class_diagram> {
  "--"/\s*[|>o*(] { yybegin(class_relation_end); return ClassDiagram.LINE; }
  ".."/\s*[|>o*(] { yybegin(class_relation_end); return ClassDiagram.DOTTED_LINE; }

  "--" { yybegin(class_in_relation); return ClassDiagram.LINE; }
  ".." { yybegin(class_in_relation); return ClassDiagram.DOTTED_LINE; }
}
<class_relation_end> {
  "|>" { yybegin(class_in_relation); return ClassDiagram.EXTENSION_END; }
  ">" { yybegin(class_in_relation); return ClassDiagram.DEPENDENCY_END; }
  [\*] { yybegin(class_in_relation); return ClassDiagram.COMPOSITION; }
  "o" { yybegin(class_in_relation); return ClassDiagram.AGGREGATION; }
  "()" { yybegin(class_in_relation); return ClassDiagram.LOLLIPOP; }
}
<class_diagram, namespace_body> {
  "link" { yypushstate(click); return LINK; }
  "callback" { yypushstate(click); return CALLBACK; }
  "note for" { return ClassDiagram.NOTE_FOR; }
  "note" { return NOTE; }
  "class"/[^\S\n\r]+.* { return CLASS; }
  "direction" { yypushstate(direction_value); return DIRECTION; }
  "namespace" { yybegin(namespace_body); return ClassDiagram.NAMESPACE; }
  "style" { yypushstate(style); return STYLE; }
}
<class_diagram> {
  [\w_-]+/[^\S\n\r]*[oO]("--"|"..")[^\S\n\r]*[\w_-]+ { yybegin(class_relation_start); return ClassDiagram.CLASS_ID; }
  [\w_-]+/[^\S\n\r]*"--"[^\S\n\r]*[\w_-]+ { yybegin(class_relation_line); return ClassDiagram.CLASS_ID; }
  [\w_-]+/[^\S\n\r]*"--" { yybegin(class_relation_line); return ClassDiagram.CLASS_ID; }
  [\w_-]+ { yybegin(pre_generic_class_diagram); return ClassDiagram.CLASS_ID; }
}
<namespace_body> {
  [\w_-]+ { return ClassDiagram.CLASS_ID; }
  "{" { yybegin(class_diagram); return OPEN_CURLY; }
}
<class_diagram, pre_generic_class_diagram> {
  ":::" { yypushstate(class_style_id); return STYLE_SEPARATOR; }
  "{" { yybegin(struct); return OPEN_CURLY; }
  "}" { return CLOSE_CURLY; }
}
<struct, pre_generic_struct> {
  "{" { return OPEN_CURLY; }
  "}" { yybegin(class_diagram); return CLOSE_CURLY; }
}
<class_in_relation, pre_generic_in_relation> {
  [\w_-]+ { yybegin(pre_generic_in_relation); return ClassDiagram.CLASS_ID; }
  ":" { yypushstate(description); return COLON; }
}
<class_in_relation, pre_generic_class_diagram, pre_generic_in_relation> {
  [\n\r] { yybegin(class_diagram); return EOL; }
}
<class_style_id> {
  [\w_]+ { yypopstate(); return ID; }
}
<struct, class_member, pre_generic_member, pre_generic_struct, pre_generic_class_diagram, pre_generic_in_relation> {
  "<" { return OPEN_ANGLE; }
  ">" { return CLOSE_ANGLE; }
}
<struct> {
  [^\"\.<>{}()\[\]~\+\-#*$,:;\s]+ { yypushstate(pre_generic_struct); return ATTRIBUTE_WORD; }
}
<class_member> {
  [^\"\.<>{}()\[\]~\+\-#*$,:;\s]+ { yypushstate(pre_generic_member); return ATTRIBUTE_WORD; }
}
<pre_generic_member, pre_generic_struct> {
  [^\"\.<>{}()\[\]~\+\-#*$,:;\s]+ { return ATTRIBUTE_WORD; }
}
<class_member, pre_generic_member> {
  "{" { return OPEN_CURLY; }
  "}" { return CLOSE_CURLY; }
}
<struct, pre_generic_struct> {
  ":" { return COLON; }
}
<pre_generic_struct> {
  [\n\r] { yybegin(struct); return EOL; }
}
<class_diagram, pre_generic_class_diagram> {
  ":" { yybegin(class_member); return COLON; }
}
<pre_generic_class_diagram, pre_generic_in_relation> {
  [~]/[^\n\r]+[~] { yypushstate(simple_generic); return TILDA; }
}
<pre_generic_member, pre_generic_struct> {
  [~]/[^\n\r]+[~] { yypushstate(complex_generic); return TILDA; }
}
<class_diagram, class_member, struct, class_in_relation, pre_generic_class_diagram, pre_generic_member, pre_generic_struct> {
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [`] { yypushstate(back_quoted_string); return BACK_QUOTE; }
  "<<" { yypushstate(annotation); return ANNOTATION_START; }

  "+" { return PLUS; }
  "-" { return MINUS; }
  "#" { return POUND; }
  "*" { return STAR; }
  "$" { return DOLLAR; }
  "(" { return OPEN_ROUND; }
  ")" { return CLOSE_ROUND; }
  "[" { return OPEN_SQUARE; }
  "]" { return CLOSE_SQUARE; }
  "." { return DOT; }
  "," { return COMMA; }
  "~" { return TILDA; }
}
<pre_generic_member, class_member> {
  [\n\r] { yybegin(class_diagram); return EOL; }
}
<simple_generic> {
  [^\s~(),]+ { return GENERIC_TYPE; }
  "(" { return OPEN_ROUND; }
  ")" { return CLOSE_ROUND; }
  "," { return COMMA; }
  "~" { yypopstate(); return TILDA; }
}
<complex_generic> {
  [^\s~(),]+/[^\n\r~]*"~" { yybegin(complex_generic_inner); return GENERIC_TYPE; }
  [^\s~,]+/[^\n\r,]*"~" { return GENERIC_TYPE; }
  "(" { return GENERIC_TYPE; }
  ")" { return GENERIC_TYPE; }
  "," { return GENERIC_TYPE; }
  "~"/[^\n\r]+[~] { return TILDA; }
  "~" { yypopstate(); return TILDA; }
}
<complex_generic_inner> {
  [^\s~]+ { return GENERIC_TYPE; }
  "~" { yypopstate(); return TILDA; }
}
<annotation> {
  \w+ { return ANNOTATION_VALUE; }
  ">>" { yypopstate(); return ANNOTATION_END; }
  [^\S\n\r]+ { return WHITE_SPACE; }
}
<description> {
  [^\s]+ { return LABEL; }
  [^\s]+/[\n\r] { yypopstate(); return LABEL; }
  [^\S\n\r]+ { return WHITE_SPACE; }
  [^\S\n\r]+/[\n\r] { yypopstate(); return WHITE_SPACE; }
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

  "classDef" { yybegin(state_class_def); return CLASS_DEF; }
  "class" { yybegin(state_class); return CLASS; }

  "scale" { yybegin(state_scale); return StateDiagram.SCALE; }

  ":" { yypushstate(description); return COLON; }
  "["/"*" { return OPEN_SQUARE; }
  "*"/"]" { return STAR; }
  "]" { return CLOSE_SQUARE; }
  [^\-\{\[\s\":,\.,<>][^\-\{\}\s\":\.,<>]* { return ID; }
  "-->" { return ARROW; }
  "--" { return StateDiagram.DIVIDER; }

  ":::" { return STYLE_SEPARATOR; }
}
<note_statement> {
  "right of" { return RIGHT_OF; }
  "left of" { return LEFT_OF; }
  [^\-\{\[\s\":\.,<>][^\-\{\}\s\":\.,<>]* { yybegin(note_content); return ID; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<note_content, simple_note_content> {
  ([^\s:][^\s]?)* { return NOTE_CONTENT; }
  [^\S\n\r]+ { return WHITE_SPACE; }
}
<note_content> {
  "end note" { yypopstate(); return END; }
  ":" { yybegin(simple_note_content); return COLON; }
  [\n\r] { return EOL; }
}
<simple_note_content> {
  [\n\r] { yypopstate(); return EOL; }
}
<state_class_def> {
  "default" { yypushstate(state_style_opt); return DEFAULT; }
  \w+ { yypushstate(state_style_opt); return StateDiagram.CLASS_DEF_ID; }
}
<state_style_opt> {
  [^\s:,]* { return STYLE_OPT; }
  ":" { yybegin(state_style_value); return COLON; }
  [\n\r] { yybegin(state_diagram); return EOL; }
}
<state_style_value> {
  [^\s:,]* { return STYLE_VAL; }
  "," { yybegin(state_style_opt); return COMMA; }
  [\n\r] { yybegin(state_diagram); return EOL; }
}
<state_class> {
  (\w+)+((","\s*\w+)*) { yybegin(state_class_style); return StateDiagram.CLASS_ENTITY_IDS; }
}
<state_class_style> {
  [^\n]* { yybegin(state_diagram); return StateDiagram.STYLE_CLASS; }
}
<state_scale> {
  \d+ { return StateDiagram.WIDTH_VALUE; }
  "width" { yybegin(state_diagram); return StateDiagram.WIDTH; }
}

//---entity-relationship----------------------------------------------------------
<entity_relationship> {
  "|o" |
  "o|" |
  "one or zero" |
  "zero or one" { return EntityRelationship.ZERO_OR_ONE; }

  "}|" |
  "|{" |
  "1+" |
  "one or more" |
  "one or many" |
  "many(1)" { return EntityRelationship.ONE_OR_MORE; }

  "}o" |
  "o{" |
  "zero or more" |
  "zero or many" |
  "0+" |
  "many(0)" |
  "many"  { return EntityRelationship.ZERO_OR_MORE; }

  "||" |
  "one" |
  "only one" |
  "1" { return EntityRelationship.ONLY_ONE; }

  "u" |
  "u"/[^\S\r\n]*("--"|"to"|".."|".-"|"-."|"optionally to") { return EntityRelationship.MD_PARENT; }

  "--" |
  "to" { return EntityRelationship.IDENTIFYING; }

  ".." |
  ".-" |
  "-." |
  "optionally to" { return EntityRelationship.NON_IDENTIFYING; }

  [a-zA-Z_][\w\-\.]* { return ID; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }

  ":" { yypushstate(relationship_description); return COLON; }
  "{" { yybegin(entity_attributes); return OPEN_CURLY; }

  "[" { return OPEN_SQUARE; }
  "]" { return CLOSE_SQUARE; }
}
<entity_attributes> {
  "FK" | "PK" | "UK" { return EntityRelationship.ATTR_KEY; }
  [\*a-zA-Z_][\w\-\[\]\(\)]* { return ATTRIBUTE_WORD; }
  [~]/[^\n\r]+[~] { yypushstate(complex_generic); return TILDA; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  "{" { return OPEN_CURLY; }
  "}" { yybegin(entity_relationship); return CLOSE_CURLY; }
  "," { return COMMA; }
  "~" { return TILDA; }
}
<relationship_description> {
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [^\s\"]+ { return LABEL; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r] { yybegin(entity_relationship); return EOL; }
}

//---gantt------------------------------------------------------------------------
<gantt> {
  "title" { yypushstate(gantt_title); return TITLE; }
  "dateFormat" { yybegin(gantt_value); return Gantt.DATE_FORMAT; }
  "inclusiveEndDates" { return Gantt.INCLUSIVE_END_DATES; }
  "topAxis" { return Gantt.TOP_AXIS; }
  "axisFormat" { yybegin(gantt_value); return Gantt.AXIS_FORMAT; }
  "includes" { yybegin(gantt_value); return Gantt.INCLUDES; }
  "excludes" { yybegin(gantt_value); return Gantt.EXCLUDES; }
  "todayMarker" { yybegin(gantt_today_marker_value); return Gantt.TODAY_MARKER; }
  "tickInterval" { yybegin(gantt_value); return Gantt.TICK_INTERVAL; }
  "section" { yypushstate(gantt_section); return SECTION; }
  
  "weekday" { return Gantt.WEEKDAY; }
  "monday"  { return Gantt.MONDAY; }
  "tuesday"  { return Gantt.TUESDAY; }
  "wednesday" { return Gantt.WEDNESDAY; }
  "thursday" { return Gantt.THURSDAY; }
  "friday" { return Gantt.FRIDAY; }
  "saturday" { return Gantt.SATURDAY; }
  "sunday" { return Gantt.SUNDAY; }
  
  [^\s:]+ { return TASK_NAME; }
  ":" { yybegin(gantt_task_data); return COLON; }
}
<gantt_task_data, gantt_value, gantt_today_marker_value> {
  [\n\r] { yybegin(gantt); return EOL; }
}
<gantt_task_data> {
  (#[^\n\r]*)/[\n\r]? { return IGNORED; }
  [^#\s;,]+ { return TASK_DATA; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  "," { return COMMA; }
}
<gantt_value> {
  [^#\s;]+ { return Gantt.GANTT_VALUE; }
}
<gantt_today_marker_value> {
  [^\s;]+ { return Gantt.GANTT_VALUE; }
}
<gantt_section> {
  [^\S\n\r]+ { yybegin(gantt_section_title); return WHITE_SPACE; }
}
<gantt_section_title> {
  [\n\r] { yypopstate(); return EOL; }
  [^\s:]+ { return SECTION_TITLE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}
<gantt_title> {
  [^\S\n\r]+ { yybegin(gantt_title_value); return WHITE_SPACE; }
}
<gantt_title_value> {
  [\n\r] { yypopstate(); return EOL; }
  [^\s]+ { return TITLE_VALUE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}

//---requirement------------------------------------------------------------------
<requirement_diagram> {
  "requirement"/\s+[\w][^\r\n\{\<\>\-\=:;]*\s*\{ { yybegin(requirement); return Requirement.REQUIREMENT; }
  "functionalRequirement"/\s+[\w][^\r\n\{\<\>\-\=:;]*\s*\{ { yybegin(requirement); return Requirement.FUNCTIONAL_REQUIREMENT; }
  "interfaceRequirement"/\s+[\w][^\r\n\{\<\>\-\=:;]*\s*\{ { yybegin(requirement); return Requirement.INTERFACE_REQUIREMENT; }
  "performanceRequirement"/\s+[\w][^\r\n\{\<\>\-\=:;]*\s*\{ { yybegin(requirement); return Requirement.PERFORMANCE_REQUIREMENT; }
  "physicalRequirement"/\s+[\w][^\r\n\{\<\>\-\=:;]*\s*\{ { yybegin(requirement); return Requirement.PHYSICAL_REQUIREMENT; }
  "designConstraint"/\s+[\w][^\r\n\{\<\>\-\=:;]*\s*\{ { yybegin(requirement); return Requirement.DESIGN_CONSTRAINT; }
  "element"/\s+[\w][^\r\n\{\<\>\-\=:;]*\s*\{ { yybegin(req_element); return Requirement.ELEMENT; }
  "contains"/\s*\- { return Requirement.CONTAINS; }
  "copies"/\s*\- { return Requirement.COPIES; }
  "derives"/\s*\- { return Requirement.DERIVES; }
  "satisfies"/\s*\- { return Requirement.SATISFIES; }
  "verifies"/\s*\- { return Requirement.VERIFIES; }
  "refines"/\s*\- { return Requirement.REFINES; }
  "traces"/\s*\- { return Requirement.TRACES; }
  "<-" { return Requirement.ARROW_LEFT; }
  "->" { return Requirement.ARROW_RIGHT; }
  "-" { return Requirement.REQ_LINE; }
}
<requirement, req_element> {
  ":" { yypushstate(requirement_value); return COLON; }
  "{" { return OPEN_CURLY; }
  "}" { yybegin(requirement_diagram); return CLOSE_CURLY; }
}
<requirement> {
  "id" { return ID_KEYWORD; }
  "text" { return Requirement.TEXT; }
  "risk" { yybegin(requirement_constant_value); return Requirement.RISK; }
  "verifymethod" { yybegin(requirement_constant_value); return Requirement.VERIFY_METHOD; }
}
<req_element> {
  "type" { return TYPE; }
  "doc"[rR]"ef" { return Requirement.DOCREF; }
}
<requirement_diagram, requirement, req_element> {
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [\w][^\s\{\<\>\-\=:;]* { return ID; }
}
<requirement_value> {
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [\w][^\s\{\<\>\-\=]* { return LABEL; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r] { yypopstate(); return EOL; }
}
<requirement_constant_value> {
  ":" { return COLON; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  "high" { yybegin(requirement); return Requirement.HIGH; }
  "medium" { yybegin(requirement); return Requirement.MEDIUM; }
  "low" { yybegin(requirement); return Requirement.LOW; }
  "analysis" { yybegin(requirement); return Requirement.ANALYSIS; }
  "inspection" { yybegin(requirement); return Requirement.INSPECTION; }
  "test" { yybegin(requirement); return Requirement.TEST; }
  "demonstration" { yybegin(requirement); return Requirement.DEMONSTRATION; }

  [^\s:]+ { return BAD_CHARACTER; }
}

//---gitgraph---------------------------------------------------------------------
<gitgraph> {
  "commit" { return GitGraph.COMMIT; }
  "id" { return ID_KEYWORD; }
  "type" { return TYPE; }
  "tag" { return GitGraph.TAG; }
  "msg" { return GitGraph.MSG; }
  "parent" { return GitGraph.PARENT; }
  "branch" { return GitGraph.BRANCH; }
  "order" { return GitGraph.ORDER; }
  "merge" { return GitGraph.MERGE; }
  "cherry-pick" { return GitGraph.CHERRY_PICK; }
  "checkout" { return GitGraph.CHECKOUT; }

  "NORMAL" { return GitGraph.NORMAL; }
  "REVERSE" { return GitGraph.REVERSE; }
  "HIGHLIGHT" { return GitGraph.HIGHLIGHT; }

  "LR" |
  "TB" { return DIR; }

  ":" { return COLON; }
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [0-9]+ { return NUM; }
  \w([-\./\w]*[-\w])? { return ID; }
  (#[^\n\r]*)/[\n\r]? { return IGNORED; }
}

//---C4---------------------------------------------------------------------------
<c4> {
  "direction" { yypushstate(simple_direction_value); return DIRECTION; }
  "title" { yypushstate(title); return TITLE; }

  "Person_Ext" { yybegin(person_ext); return C4.PERSON_EXT; }
  "Person" { yybegin(person); return C4.PERSON; }
  "SystemQueue_Ext" { yybegin(system_ext_queue); return C4.SYSTEM_EXT_QUEUE; }
  "SystemDb_Ext" { yybegin(system_ext_db); return C4.SYSTEM_EXT_DB; }
  "System_Ext" { yybegin(system_ext); return C4.SYSTEM_EXT; }
  "SystemQueue" { yybegin(system_queue); return C4.SYSTEM_QUEUE; }
  "SystemDb" { yybegin(system_db); return C4.SYSTEM_DB; }
  "System" { yybegin(system); return C4.SYSTEM; }

  "Boundary" { yybegin(boundary); return C4.BOUNDARY; }
  "Enterprise_Boundary" { yybegin(enterprise_boundary); return C4.ENTERPRISE_BOUNDARY; }
  "System_Boundary" { yybegin(system_boundary); return C4.SYSTEM_BOUNDARY; }

  "ContainerQueue_Ext" { yybegin(container_ext_queue); return C4.CONTAINER_EXT_QUEUE; }
  "ContainerDb_Ext" { yybegin(container_ext_db); return C4.CONTAINER_EXT_DB; }
  "Container_Ext" { yybegin(container_ext); return C4.CONTAINER_EXT; }
  "ContainerQueue" { yybegin(container_queue); return C4.CONTAINER_QUEUE; }
  "ContainerDb" { yybegin(container_db); return C4.CONTAINER_DB; }
  "Container" { yybegin(container); return C4.CONTAINER; }

  "Container_Boundary" { yybegin(container_boundary); return C4.CONTAINER_BOUNDARY; }

  "ComponentQueue_Ext" { yybegin(component_ext_queue); return C4.COMPONENT_EXT_QUEUE; }
  "ComponentDb_Ext" { yybegin(component_ext_db); return C4.COMPONENT_EXT_DB; }
  "Component_Ext" { yybegin(component_ext); return C4.COMPONENT_EXT; }
  "ComponentQueue" { yybegin(component_queue); return C4.COMPONENT_QUEUE; }
  "ComponentDb" { yybegin(component_db); return C4.COMPONENT_DB; }
  "Component" { yybegin(component); return C4.COMPONENT; }

  "Deployment_Node" { yybegin(node); return C4.NODE; }
  "Node" { yybegin(node); return C4.NODE; }
  "Node_L" { yybegin(node_l); return C4.NODE_L; }
  "Node_R" { yybegin(node_r); return C4.NODE_R; }


  "Rel" { yybegin(rel); return C4.REL; }
  "BiRel" { yybegin(birel); return C4.BIREL; }
  "Rel_Up" { yybegin(rel_u); return C4.REL_U; }
  "Rel_U" { yybegin(rel_u); return C4.REL_U; }
  "Rel_Down" { yybegin(rel_d); return C4.REL_D; }
  "Rel_D" { yybegin(rel_d); return C4.REL_D; }
  "Rel_Left" { yybegin(rel_l); return C4.REL_L; }
  "Rel_L" { yybegin(rel_l); return C4.REL_L; }
  "Rel_Right" { yybegin(rel_r); return C4.REL_R; }
  "Rel_R" { yybegin(rel_r); return C4.REL_R; }
  "Rel_Back" { yybegin(rel_b); return C4.REL_B; }
  "RelIndex" { yybegin(rel_index); return C4.REL_INDEX; }

  "UpdateElementStyle" { yybegin(update_el_style); return C4.UPDATE_EL_STYLE; }
  "UpdateRelStyle" { yybegin(update_rel_style); return C4.UPDATE_REL_STYLE; }
  "UpdateLayoutConfig" { yybegin(update_layout_config); return C4.UPDATE_LAYOUT_CONFIG; }

  "{" { return OPEN_CURLY; }
  "}" { return CLOSE_CURLY; }
}
<person,person_ext,system_ext_queue,system_ext_db,system_ext,system_queue,system_db,system,boundary,enterprise_boundary,system_boundary,container_ext_db,container_ext,container_queue,container_ext_queue,container_db,container,container_boundary,component_ext_db,component_ext,component_queue,component_ext_queue,component_db,component,node,node_l,node_r,rel,birel,rel_u,rel_d,rel_l,rel_r,rel_b,rel_index,update_el_style,update_rel_style,update_layout_config> {
  "(" { return OPEN_ROUND; }
  ")" { yybegin(c4); return CLOSE_ROUND; }
  "," { return COMMA; }
  \" { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  "$" { return DOLLAR; }
  "=" { return C4.EQUALITY; }
  [^$=(),\"\s]* { return C4.C4_ATTRIBUTE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
}

//---mindmap----------------------------------------------------------------------
<mindmap> {
  ":::" { yypushstate(mindmap_class); return STYLE_SEPARATOR; }
  "::icon(" { yypushstate(icon); return Mindmap.OPEN_ICON; }
  ":" { return COLON; }

  "-)" |
  "(-" |
  "))" |
  ")"  |
  "((" |
  "("  |
  "["  |
  "{{" { yybegin(mindmap_node); return NODE_DESCR_START; }

  [^:(\[\s){}][^(\[\s){}]* { return ID; }
}
<mindmap_class> {
  [^\s]* { return CLASS; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r] { yypopstate(); return EOL; }
}
<icon> {
  [^)]* { return Mindmap.ICON_VALUE; }
  ")" { yypopstate(); return Mindmap.CLOSE_ICON; }
}
<mindmap_node> {
  \" { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [\"]/` { yypushstate(md_string); return DOUBLE_QUOTE; }

  [^\"\s)\](}][^\"\s)\](}]* { return Mindmap.NODE_DESCR; }

  "))" |
  ")"  |
  "]"  |
  "(-" |
  "-)" |
  "((" |
  "("  |
  "}}" { yybegin(mindmap); return NODE_DESCR_END; }

  [^\S\r\n]+ { return WHITE_SPACE; }
}

//---timeline---------------------------------------------------------------------
<timeline> {
  "title" { yypushstate(title); return TITLE; }
  "section" { yypushstate(journey_section); return SECTION; }
  ":" { yypushstate(section_task); return COLON; }
  [^\s#:;]+ { return TASK_NAME; }
  (#[^\n\r]*)/[\n\r]? { return IGNORED; }
}

//---quadrant---------------------------------------------------------------------
<quadrant> {
  "title" { yypushstate(title); return TITLE; }

  "x-axis" { return X_AXIS; }
  "y-axis" { return Y_AXIS; }

  \-\-+\> { return ARROW; }

  "quadrant-1" |
  "quadrant-2" |
  "quadrant-3" |
  "quadrant-4" { return Quadrant.QUADRANT; }

  \" { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [\"]/` { yypushstate(md_string); return DOUBLE_QUOTE; }

  [!#$%&'*+,-.`?\\_/=\w]+ { return Quadrant.QUADRANT_TEXT; }

  ":" { return COLON; }
  "[" { yybegin(quadrant_point); return OPEN_SQUARE; }
}
<quadrant_point> {
  1 |
  0(\.\d+)? { return NUM; }

  "," { return COMMA; }
  "]" { yybegin(quadrant); return CLOSE_SQUARE; }
}

//---sankey----------------------------------------------------------------------
<sankey> {
  "," { return COMMA; }
  [\"] { yypushstate(sankey_quoted_text); return DOUBLE_QUOTE; }
  [^,\"\s]+ { return Sankey.SANKEY_TEXT; }
}
<sankey_quoted_text> {
  "," { return COMMA; }
  [\"]/[^\"]? { yypopstate(); return DOUBLE_QUOTE; }
  [\"] { return DOUBLE_QUOTE; }
  [^,\"\s]* { return Sankey.SANKEY_TEXT; }
}

//---xy-chart--------------------------------------------------------------------
<xy_chart> {
  "title" { yypushstate(title); return TITLE; }

  "x-axis" { return X_AXIS; }
  "y-axis" { return Y_AXIS; }

  "vertical" | "horizontal" { return XYChart.ORIENTATION_VALUE; }

  "line" { return XYChart.LINE_KEYWORD; }
  "bar" { return XYChart.BAR_KEYWORD; }

  "[" { return OPEN_SQUARE; }
  "]" { return CLOSE_SQUARE; }
  "-->" { return ARROW; }

  [+-]?(\d+(\.\d+)?|\.\d+) { return NUM; }

  [\w&+=*\.#\-]+ { return XYChart.XY_CHART_TEXT; }
  ":" { return COLON; }
  "," { return COMMA; }
  ";" { return SEMICOLON;}

  \" { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [\"]/` { yypushstate(md_string); return DOUBLE_QUOTE; }
}

//---block-----------------------------------------------------------------------
<block_diagram> {
  "block" { return Block.BLOCK; }
  "block"/":" { yybegin(block_id); return Block.BLOCK; }

  "columns" { yybegin(block_diagram_columns); return Block.COLUMNS; }
  "space" { return Block.SPACE; }

  "default" { return DEFAULT; }
  "interpolate" { return Block.INTERPOLATE; }
  "linkStyle" { return Flowchart.LINK_STYLE; }
  "style" { yypushstate(style); return STYLE; }
  "classDef" { yypushstate(class_def); return CLASS_DEF; }
  "class" { yypushstate(flowchart_class); return CLASS; }

  "end" { return END; }

  "-)"  |
  "(-"  |
  "))"  |
  ")"   |
  "(((" |
  "(("  |
  "{{"  |
  "{"   |
  ">"   |
  "(["  |
  "("   |
  "[["  |
  "[|"  |
  "[("  |
  ")))" |
  "[\\" |
  "[/"  |
  "[" { yybegin(block_diagram_node); return NODE_DESCR_START; }

  "<[" { yybegin(block_diagram_arrow); return Block.ARROW_DESCR_START; }

  ":" { return COLON; }
  ":"/\s*\d+ { yybegin(block_diagram_size); return COLON; }

  [xo<]?\-\-+[-xo>] { return ARROW; }
  [xo<]?\=\=+[=xo>] { return ARROW; }
  [xo<]?\-?\.+\-[xo>]? { return ARROW; }
  [xo<]?\-\- { yybegin(block_diagram_arrow); return START_ARROW; }
  [xo<]?\=\= { yybegin(block_diagram_arrow); return START_ARROW; }
  [xo<]?\-\. { yybegin(block_diagram_arrow); return START_ARROW; }

  [^\s\n\r:\[(){}><\-]+ { return ID; }
}
<block_id> {
  ":" { return COLON; }
  [^\s\n\r:\[(){}><\-]+ { yybegin(block_diagram); return ID; }
}
<block_diagram, block_diagram_arrow, block_diagram_node> {
  [\"] { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }
  [\"]/` { yypushstate(md_string); return DOUBLE_QUOTE; }
}
<block_diagram_node> {
  "(((" |
  ")))" |
  [\)]\) |
  "}}" |
  "}" |
  "(-" |
  "-)" |
  "((" |
  "]]" |
  "(" |
  "])" |
  "\\]" |
  "/]" |
  ")]" |
  [\)] |
  \]\> |
  [\]] { yybegin(block_diagram); return NODE_DESCR_END; }
}
<block_diagram_arrow> {
  "]>" { return Block.ARROW_DESCR_END; }

  "right" |
  "left"  |
  "x"     |
  "y"     |
  "up"    |
  "down" { return Block.ARROW_DIR; }

  "," { return COMMA; }
  "(" { return OPEN_ROUND; }
  ")" { yybegin(block_diagram); return CLOSE_ROUND; }

  [xo<]?\-\-+[-xo>] { yybegin(block_diagram); return ARROW; }
  [xo<]?\=\=+[=xo>] { yybegin(block_diagram); return ARROW; }
  [xo<]?\-?\.+\-[xo>]? { yybegin(block_diagram); return ARROW; }
}
<block_diagram_size> {
  ":" { return COLON; }
  \d+ { yybegin(block_diagram); return NUM; }
  [\n\r] { yybegin(block_diagram); return EOL; }

  [^\s] { return BAD_CHARACTER; }
}
<block_diagram_columns> {
  "auto" { yybegin(block_diagram); return Block.AUTO; }
  \d+ { yybegin(block_diagram); return NUM; }
  [\n\r] { yybegin(block_diagram); return EOL; }

  [^\s]+ { return BAD_CHARACTER; }
}

//--------------------------------------------------------------------------------
<double_quoted_string> {
  [\"] { yypopstate(); return DOUBLE_QUOTE; }
  [^\"]* { return STRING_VALUE; }
}
<back_quoted_string> {
  [`] { yypopstate(); return BACK_QUOTE; }
  [^`]* { return STRING_VALUE; }
}
<md_string> {
  [`] { return BACK_QUOTE; }
  [\"] { yypopstate(); return DOUBLE_QUOTE; }
  [^`\"]+ { return MD_STRING_VALUE; }
}
<click> {
  "call" { return CALL; }
  "href" { return HREF; }
  "(" { return OPEN_ROUND; }
  ")" { return CLOSE_ROUND; }
  "," { return COMMA; }
  \" { yypushstate(double_quoted_string); return DOUBLE_QUOTE; }

  "_self" |
  "_blank" |
  "_parent" |
  "_top" { return LINK_TARGET; }

  [^%()\"\s]+ { return CLICK_DATA; }

  [^\S\n\r]+ { return WHITE_SPACE; }
  [\n\r] { yypopstate(); return EOL; }
}

<acc_title> {
  ":" { yybegin(acc_title_value); return COLON; }

  [^\S\n\r]+ { return WHITE_SPACE; }
  [\n\r] { return EOL; }
}
<acc_title_value> {
  [^\s]+ { return ACC_TITLE_VALUE; }
  [\n\r] { yypopstate(); return EOL; }
}
<acc_descr> {
  ":" { yybegin(acc_descr_value); return COLON; }
  "{" { yybegin(acc_descr_multiline_value); return OPEN_CURLY; }

  [^\S\n\r]+ { return WHITE_SPACE; }
  [\n\r] { return EOL; }
}
<acc_descr_value> {
  [^\s]+ { return ACC_DESCR_VALUE; }
  [\n\r] { yypopstate(); return EOL; }
}
<acc_descr_multiline_value> {
  [^}\s]* { return ACC_DESCR_MULTILINE_VALUE; }
  "}" { yypopstate(); return CLOSE_CURLY; }
}

";" { return SEMICOLON; }
[^\S\n\r]+ { return WHITE_SPACE; }
[\n\r] { return EOL; }
[^] { return BAD_CHARACTER; }
