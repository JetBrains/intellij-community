package com.intellij.sh.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.IntStack;
import com.intellij.lexer.FlexLexer;
import static com.intellij.sh.lexer.ShTokenTypes.*;
import com.intellij.openapi.util.text.StringUtil;

%%

%class _ShLexerGen
%implements FlexLexer
%unicode
%public

%function advance
%type IElementType

%{
  public _ShLexerGen() {
    this(null);
  }

  private static final int DOUBLE_PARENTHESES = 2;
  private static final int PARENTHESES = 1;

  private boolean isArithmeticExpansion;
  private boolean isBackquoteOpen;
  private boolean isQuoteOpen;
  private String heredocMarker;
  private boolean heredocWithWhiteSpaceIgnore;
  private int regexStart = -1;
  private int regexGroups = 0;
  private int herestringStartPosition = -1;
  private final IntStack stateStack = new IntStack(1_000);
  private final IntStack parenStack = new IntStack(1_000);

  private void pushState(int state) {
    int currentState = yystate();
    assert currentState != YYINITIAL || stateStack.empty() : "Can't push initial state into the not empty stack";
    stateStack.push(currentState);
    yybegin(state);
  }

  private void popState() {
    assert !stateStack.empty() : "States stack is empty";
    yybegin(stateStack.pop());
  }

  private void pushParentheses(int parentheses) {
    parenStack.push(parentheses);
  }

  private void popParentheses() {
    assert !parenStack.empty() : "Parentheses stack is empty";
    parenStack.pop();
  }

  private boolean shouldCloseDoubleParen() {
    return !parenStack.empty() && parenStack.peek() == DOUBLE_PARENTHESES;
  }

  private boolean shouldCloseSingleParen() {
    return !parenStack.empty() && parenStack.peek() == PARENTHESES;
  }

  protected void onReset() {
    stateStack.clear();
    parenStack.clear();
    heredocWithWhiteSpaceIgnore = false;
    heredocMarker = null;
    isArithmeticExpansion = false;
    isQuoteOpen = false;
    isBackquoteOpen = false;
    herestringStartPosition = -1;
    regexStart = -1;
    regexGroups = 0;
  }
%}

/***** Custom user code *****/

InputCharacter           = [^\r\n]
LineTerminator           = \r\n | \r | \n
LineContinuation         = "\\" {LineTerminator}
WhiteSpace               = [ \t\f] {LineContinuation}*

Shebang                  = #\! {InputCharacter}*
Comment                  = # {InputCharacter}*

EscapedChar              = "\\" [^\n]
EscapedAnyChar           = {EscapedChar} | {LineContinuation}
Quote                    = \"

UnclosedRawString        = '[^']*
DollarSignRawString      = "$'"([^'] | {EscapedChar})*
RawString                = {UnclosedRawString}' | {DollarSignRawString}'

WordFirst                = [[:letter:]||[:digit:]||[_/@?.*:%\^+,~-]] | {EscapedAnyChar} | [\u00C0-\u00FF]
WordAfter                = {WordFirst} | [#!]
Word                     = {WordFirst}{WordAfter}*

ArithWordFirst           = [a-zA-Z_@.] | {EscapedAnyChar}
ArithWordAfter           = {ArithWordFirst} | [0-9#!]
ArithWord                = {ArithWordFirst}{ArithWordAfter}*

AssignmentWord           = [[:letter:]||[_]] [[:letter:]||[0-9_]]*
Variable                 = \$ {AssignmentWord} | \$[@$#0-9?!*_-]

IntegerLiteral           = [0] | ([1-9][0-9]*) | (6[0-4]|[1-5][0-9]|[2-9]) # [[:letter:]||[:digit:]]*  // todo: fix Rule can never be matched
HexIntegerLiteral        = "0" [xX] [0-9a-fA-F]+
OctalIntegerLiteral      = "0" [0-7]+

PatternSimple = [*?]
PatternExt = ([?*+@!] "(" [^)]+ ")" ) | ([?*+@!] "[" [^]]+ "]")
Pattern = ({PatternSimple} | {PatternExt})+

CaseFirst                = {EscapedChar} | {Pattern} | [^|\"'$)(# \n\r\f\t\f]
CaseAfter                = {EscapedChar} | {Pattern} | [^|\"'$`)( \n\r\f\t\f;]
CasePattern              = {CaseFirst} ({LineContinuation}? {CaseAfter})*

Filedescriptor           = "&" {IntegerLiteral} | "&-"  //todo:: check the usage ('<&' | '>&') (num | '-') in parser
AssigOp                  = "=" | "+="

ParamExpansionName       = ([a-zA-Z0-9_] | {EscapedAnyChar})+
ParamExpansionSeparator  = "#""#"? | "!" | ":" | ":"?"=" | ":"?"+" | ":"?"-" | ":"?"?" | "@" | ","","? | "^""^"? | "*"

HeredocMarker            = [^\r\n|&\\;()[] \t\"'] | {EscapedChar}
HeredocMarkerInQuotes    = {HeredocMarker}+ | '{HeredocMarker}+' | \"{HeredocMarker}+\"

RegexWord                = [^\r\n\\\"' \t$`()] | {EscapedChar}
Regex                    = {RegexWord}+

HereString               = [^\r\n$` \"';()|>&] | {EscapedChar}
StringContent            = [^$\"`(\\] | {EscapedAnyChar}
EvalContent              = [^\r\n$\"`'() ;] | {EscapedAnyChar}

%state ARITHMETIC_EXPRESSION
%state OLD_ARITHMETIC_EXPRESSION
%state LET_EXPRESSION
%state EVAL_EXPRESSION
%state TEST_EXPRESSION
%state CONDITIONAL_EXPRESSION

%state IF_CONDITION
%state OTHER_CONDITIONS
%state CASE_CONDITION
%state CASE_PATTERN

%state STRING_EXPRESSION
%state REGULAR_EXPRESSION

%state HERE_STRING

%state HERE_DOC_START_MARKER
%state HERE_DOC_END_MARKER
%state HERE_DOC_PIPELINE
%state HERE_DOC_BODY

%state PARAMETER_EXPANSION
%state PARAMETER_EXPANSION_EXPR
%state PARENTHESES_COMMAND_SUBSTITUTION
%state BACKQUOTE_COMMAND_SUBSTITUTION
%%

<STRING_EXPRESSION> {
    {Quote}                       { popState(); return CLOSE_QUOTE; }
    "$" | "(" | {StringContent}+  { return STRING_CONTENT; }
}

<EVAL_EXPRESSION> {
   {Quote}                       { pushState(STRING_EXPRESSION); return OPEN_QUOTE; }
   "`"                           { if (isBackquoteOpen) { popState(); yypushback(yylength()); }
                                   else { pushState(BACKQUOTE_COMMAND_SUBSTITUTION); isBackquoteOpen = true; return OPEN_BACKQUOTE; } }
   {WhiteSpace}+                 { return WHITESPACE; }
   {RawString}                   { return RAW_STRING; }
   ")" | ";"                     |
   {LineTerminator}              { popState(); yypushback(yylength()); }
   "$" | "(" | {EvalContent}+    { return EVAL_CONTENT; }
}

<STRING_EXPRESSION, EVAL_EXPRESSION> {
   "$(("                         { isArithmeticExpansion = true; yypushback(2); return DOLLAR; }
   "(("                          { if (isArithmeticExpansion) { pushState(ARITHMETIC_EXPRESSION);
                                       pushParentheses(DOUBLE_PARENTHESES); isArithmeticExpansion = false; return LEFT_DOUBLE_PAREN; }
                                   else return STRING_CONTENT; }
   "$("                          { pushState(PARENTHESES_COMMAND_SUBSTITUTION); yypushback(1); return DOLLAR; }
   "${"                          { pushState(PARAMETER_EXPANSION); yypushback(1); return DOLLAR;}
   "$["                          { pushState(OLD_ARITHMETIC_EXPRESSION); return ARITH_SQUARE_LEFT; }
   "`"                           { pushState(BACKQUOTE_COMMAND_SUBSTITUTION); isBackquoteOpen = true; return OPEN_BACKQUOTE; }
   {Variable}                    { return VAR; }
}

<LET_EXPRESSION> {
    "let"                         { return LET; }
    {Quote}                       { if (isQuoteOpen) { isQuoteOpen = false; return CLOSE_QUOTE; }
                                    else { isQuoteOpen = true; return OPEN_QUOTE; } }
    ";"                           { popState(); return SEMI; }
    {LineTerminator}              { popState(); return LINEFEED; }
}

<TEST_EXPRESSION> {
    "!="                          { return WORD; }
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
    "=~"                          { regexStart = getTokenEnd(); regexGroups = 0; pushState(REGULAR_EXPRESSION); return REGEXP; }
    "<"                           { return LT; }
    ">"                           { return GT; }
}


<REGULAR_EXPRESSION, PARAMETER_EXPANSION> {
  "$(("                              { isArithmeticExpansion = true; yypushback(2); return DOLLAR; }
  "$("                               { pushState(PARENTHESES_COMMAND_SUBSTITUTION); yypushback(1); return DOLLAR; }
  "${"                               { pushState(PARAMETER_EXPANSION); yypushback(1); return DOLLAR;}
  "$["                               { pushState(OLD_ARITHMETIC_EXPRESSION); return ARITH_SQUARE_LEFT; }
  "`"                                { pushState(BACKQUOTE_COMMAND_SUBSTITUTION); isBackquoteOpen = true; return OPEN_BACKQUOTE; }

  {Variable}                         { return VAR; }
  {Quote}                            { pushState(STRING_EXPRESSION); return OPEN_QUOTE; }
  {RawString}                        |
  {UnclosedRawString}                { return RAW_STRING; }
}

<REGULAR_EXPRESSION> {
  "(("                            { if (isArithmeticExpansion) { pushState(ARITHMETIC_EXPRESSION);
                                       pushParentheses(DOUBLE_PARENTHESES); isArithmeticExpansion = false; return LEFT_DOUBLE_PAREN; }
                                    else { yypushback(1); regexGroups++; return WORD; } }
  "$"                             { return WORD; }
  "("                             { regexGroups++; return WORD; }
  ")"                             { if (regexGroups <= 0) { regexGroups = 0; popState(); return RIGHT_PAREN; } else { regexGroups--; return WORD; } }

  {Regex}                         { return WORD; }
  {WhiteSpace}+                   { if (regexGroups <= 0 && regexStart != getTokenStart()) { regexStart = -1; popState(); }; return WHITESPACE; }
}

<PARAMETER_EXPANSION> {
  "(("                               { if (isArithmeticExpansion) { pushState(ARITHMETIC_EXPRESSION);
                                          pushParentheses(DOUBLE_PARENTHESES); isArithmeticExpansion = false; return LEFT_DOUBLE_PAREN; }
                                      else return WORD; }
  "[["                               { pushState(CONDITIONAL_EXPRESSION); return LEFT_DOUBLE_BRACKET; }
  "["                                { pushState(CONDITIONAL_EXPRESSION); return LEFT_SQUARE; }
  "{"                                {             return LEFT_CURLY; }
  "}"                                { popState(); return RIGHT_CURLY; }

  {ParamExpansionSeparator}          { return PARAM_SEPARATOR; }
  "%""%"? | "/""/"?                  { pushState(PARAMETER_EXPANSION_EXPR); return PARAM_SEPARATOR; }
  {IntegerLiteral}                   { return INT; }
  {HexIntegerLiteral}                { return HEX; }
  {OctalIntegerLiteral}              { return OCTAL; }
  {ParamExpansionName}               { return WORD; }
  {WhiteSpace}+                      |
  {LineContinuation}+                { return WHITESPACE; }
  {LineTerminator}                   { return LINEFEED; }
  [^]                                { return WORD; }
}

<PARAMETER_EXPANSION_EXPR> {
  [^}/$`\"]+                           { popState(); return WORD; }
  [^]                                  { popState(); yypushback(yylength()); }
}

<CASE_CONDITION> {
  ";&" | ";;&" | ";;"             { pushState(CASE_PATTERN);    return CASE_END; }
  "in"                            { if (yystate() == CASE_CONDITION) {pushState(CASE_PATTERN); return IN; } else return WORD; }
}

<CASE_PATTERN> {
  "esac"                          { popState(); yypushback(yylength()); }
  ")"                             { popState(); return RIGHT_PAREN; }
  {CasePattern}                   { return WORD; }
}

<HERE_STRING> {
  ">"  | ";" | "|" | "(" | ")"  |
  "&"  | "`"                    { herestringStartPosition=-1; popState(); yypushback(1);}
  {LineTerminator}              { herestringStartPosition=-1; popState(); return LINEFEED; }
  {WhiteSpace}+                 { if (herestringStartPosition != getTokenStart()) { herestringStartPosition=-1; popState(); } return WHITESPACE; }
  {HereString}+                 { return WORD; }
}

<HERE_DOC_START_MARKER> {
  {HeredocMarkerInQuotes}         { if ((yycharat(yylength()-1) == '\'' || yycharat(yylength()-1) == '"') && yylength() > 2)
                                      heredocMarker = yytext().subSequence(1, yylength()-1).toString();
                                    else heredocMarker = yytext().toString();
                                    heredocMarker = heredocMarker.replaceAll("(\\\\)(.)", "$2");
                                    yybegin(HERE_DOC_PIPELINE);
                                    return HEREDOC_MARKER_START; }
  {WhiteSpace}+                   { return WHITESPACE; }
  {LineTerminator}                { popState(); return LINEFEED; }
}

<HERE_DOC_PIPELINE> {
  {LineTerminator}                { yybegin(HERE_DOC_END_MARKER); return LINEFEED; }
}

<HERE_DOC_END_MARKER> {
  {WhiteSpace}+                   { if (!heredocWithWhiteSpaceIgnore) yybegin(HERE_DOC_BODY); return HEREDOC_CONTENT; }
  {HeredocMarker}+                { if (yytext().toString().equals(heredocMarker))
                                  { heredocMarker = null; heredocWithWhiteSpaceIgnore = false; popState(); return HEREDOC_MARKER_END; }
                                    else { yypushback(yylength()); yybegin(HERE_DOC_BODY); } }
  [^]                             { yypushback(yylength()); yybegin(HERE_DOC_BODY); }
}

<HERE_DOC_BODY> {
    {InputCharacter}+             { return HEREDOC_CONTENT; }
    {LineTerminator}              { yybegin(HERE_DOC_END_MARKER); return HEREDOC_CONTENT; }
}

<YYINITIAL, CASE_CONDITION, CASE_PATTERN, IF_CONDITION, OTHER_CONDITIONS, PARENTHESES_COMMAND_SUBSTITUTION, BACKQUOTE_COMMAND_SUBSTITUTION> {

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
    "let"                         { pushState(LET_EXPRESSION); return LET; }
    "eval"                        { pushState(EVAL_EXPRESSION); return EVAL; }
    "test"                        { pushState(TEST_EXPRESSION); return TEST; }
}

<YYINITIAL, ARITHMETIC_EXPRESSION, OLD_ARITHMETIC_EXPRESSION, LET_EXPRESSION, TEST_EXPRESSION, CONDITIONAL_EXPRESSION, HERE_DOC_PIPELINE,
  CASE_CONDITION, CASE_PATTERN, IF_CONDITION, OTHER_CONDITIONS, PARENTHESES_COMMAND_SUBSTITUTION, BACKQUOTE_COMMAND_SUBSTITUTION, HERE_STRING> {
    {AssignmentWord} / {AssigOp}  { return WORD; }
    {Filedescriptor}              { return FILEDESCRIPTOR; }

    /***** Conditional statements *****/
    "$(("                         { yypushback(2); return DOLLAR; }
    "(("                          { pushState(ARITHMETIC_EXPRESSION); pushParentheses(DOUBLE_PARENTHESES);
                                    return LEFT_DOUBLE_PAREN; }
    "$["                          { pushState(OLD_ARITHMETIC_EXPRESSION); return ARITH_SQUARE_LEFT; }
    "$("                          { pushState(PARENTHESES_COMMAND_SUBSTITUTION); yypushback(1); return DOLLAR; }
    "${"                          { pushState(PARAMETER_EXPANSION); yypushback(1); return DOLLAR;}
    "[["                          { pushState(CONDITIONAL_EXPRESSION); return LEFT_DOUBLE_BRACKET; }
    "["                           { pushState(CONDITIONAL_EXPRESSION); return LEFT_SQUARE; }
    "))"                          { if (shouldCloseDoubleParen()) { popState(); popParentheses(); return RIGHT_DOUBLE_PAREN; }
                                    else if (shouldCloseSingleParen()) {
                                      if (yystate() == PARENTHESES_COMMAND_SUBSTITUTION) popState(); yypushback(1); popParentheses(); return RIGHT_PAREN;
                                    } else return RIGHT_DOUBLE_PAREN; }
    "]]"                          { if (yystate() == CONDITIONAL_EXPRESSION) popState(); return RIGHT_DOUBLE_BRACKET; }
    "]"                           { switch (yystate()) {
                                      case OLD_ARITHMETIC_EXPRESSION: popState(); return ARITH_SQUARE_RIGHT;
                                      case CONDITIONAL_EXPRESSION: popState(); return RIGHT_SQUARE;
                                      default: return RIGHT_SQUARE; }
                                  }

    /***** General operators *****/
    "+="                          { return PLUS_ASSIGN; }
    "="                           { return ASSIGN; }
    "$"                           { return DOLLAR; }
    "("                           { pushParentheses(PARENTHESES); return LEFT_PAREN; }
    ")"                           { if (shouldCloseSingleParen()) popParentheses();
                                    if (yystate() == PARENTHESES_COMMAND_SUBSTITUTION) popState(); return RIGHT_PAREN; }
    "{"                           { return LEFT_CURLY; }
    "}"                           { return RIGHT_CURLY; }

    "!"                           { return BANG; }
    "`"                           { if (yystate() == BACKQUOTE_COMMAND_SUBSTITUTION) { popState(); isBackquoteOpen = false; return CLOSE_BACKQUOTE; }
                                    else { pushState(BACKQUOTE_COMMAND_SUBSTITUTION); isBackquoteOpen = true; return OPEN_BACKQUOTE; } }

    /***** Pipeline separators *****/
    "&&"                          { return AND_AND; }
    "||"                          { return OR_OR; }
    "&"                           { return AMP; }
    ";"                           { return SEMI; }

    /***** Pipelines *****/
    "|"                           { return PIPE; }
    "|&"                          { return PIPE_AMP; }

    /***** Redirections *****/
    "&>>"                         { return REDIRECT_AMP_GREATER_GREATER; }
    "<&"                          { return REDIRECT_LESS_AMP; }
    ">&"                          { return REDIRECT_GREATER_AMP; }
    "<>"                          { return REDIRECT_LESS_GREATER; }
    "&>"                          { return REDIRECT_AMP_GREATER; }
    ">|"                          { return REDIRECT_GREATER_BAR; }
    ">("                          { return OUTPUT_PROCESS_SUBSTITUTION; }
    "<("                          { return INPUT_PROCESS_SUBSTITUTION; }

    "<<<"                         { herestringStartPosition = getTokenEnd(); pushState(HERE_STRING); return REDIRECT_HERE_STRING; }
    "<<-"                         { if (yystate() != HERE_DOC_PIPELINE)
                                    { pushState(HERE_DOC_START_MARKER); heredocWithWhiteSpaceIgnore = true; return HEREDOC_MARKER_TAG; }
                                    else return SHIFT_LEFT; }
    "<<"                          { if (yystate() != HERE_DOC_PIPELINE)
                                    { pushState(HERE_DOC_START_MARKER); return HEREDOC_MARKER_TAG; }
                                    else return SHIFT_LEFT; }
    ">>"                          { return SHIFT_RIGHT; }
    ">"                           { return GT; }
    "<"                           { return LT; }

    {IntegerLiteral}              { return INT; }
    {HexIntegerLiteral}           { return HEX; }
    {OctalIntegerLiteral}         { return OCTAL; }

    ^{Shebang}                    { if (getTokenStart() == 0) return SHEBANG; else return COMMENT; }
    {Comment}                     { return COMMENT; }

    {WhiteSpace}+                 |
    {LineContinuation}+           { return WHITESPACE; }
    {LineTerminator}              { return LINEFEED; }
    {Variable}                    { return VAR; }

    {Quote}                       { pushState(STRING_EXPRESSION); return OPEN_QUOTE; }

    {RawString}                   |
    {UnclosedRawString}           { return RAW_STRING; }
}

<YYINITIAL, CONDITIONAL_EXPRESSION, HERE_DOC_PIPELINE, CASE_CONDITION, CASE_PATTERN, IF_CONDITION,
  OTHER_CONDITIONS, PARENTHESES_COMMAND_SUBSTITUTION, BACKQUOTE_COMMAND_SUBSTITUTION, TEST_EXPRESSION> {
    {PatternExt}+                 |
    {Word}                        { return WORD; }
}

[^]                               { return BAD_CHARACTER; }

