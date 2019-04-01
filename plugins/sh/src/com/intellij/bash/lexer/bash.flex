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
    private boolean inString;
    private boolean letWithQuote;
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

RightDoubleBracketWithWhiteSpace = {WhiteSpace}+ "]]"  // TODO: think about the soulution ___]]
RightBracketWithWhiteSpace = {WhiteSpace}+ "]"

%state EXPRESSIONS
%state LET_EXPRESSION_START
%state LET_EXPRESSIONS
%state OLD_EXPRESSIONS
%state CONDITIONAL_EXPRESSION

%state PARAMETER_EXPANSION

%state CASE_CLAUSE
%state CASE_PATTERN

%state HERE_DOC_START_MARKER
%state HERE_DOC_END_MARKER
%state HERE_DOC_PIPELINE
%state HERE_DOC_BODY

%%

<EXPRESSIONS, LET_EXPRESSIONS, OLD_EXPRESSIONS> {
    "*="                          { return ARITH_ASS_MUL; }
    "/="                          { return ARITH_ASS_DIV; }
    "%="                          { return ARITH_ASS_MOD; }
    "+="                          { return ARITH_ASS_PLUS; }
    "-="                          { return ARITH_ASS_MINUS; }
    ">>="                         { return ARITH_ASS_SHIFT_RIGHT; }
    "<<="                         { return ARITH_ASS_SHIFT_LEFT; }
    "&="                          { return ARITH_ASS_BIT_AND; }
    "|="                          { return ARITH_ASS_BIT_OR; }
    "^="                          { return ARITH_ASS_BIT_XOR; }
    "!="                          { return ARITH_NE; }
    "=="                          { return ARITH_EQ; }
    ">="                          { return GE; }
    "<="                          { return LE; }

    "++"                          { return ARITH_PLUS_PLUS; }
    "--"                          { return ARITH_MINUS_MINUS; }
    "**"                          { return EXPONENT; }

    "!"                           { return ARITH_NEGATE; }
    "~"                           { return ARITH_BITWISE_NEGATE; }
    "+"                           { return ARITH_PLUS; }
    "-"                           { return ARITH_MINUS; }
    "*"                           { return MULT; }
    "/"                           { return DIV; }
    "%"                           { return MOD; }

    "<<"                          { return SHIFT_LEFT; }
    ">>"                          { return SHIFT_RIGHT; }
    "<"                           { return LT; }
    ">"                           { return GT; }

    "&&"                          { return AND_AND; }
    "||"                          { return OR_OR; }
    "&"                           { return ARITH_BITWISE_AND; }
    "^"                           { return ARITH_BITWISE_XOR; }
    "|"                           { return ARITH_BITWISE_OR; }

    "?"                           { return ARITH_QMARK; }
    ":"                           { return ARITH_COLON; }
    ","                           { return COMMA; }

    {ArithWord}                   { return WORD; }
}

<CONDITIONAL_EXPRESSION> {
    "=="                          { return COND_OP_EQ_EQ; }
    "!="                          { return ARITH_NE; }
    "=~"                          { return COND_OP_REGEX; }
    "<"                           { return LT; }
    ">"                           { return GT; }
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

<YYINITIAL, EXPRESSIONS, LET_EXPRESSIONS, OLD_EXPRESSIONS, CONDITIONAL_EXPRESSION, CASE_CLAUSE, CASE_PATTERN, HERE_DOC_PIPELINE> {
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
    "let"                         { pushState(LET_EXPRESSION_START); return LET; }
    "time"                        { return TIME; }

    {Filedescriptor}              { return FILEDESCRIPTOR; }

    /***** Conditional statements *****/
    "$["                          { pushState(OLD_EXPRESSIONS); return ARITH_SQUARE_LEFT; }
    "${"                          { pushState(PARAMETER_EXPANSION); yypushback(1); return DOLLAR;}
    "[[ "                         { pushState(CONDITIONAL_EXPRESSION); return LEFT_DOUBLE_BRACKET; }
    "[ "                          { pushState(CONDITIONAL_EXPRESSION); return EXPR_CONDITIONAL_LEFT; }
    "(("                          { pushState(EXPRESSIONS); pushParentheses(yytext()); return LEFT_DOUBLE_PAREN; }
    "))"                          { if (shouldCloseDoubleParen()) { popState(); popParentheses(); return RIGHT_DOUBLE_PAREN; }
                                    else if (shouldCloseSingleParen()) { yypushback(1); popParentheses(); return RIGHT_PAREN; }
                                    else return RIGHT_DOUBLE_PAREN; }
    {RightDoubleBracketWithWhiteSpace} { popState(); return RIGHT_DOUBLE_BRACKET; }
    {RightBracketWithWhiteSpace}  { switch (yystate()) {
                                      case OLD_EXPRESSIONS: popState(); return ARITH_SQUARE_RIGHT;
                                      case CONDITIONAL_EXPRESSION: popState(); return EXPR_CONDITIONAL_RIGHT;
                                      default: return RIGHT_SQUARE;
                                    }
                                  }

    /***** General operators *****/
    "+="                          { return ADD_EQ; }
    "="                           { return EQ; }
    "$"                           { return DOLLAR; }
    "("                           { pushParentheses(yytext()); return LEFT_PAREN; }
    ")"                           { if (shouldCloseSingleParen()) { popParentheses(); } return RIGHT_PAREN; }
    "{"                           { return LEFT_CURLY; }
    "}"                           { return RIGHT_CURLY; }
    "["                           { return LEFT_SQUARE; }
    "]"                           { if (yystate() == OLD_EXPRESSIONS) { popState(); return ARITH_SQUARE_RIGHT; }
                                    return RIGHT_SQUARE; }
    "!"                           { return BANG; }
    "`"                           { return BACKQUOTE; }

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

    {Quote}                       { inString = !inString; return QUOTE; } // todo: refactor
    {RawString}                   { if (inString && contains(yytext(), '"')) { yypushback(yylength() - 1); return WORD; }  else return RAW_STRING; }
}

<YYINITIAL, CONDITIONAL_EXPRESSION, CASE_CLAUSE, CASE_PATTERN, HERE_DOC_PIPELINE> {
    {Word}                        { return WORD; }
}

[^]                               { return BAD_CHARACTER; }

