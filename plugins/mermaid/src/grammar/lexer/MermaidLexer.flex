package com.github.firsttimeinforever.mermaid.lang.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.FlexLexer;
import static com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.*;
import static com.github.firsttimeinforever.mermaid.lang.lexer.MermaidTokens.Pie;

%%

%{
  public _MermaidLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _MermaidLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

%xstate double_quoted_string
%xstate title
%xstate title_value
%xstate value
%xstate line_comment
%xstate line_comment_text_after_white_space

%xstate directive
%xstate directive_close
%xstate double_quoted_string_inside_directive

//%xstate open_directive

//%xstate property_end
//%xstate property_key
//%xstate property_value
//%xstate property_chain
//%xstate quoted_property_value

//%xstate type_directive
//%xstate arg_directive
//%xstate close_directive
//%xstate block_comment

%%

//<YYINITIAL> {
//  \%\%\{ { yybegin(open_directive); return OPEN_DIRECTIVE; }
//  [^\S\r\n]+ { return WHITE_SPACE; }
//  [\n\r]+ { return EOL; }
//  \%\% { yybegin(line_comment); return LINE_COMMENT; }
//  "title" { yybegin(title); return Pie.TITLE; }
//  [\"] { yybegin(string); return DOUBLE_QUOTE; }
//  ":" { yybegin(value); return COLON; }
//  "pie" { return Pie.PIE; }
//  "showData" { return Pie.SHOW_DATA; }
//  ";" { return COLON; }
//}
<YYINITIAL> {
  "%%{" { yybegin(directive); return OPEN_DIRECTIVE; }
  [^\S\r\n]+ { return WHITE_SPACE; }
  [\n\r]+ { return EOL; }
  "%%" { yybegin(line_comment); return LINE_COMMENT; }
  "title" { yybegin(title); return Pie.TITLE; }
  [\"] { yybegin(double_quoted_string); return DOUBLE_QUOTE; }
  ":" { yybegin(value); return COLON; }
  "pie" { return Pie.PIE; }
  "showData" { return Pie.SHOW_DATA; }
  ";" { return SEMICOLON; }
}
<directive> {
  "}%%" { yybegin(YYINITIAL); return CLOSE_DIRECTIVE; }
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

//<open_directive> {
////  ((:?(!\}?\%\%)[^:.])*) { yybegin(type_directive); return TYPE_DIRECTIVE; }
//  [^\S\n\r]+ { return WHITE_SPACE; }
//  [\n\r]+ { return EOL; }
//  \}\%\% { yybegin(YYINITIAL); return CLOSE_DIRECTIVE; }
//  \w+ { yybegin(property_key); return PROPERTY_KEY; }
//  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
//}
//
//<property_chain> {
//  \w+ { yybegin(property_key); return PROPERTY_KEY; }
//}
//<property_key> {
//  [^\S\n\r]+ { return WHITE_SPACE; }
//  [\n\r]+ { return EOL; }
//  ":" { yybegin(property_value); return COLON; }
//  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
//}
//<property_value> {
//  [^\S\n\r]+ { return WHITE_SPACE; }
//  [\n\r]+ { return EOL; }
//  \d+ { yybegin(open_directive); return PROPERTY_VALUE; }
//  \" { yybegin(quoted_property_value); return DOUBLE_QUOTE; }
//  "," { yybegin(property_chain); return COMMA; }
////  "," { yybegin(property_end); return COMMA; }
////  \".*\" {
////    yybegin(YYINITIAL);
////    return PROPERTY_VALUE;
////  }
////  [^\{\}] { yybegin(property); }
//  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
//}


//<type_directive> {
//  ":" { yybegin(YYINITIAL); yybegin(arg_directive); return COLON; }
//  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
//}
//<type_directive,arg_directive> {
//  \}\%\% {
//  //    this.popState();
//  //    this.popState();
//    yybegin(YYINITIAL);
//    return CLOSE_DIRECTIVE;
//  }
//  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
//}
//<arg_directive> {
//  ((:?(!\}?\%\%).|\n)*) { return ARG_DIRECTIVE; }
//  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
//}


<title> {
  [^\S\n\r]+ { yybegin(title_value); return WHITE_SPACE; }
  //<title>!(\n|;|#)+[^\n]* {
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<title_value> {
  [\n\r] { yybegin(YYINITIAL); return EOL; }
  .+ { return Pie.TITLE_VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

<line_comment> {
  [^\S\n\r]+ { yybegin(line_comment_text_after_white_space); return WHITE_SPACE; }
  [^\n\r]+ { yybegin(YYINITIAL); return COMMENT_TEXT; }
  [\n\r] { yybegin(YYINITIAL); return EOL; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}
<line_comment_text_after_white_space> {
  [^\n\r]+ { yybegin(YYINITIAL); return COMMENT_TEXT; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

<double_quoted_string> {
  [\"] { yybegin(YYINITIAL); return DOUBLE_QUOTE; }
  [^\"]* { return STRING_VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

<value> {
  [^\S\n\r]+ { return WHITE_SPACE; }
  [\d]+(:?\.[\d]+)? { yybegin(YYINITIAL); return Pie.VALUE; }
  [^] { yybegin(YYINITIAL); yypushback(yylength()); return BAD_CHARACTER; }
}

[^] { return BAD_CHARACTER; }
//\%\%\{                                                          { this.begin('open_directive'); return 'open_directive'; }
//<open_directive>((?:(?!\}\%\%)[^:.])*)                          { this.begin('type_directive'); return 'type_directive'; }
//<type_directive>":"                                             { this.popState(); this.begin('arg_directive'); return ':'; }
//<type_directive,arg_directive>\}\%\%                            { this.popState(); this.popState(); return 'close_directive'; }
//<arg_directive>((?:(?!\}\%\%).|\n)*)                            return 'arg_directive';
//\%\%(?!\{)[^\n]*                                                /* skip comments */
//[^\}]\%\%[^\n]*                                                 /* skip comments */{ /*console.log('');*/ }
//[\n\r]+                                                         return 'NEWLINE';
//\%\%[^\n]*                                                      /* do nothing */
//[\s]+ 		                                                      /* ignore */
//title                                                           { this.begin("title");return 'title'; }
//<title>(?!\n|;|#)*[^\n]*                                        { this.popState(); return "title_value"; }
//["]                                                             { this.begin("string"); }
//<string>["]                                                     { this.popState(); }
//<string>[^"]*                                                   { return "txt"; }
//"pie"		                                                        return 'PIE';
//"showData"                                                      return 'showData';
//":"[\s]*[\d]+(?:\.[\d]+)?                                       return "value";
//<<EOF>>                                                         return 'EOF';