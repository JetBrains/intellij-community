package com.intellij.bash.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.IntStack;
import com.intellij.util.containers.Stack;
import com.intellij.lexer.FlexLexer;
import static com.intellij.bash.lexer.BashTokenTypes.*;
import static org.apache.commons.lang3.StringUtils.contains;

%%

%class _BashLexerGen
%implements FlexLexer
%unicode
%public

%function advance
%type IElementType

%{
  public _BashLexerGen() { this(null); }
    private void pushState(int state) { stateStack.push(yystate()); yybegin(state);}
    private void popState() { if (!stateStack.empty()) yybegin(stateStack.pop());}
    private void switchState(int state) { popState(); pushState(state); }
    private IntStack stateStack = new IntStack(20);
    private void pushParentheses(CharSequence parentheses) { parenStack.push(parentheses); }
    private void popParentheses() { if (!parenStack.empty()) parenStack.pop(); }
    private boolean shouldCloseDoubleParen() { return !parenStack.empty() && parenStack.peek().equals("(("); }
    private boolean shouldCloseSingleParen() { return !parenStack.empty() && parenStack.peek().equals("("); }
    private Stack<CharSequence> parenStack = new Stack<>();
    private CharSequence heredocMarker;

    protected void onReset() { stateStack.clear(); }
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

%state ARITHMETIC_EXPRESSION
%state OLD_ARITHMETIC_EXPRESSION
%state LET_EXPRESSION
%state CONDITIONAL_EXPRESSION

%state IF_CONDITION
%state OTHER_CONDITIONS
%state CASE_CONDITION
%state CASE_PATTERN

%state STRING_EXPRESSION

%state HERE_DOC_START_MARKER
%state HERE_DOC_END_MARKER
%state HERE_DOC_PIPELINE
%state HERE_DOC_BODY

%state PARAMETER_EXPANSION
%state COMMAND_SUBSTITUTION
%%

<STRING_EXPRESSION> {
    {Quote}                       { popState(); return QUOTE; }
    {RawString}                   { if (contains(yytext(), '"')) { yypushback(yylength() - 1); return WORD; }  else return RAW_STRING; }
}

<LET_EXPRESSION> {
    "let"                         { return LET; }
    {Quote}                       { return QUOTE; }
    ";"                           { popState(); return SEMI; }
    {LineTerminator}              { popState(); return LINEFEED; }
}

<ARITHMETIC_EXPRESSION, OLD_ARITHMETIC_EXPRESSION, LET_EXPRESSION> {
    "*="                          { return MULT_ASSIGN; }
    "/="                          { return DIV_ASSIGN; }
    "%="                          { return MOD_ASSIGN; }
    "+="                          { return PLUS_ASSIGN; }
    "-="                          { return MINUS_ASSIGN; }
    ">>="                         { return SHIFT_RIGHT_ASSIGN; }
    "<<="                         { return SHIFT_LEFT_ASSIGN; }
    "&="                          { return BIT_AND_ASSIGN; }
    "|="                          { return BIT_OR_ASSIGN; }
    "^="                          { return BIT_XOR_ASSIGN; }
    "!="                          { return NE; }
    "=="                          { return EQ; }
    ">="                          { return GE; }
    "<="                          { return LE; }

    "++"                          { return PLUS_PLUS; }
    "--"                          { return MINUS_MINUS; }
    "**"                          { return EXPONENT; }

    "!"                           { return BANG; }
    "~"                           { return BITWISE_NEGATION; }
    "+"                           { return PLUS; }
    "-"                           { return MINUS; }
    "*"                           { return MULT; }
    "/"                           { return DIV; }
    "%"                           { return MOD; }

    "<<"                          { return SHIFT_LEFT; }
    ">>"                          { return SHIFT_RIGHT; }
    "<"                           { return LT; }
    ">"                           { return GT; }

    "&&"                          { return AND_AND; }
    "||"                          { return OR_OR; }
    "&"                           { return AMP; }
    "^"                           { return XOR; }
    "|"                           { return PIPE; }

    "?"                           { return QMARK; }
    ":"                           { return COLON; }
    ","                           { return COMMA; }

    {ArithWord}                   { return WORD; }
}

<CONDITIONAL_EXPRESSION> {
    "=="                          { return EQ; }
    "!="                          { return NE; }
    "=~"                          { return REGEXP; }
    "<"                           { return LT; }
    ">"                           { return GT; }
}

<PARAMETER_EXPANSION> {
  "{"                                 {             return LEFT_CURLY; }
  "}"                                 { popState(); return RIGHT_CURLY; }
  ([^{}]|{EscapedRightCurly})+ / "}"  {             return PARAMETER_EXPANSION_BODY; }
  [^]                                 { popState(); }
}

<CASE_CONDITION> {
  ";&" | ";;&" | ";;"             { pushState(CASE_PATTERN);    return CASE_END; }
  "in"                            { if (yystate() == CASE_CONDITION) pushState(CASE_PATTERN); return WORD; }
}

<CASE_PATTERN> {
  "esac"                          { popState(); yypushback(yylength()); }
  ")"                             { popState(); return RIGHT_PAREN; }
  {CasePattern}                   { return WORD; }
}

<HERE_DOC_START_MARKER> {
  {HeredocMarkerInQuotes}         { if ((yycharat(yylength()-1) == '\'' || yycharat(yylength()-1) == '"') && yylength() > 2)
                                      heredocMarker = yytext().subSequence(1, yylength()-1);
                                    else heredocMarker = yytext();
                                    yybegin(HERE_DOC_PIPELINE);
                                    return HEREDOC_MARKER_START; }
  {WhiteSpace}+                   { return WHITESPACE; }
  {LineTerminator}                { popState(); return LINEFEED; }
}

<HERE_DOC_PIPELINE> {
  {LineTerminator}                { yybegin(HERE_DOC_END_MARKER); return LINEFEED; }
}

<HERE_DOC_END_MARKER> {
  {HeredocMarker}+                { if (yytext().equals(heredocMarker)) { heredocMarker = ""; popState(); return HEREDOC_MARKER_END; }
                                    else { yypushback(yylength()); yybegin(HERE_DOC_BODY); } }
  [^]                             { yypushback(yylength()); yybegin(HERE_DOC_BODY); }
}

<HERE_DOC_BODY> {
    {InputCharacter}*             { return HEREDOC_LINE; }
    {LineTerminator}              { yybegin(HERE_DOC_END_MARKER); return HEREDOC_LINE; }
}

<YYINITIAL, CASE_CONDITION, CASE_PATTERN, IF_CONDITION, OTHER_CONDITIONS, COMMAND_SUBSTITUTION> {

    "case"                        { pushState(CASE_CONDITION); return CASE; }
    "esac"                        { if (yystate() == CASE_CONDITION) popState(); return ESAC; }
    "done"                        { if (yystate() == OTHER_CONDITIONS) popState(); return DONE; }
    "do"                          { return DO; }
    "elif"                        { return ELIF; }
    "else"                        { return ELSE; }
    "fi"                          { if (yystate() == IF_CONDITION) popState(); return FI; }
    "for"                         { pushState(OTHER_CONDITIONS); return FOR; }
    "function"                    { return FUNCTION; }
    "if"                          { pushState(IF_CONDITION); return IF; }
    "select"                      { pushState(OTHER_CONDITIONS); return SELECT; }
    "then"                        { return THEN; }
    "until"                       { pushState(OTHER_CONDITIONS); return UNTIL; }
    "while"                       { pushState(OTHER_CONDITIONS); return WHILE; }
    "trap"                        { return TRAP; }
    "let"                         { pushState(LET_EXPRESSION); return LET; }
    "time"                        { return TIME; }
}

<YYINITIAL, ARITHMETIC_EXPRESSION, OLD_ARITHMETIC_EXPRESSION, LET_EXPRESSION, CONDITIONAL_EXPRESSION, HERE_DOC_PIPELINE, STRING_EXPRESSION,
  CASE_CONDITION, CASE_PATTERN, IF_CONDITION, OTHER_CONDITIONS, COMMAND_SUBSTITUTION> {
    {AssignmentWord} / {AssigOp}  { return WORD; }
    {Filedescriptor}              { return FILEDESCRIPTOR; }

    /***** Conditional statements *****/
    "$["                          { pushState(OLD_ARITHMETIC_EXPRESSION); return ARITH_SQUARE_LEFT; }
    "$("                          { pushState(COMMAND_SUBSTITUTION); yypushback(1); return DOLLAR; }
    "${"                          { pushState(PARAMETER_EXPANSION); yypushback(1); return DOLLAR;}
    "[[ "                         { pushState(CONDITIONAL_EXPRESSION); return LEFT_DOUBLE_BRACKET; }
    "[ "                          { pushState(CONDITIONAL_EXPRESSION); return EXPR_CONDITIONAL_LEFT; }
    "(("                          { pushState(ARITHMETIC_EXPRESSION); pushParentheses(yytext()); return LEFT_DOUBLE_PAREN; }
    "))"                          { if (shouldCloseDoubleParen()) { popState(); popParentheses(); return RIGHT_DOUBLE_PAREN; }
                                    else if (shouldCloseSingleParen()) { yypushback(1); popParentheses(); return RIGHT_PAREN; }
                                    else return RIGHT_DOUBLE_PAREN; }
    {WhiteSpace}+ "]]"            { if (yystate() == CONDITIONAL_EXPRESSION) popState(); return RIGHT_DOUBLE_BRACKET; }
    {WhiteSpace}+ "]"             { switch (yystate()) {
                                      case OLD_ARITHMETIC_EXPRESSION: popState(); return ARITH_SQUARE_RIGHT;
                                      case CONDITIONAL_EXPRESSION: popState(); return EXPR_CONDITIONAL_RIGHT;
                                      default: return RIGHT_SQUARE; }
                                  }

    /***** General operators *****/
    "+="                          { return PLUS_ASSIGN; }
    "="                           { return ASSIGN; }
    "$"                           { return DOLLAR; }
    "("                           { pushParentheses(yytext()); return LEFT_PAREN; }
    ")"                           { if (shouldCloseSingleParen()) popParentheses();
                                    if (yystate() == COMMAND_SUBSTITUTION) popState(); return RIGHT_PAREN; }
    "{"                           { return LEFT_CURLY; }
    "}"                           { return RIGHT_CURLY; }
    "["                           { return LEFT_SQUARE; }
    "]"                           { switch (yystate()) {
                                      case OLD_ARITHMETIC_EXPRESSION: popState(); return ARITH_SQUARE_RIGHT;
                                      case CONDITIONAL_EXPRESSION: popState(); return RIGHT_SQUARE;
                                      default: return RIGHT_SQUARE; }
                                  }
    "!"                           { return BANG; }
    "`"                           { if (yystate() == COMMAND_SUBSTITUTION) popState(); else pushState(COMMAND_SUBSTITUTION);
                                  return BACKQUOTE; }

    /***** Pipeline separators *****/
    "&&"                          { return AND_AND; }
    "||"                          { return OR_OR; }
    "&"                           { return AMP; }
    ";"                           { return SEMI; }

    /***** Pipelines *****/
    "|"                           { return PIPE; }
    "|&"                          { return PIPE_AMP; }

    /***** Redirections *****/
    "<&"                          { return REDIRECT_LESS_AMP; }
    ">&"                          { return REDIRECT_GREATER_AMP; }
    "<>"                          { return REDIRECT_LESS_GREATER; }
    "&>"                          { return REDIRECT_AMP_GREATER; }
    ">|"                          { return REDIRECT_GREATER_BAR; }
    "&>>"                         { return REDIRECT_AMP_GREATER_GREATER; }

    "<<<"                         { return REDIRECT_HERE_STRING; }
    "<<-" |
    "<<"                          { if (yystate() != HERE_DOC_PIPELINE) { pushState(HERE_DOC_START_MARKER); return HEREDOC_MARKER_TAG; }
                                    else return SHIFT_LEFT; }
    ">>"                          { return SHIFT_RIGHT; }
    ">"                           { return GT; }
    "<"                           { return LT; }

    {IntegerLiteral}              { return INT; }
    {HexIntegerLiteral}           { return HEX; }
    {OctalIntegerLiteral}         { return OCTAL; }

//    {AssignListWord}              { return WORD; }

    {Shebang}                     { return SHEBANG; }
    {Comment}                     { return COMMENT; }

    {WhiteSpace}+                 |
    {LineContinuation}+           { return WHITESPACE; }
    {LineTerminator}              { return LINEFEED; }
    {Variable}                    { return VAR; }

    {Quote}                       { pushState(STRING_EXPRESSION); return QUOTE; }
    {RawString}                   { return RAW_STRING; }
}

<YYINITIAL, CONDITIONAL_EXPRESSION, HERE_DOC_PIPELINE, STRING_EXPRESSION, CASE_CONDITION, CASE_PATTERN, IF_CONDITION,
  OTHER_CONDITIONS, COMMAND_SUBSTITUTION> {
    {Word}                        { return WORD; }
}

[^]                               { return BAD_CHARACTER; }

