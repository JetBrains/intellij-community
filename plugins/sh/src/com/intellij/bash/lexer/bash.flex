package com.intellij.bash.lexer;

import com.intellij.psi.tree.IElementType;
import static com.intellij.bash.lexer.BashTokenTypes.*;
import com.intellij.util.containers.IntStack;
import static org.apache.commons.lang3.StringUtils.contains;
import com.intellij.lexer.FlexLexer;

%%

%class _BashLexerGen
%implements FlexLexer
%unicode
%public

%function advance
%type IElementType

%{
  public _BashLexerGen() { this(null); }
  private void pushState(int state) { myStack.push(yystate()); yybegin(state);}
  private void popState() { yybegin(myStack.pop());}
  private void switchState(int state) { popState(); pushState(state); }
  private IntStack myStack = new IntStack(20);
  private boolean inString;
  private boolean inOldArithmeticExpansion;
  private boolean letWithQuote;
  private CharSequence heredocMarker;

  protected void onReset() { myStack.clear(); }
%}

/***** Custom user code *****/

InputCharacter           = [^\r\n]
LineTerminator           = \r\n | \r | \n
LineContinuation         = "\\" {LineTerminator}
WhiteSpace               = [ \t\f] {LineContinuation}*
//WhiteSpaceLineContinuation={WhiteSpace}

Shebang                  = #\! {InputCharacter}* {LineTerminator}?
Comment                  = # {InputCharacter}*

EscapedChar              = "\\" [^\n]
Quote                    = \"

//SingleCharacter = [^\'] | {EscapedChar}
//UnescapedCharacter = [^\']

RawString                = '[^']*'

WordFirst                = [[:letter:]||[:digit:]||[_/@?.*:%\^+,~-]] | {EscapedChar} | [\u00C0-\u00FF] | {LineContinuation}
WordAfter                = {WordFirst} | [#!] // \[\]
Word                     = {WordFirst}{WordAfter}*


//ParamExpansionWordFirst  = [a-zA-Z0-9_] | {EscapedChar} | {LineContinuation}
//ParamExpansionWord       = {ParamExpansionWordFirst}+

//AssignListWordFirst      = [[\p{Letter}]||[0-9_/@?.*:&%\^~,]] | {EscapedChar} | {LineContinuation}
//AssignListWordAfter      =  {AssignListWordFirst} | [#!]
//AssignListWord           = {AssignListWordFirst}{AssignListWordAfter}*

ArithWordFirst           = [a-zA-Z_@?.:] | {EscapedChar} | {LineContinuation}
ArithWordAfter           = {ArithWordFirst} | [0-9#!]
// No "[" | "]"
ArithWord                = {ArithWordFirst}{ArithWordAfter}*

AssignmentWord           = [[:letter:]||[_]] [[:letter:]||[0-9_]]*
Variable                 = \$ {AssignmentWord} | \$[@$#0-9?!*_-]

//ArithExpr                = ({ArithWord} | [0-9a-z+*-] | {Variable} )+

IntegerLiteral           = [0] | ([1-9][0-9]*) | (6[0-4]|[1-5][0-9]|[2-9]) # [[:letter:]||[:digit:]]*  // todo: fix Rule can never be matched
HexIntegerLiteral        = "0" [xX] [0-9a-fA-F]+
OctalIntegerLiteral      = "0" [0-7]+

CaseFirst                = {EscapedChar} | [^|\"'$)(# \n\r\f\t\f]
CaseAfter                = {EscapedChar} | [^|\"'$`)( \n\r\f\t\f;]
CasePattern              = {CaseFirst} ({LineContinuation}? {CaseAfter})*

Filedescriptor           = "&" {IntegerLiteral} | "&-"
AssigOp                  = "=" | "+="

EscapedRightCurly        = "\\}"

HeredocMarker            = [^\r\n|&\\;()[] \"'] | {EscapedChar}
HeredocMarkerInQuotes    = {HeredocMarker}+ | '{HeredocMarker}+' | \"{HeredocMarker}+\"

//LetWithWhitespace        = let {WhiteSpace}+
//WhitespaceWithQuote      =  {WhiteSpace}+ {Quote}{1}

%state EXPRESSIONS
%state LET_EXPRESSION_START
%state LET_EXPRESSIONS
%state PARAMETER_EXPANSION
%state CASE_CLAUSE
%state CASE_PATTERN

%state HERE_DOC_START_MARKER
%state HERE_DOC_END_MARKER
%state HERE_DOC_PIPELINE
%state HERE_DOC_BODY

%%

<EXPRESSIONS, LET_EXPRESSIONS> {
    "~"                           { return ARITH_BITWISE_NEGATE; }
    "^"                           { return ARITH_BITWISE_XOR; }

    "?"                           { return ARITH_QMARK; }
    ":"                           { return ARITH_COLON; }

    "++"                          { return ARITH_PLUS_PLUS; }
    "+"                           { return ARITH_PLUS; }
    "--"                          { return ARITH_MINUS_MINUS; }
    "-"                           { return ARITH_MINUS; }
    "=="                          { return ARITH_EQ; }

    "**"                          { return EXPONENT; }
    "/"                           { return DIV; }
    "*"                           { return MULT; }
    "%"                           { return MOD; }
    "<<"                          { return SHIFT_LEFT; }
    "<"                           { return LT; }
    ">"                           { return GT; }

    {ArithWord}                   { return WORD; }
}

<EXPRESSIONS> {
    "))"                          { yybegin(YYINITIAL); return RIGHT_DOUBLE_PAREN; }
}

<LET_EXPRESSION_START> {
    {WhiteSpace}+ / {Quote}       { return WHITESPACE; }
    {WhiteSpace}+                 { yybegin(LET_EXPRESSIONS); return WHITESPACE; }
    {Quote}                       { letWithQuote = true; yybegin(LET_EXPRESSIONS); return QUOTE; }
}

<LET_EXPRESSIONS> {
    {WhiteSpace}+                 { if (!letWithQuote) { popState(); letWithQuote = false; } return WHITESPACE; }
    {Quote}                       { if (letWithQuote) { popState(); letWithQuote = false; } return QUOTE; }
    {LineTerminator}              { popState(); letWithQuote = false; return LINEFEED; }
}

<PARAMETER_EXPANSION> {
  "{"                                 {             return LEFT_CURLY; }
  "}"                                 { popState(); return RIGHT_CURLY; }
  ([^{}]|{EscapedRightCurly})+ / "}"  {             return PARAMETER_EXPANSION_BODY; }
  [^]                                 { popState(); }
}

<CASE_CLAUSE> {
  "esac"                          { popState();                 return ESAC; }
  ";&" | ";;&" | ";;"             { pushState(CASE_PATTERN);    return CASE_END; }
  "in"                            { pushState(CASE_PATTERN);    return WORD; }
}

<CASE_PATTERN> {
  "esac"                          { popState(); yypushback(yylength()); }
  ")"                             { popState(); return RIGHT_PAREN; }
  {CasePattern}                   { return WORD; }
}

<HERE_DOC_START_MARKER> {
  {HeredocMarkerInQuotes}               { if ((yycharat(yylength()-1) == '\'' || yycharat(yylength()-1) == '"') && yylength() > 2)
                                            heredocMarker = yytext().subSequence(1, yylength()-1);
                                          else heredocMarker = yytext();
                                          yybegin(HERE_DOC_PIPELINE);
                                          return HEREDOC_MARKER_START; }
  {WhiteSpace}+         { return WHITESPACE; }
}

<HERE_DOC_END_MARKER> {
  {HeredocMarker}+                      { if (yytext().equals(heredocMarker)) { heredocMarker = ""; popState(); return HEREDOC_MARKER_END; }
                                          else { yypushback(yylength()); yybegin(HERE_DOC_BODY); } }
  [^]                                   { yypushback(yylength()); yybegin(HERE_DOC_BODY); }
}

<HERE_DOC_BODY> {
    {InputCharacter}*             { return HEREDOC_LINE; }
    {LineTerminator}              { yybegin(HERE_DOC_END_MARKER); return HEREDOC_LINE; }
}

<YYINITIAL, EXPRESSIONS, LET_EXPRESSIONS, CASE_CLAUSE, CASE_PATTERN, HERE_DOC_PIPELINE> {
    {AssignmentWord} / {AssigOp}  { return WORD; }

    "case"                        { pushState(CASE_CLAUSE); return CASE; }
    "esac"                        { return ESAC; }
    "done"                        { return DONE; }
    "do"                          { return DO; }
    "elif"                        { return ELIF; }
    "else"                        { return ELSE; }
    "fi"                          { return FI; }
    "for"                         { return FOR; }
    "function"                    { return FUNCTION; }
    "if"                          { return IF; }
    "select"                      { return SELECT; }
    "then"                        { return THEN; }
    "until"                       { return UNTIL; }
    "while"                       { return WHILE; }
    "trap"                        { return TRAP; }
//    {LetWithQuote}                |
//    {LetWithWhitespace}           { pushState(EXPRESSIONS); return LET; }
    "let"                         { pushState(LET_EXPRESSION_START); return LET; }
//    "let" / {WhiteSpace}+         { pushState(LET_EXPRESSIONS); return LET; }

    "time"                        { return TIME; }

    {Filedescriptor}              { return FILEDESCRIPTOR; }

    "&&"                          { return AND_AND; }
    "&"                           { return AMP; }
    ";"                           { return SEMI; }
    ","                           { return COMMA; }

    "||"                          { return OR_OR; }

    "="                           { return EQ; }
    "+="                          { return ADD_EQ; }

    "[[ "                         { return LEFT_DOUBLE_BRACKET; }
    " ]]"                         { return RIGHT_DOUBLE_BRACKET; }

    "$["                          { inOldArithmeticExpansion = true; pushState(EXPRESSIONS); return ARITH_SQUARE_LEFT; }
    " ]"                          { if (inOldArithmeticExpansion) { yypushback(1); return WHITESPACE; } else return EXPR_CONDITIONAL_RIGHT; }
    "[ "                          { return EXPR_CONDITIONAL_LEFT; }
    "||"                          { return OR_OR; }
    "${"                          { pushState(PARAMETER_EXPANSION); yypushback(1); return DOLLAR;}
    "$"                           { return DOLLAR; }
    "("                           { return LEFT_PAREN; }
    ")"                           { return RIGHT_PAREN; }
    "{"                           { return LEFT_CURLY; }
    "}"                           { return RIGHT_CURLY; }
    "["                           { return LEFT_SQUARE; }
    "]"                           { if (inOldArithmeticExpansion) { inOldArithmeticExpansion = false;  popState(); return ARITH_SQUARE_RIGHT; }
                                    else return RIGHT_SQUARE; }
    ">="                          { return GE; }
    "<="                          { return LE; }
    "!="                          { return ARITH_NE; }
    "!"                           { return BANG; }
    ">"                           { return GT; }
    "<"                           { return LT; }


    "<<-" |
    "<<"                          { if (yystate() != HERE_DOC_PIPELINE) { pushState(HERE_DOC_START_MARKER); return HEREDOC_MARKER_TAG; }
                                    else return SHIFT_LEFT; }
    ">>"                          { return SHIFT_RIGHT; }


//        "!"                           { return ARITH_NEGATE; } // todo: never match wtf?



    "`"                           { return BACKQUOTE; }
    "|"                           { return PIPE; }

  "<>"                            { return REDIRECT_LESS_GREATER; }
  "<&"                            { return REDIRECT_LESS_AMP; }
  ">&"                            { return REDIRECT_GREATER_AMP; }
  ">|"                            { return REDIRECT_GREATER_BAR; }

  "|&"                            { return PIPE_AMP; }

  "&>>"                           { return REDIRECT_AMP_GREATER_GREATER; }
  "&>"                            { return REDIRECT_AMP_GREATER; }

  "<<<"                           { return REDIRECT_HERE_STRING; }

    {IntegerLiteral}              { return INT; }
    {HexIntegerLiteral}           { return HEX; }
    {OctalIntegerLiteral}         { return OCTAL; }

//    {AssignListWord}              { return WORD; }

    {Shebang}                     { return SHEBANG; }
    {Comment}                     { return COMMENT; }

    {WhiteSpace}+                 |
    {LineContinuation}+           { return WHITESPACE; }
    {LineTerminator}              { if (yystate() == HERE_DOC_PIPELINE) yybegin(HERE_DOC_END_MARKER); return LINEFEED; }
    {Variable}                    { return VAR; }

    {Quote}                       { inString = !inString; return QUOTE; } // todo: refactor
    {RawString}                   { if (inString && contains(yytext(), '"')) { yypushback(yylength() - 1); return WORD; }  else return RAW_STRING; }
}

<YYINITIAL, CASE_CLAUSE, CASE_PATTERN, HERE_DOC_PIPELINE> {
    {Word}                        { return WORD; }
    "(("                          { yybegin(EXPRESSIONS); return LEFT_DOUBLE_PAREN; }
}

[^]                               { return BAD_CHARACTER; }

