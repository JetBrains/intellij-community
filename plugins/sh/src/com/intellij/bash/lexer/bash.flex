package com.intellij.bash.lexer;

import com.intellij.psi.tree.IElementType;
import static com.intellij.bash.lexer.BashTokenTypes.*;
%%

%class _BashLexerGen
%implements StatesSwitcher
%abstract
%unicode
%public

%function advance
%type IElementType

%{
    long yychar = 0;

    // return the complete buffer
    protected CharSequence getBuffer() {
        return zzBuffer;
    }
%}

/***** Custom user code *****/

InputCharacter = [^\r\n]
LineTerminator = \r\n | \r | \n
LineContinuation = "\\" {LineTerminator}
WhiteSpace=[ \t\f]
WhiteSpaceLineContinuation={WhiteSpace} {LineContinuation}*

Shebang = "#!" {InputCharacter}* {LineTerminator}?
Comment = "#"  {InputCharacter}*

EscapedChar = "\\" [^\n]
Quote       = "\""

SingleCharacter = [^\'] | {EscapedChar}
UnescapedCharacter = [^\']

RawString = "'"[^"'"]*"'"

WordFirst = [\p{Letter}||\p{Digit}||[_/@?.*:%\^+,~-]] | {EscapedChar} | [\u00C0-\u00FF] | {LineContinuation}
WordAfter = {WordFirst} | [#!] // \[\]

ArithWordFirst = [a-zA-Z_@?.:] | {EscapedChar} | {LineContinuation}
// No "[" | "]"
ArithWordAfter = {ArithWordFirst} | [0-9#!]

ParamExpansionWordFirst = [a-zA-Z0-9_] | {EscapedChar} | {LineContinuation}
ParamExpansionWord = {ParamExpansionWordFirst}+

AssignListWordFirst = [[\p{Letter}]||[0-9_/@?.*:&%\^~,]] | {EscapedChar} | {LineContinuation}
AssignListWordAfter =  {AssignListWordFirst} | [#!]
AssignListWord = {AssignListWordFirst}{AssignListWordAfter}*

Word = {WordFirst}{WordAfter}*
ArithWord = {ArithWordFirst}{ArithWordAfter}*
AssignmentWord = [[\p{Letter}]||[_]] [[\p{Letter}]||[0-9_]]*
Variable = "$" {AssignmentWord} | "$"[@$#0-9?!*_-]

ArithExpr = ({ArithWord} | [0-9a-z+*-] | {Variable} )+

IntegerLiteral = [0] | ([1-9][0-9]*) // todo: fix Rule can never be matched
HexIntegerLiteral = "0x" [0-9a-fA-F]+
OctalIntegerLiteral = "0" [0-7]+

CaseFirst={EscapedChar} | [^|\"'$)(# \n\r\f\t\f]
CaseAfter={EscapedChar} | [^|\"'$`)( \n\r\f\t\f;]
CasePattern = {CaseFirst} ({LineContinuation}? {CaseAfter})*

Filedescriptor = "&" {IntegerLiteral} | "&-"
AssigOp = "=" | "+="

%state EXPRESSIONS

%%

<EXPRESSIONS> {
    "~"                           { return ARITH_BITWISE_NEGATE; }
    "^"                           { return ARITH_BITWISE_XOR; }

    "?"                           { return ARITH_QMARK; }
    ":"                           { return ARITH_COLON; }

    "+"                           { return ARITH_PLUS; }
    "++"                          { return ARITH_PLUS_PLUS; }
    "-"                           { return ARITH_MINUS; }
    "--"                          { return ARITH_MINUS_MINUS; }
    "=="                          { return ARITH_EQ; }

    "**"                          { return EXPONENT; }
    "*"                           { return MULT; }
    "/"                           { return DIV; }
    "%"                           { return MOD; }

    {ArithWord}                   { return WORD; }

    "))"                          { yybegin(YYINITIAL); return RIGHT_DOUBLE_PAREN; }
}


<YYINITIAL, EXPRESSIONS> {
    {AssignmentWord} / {AssigOp}  { return WORD; }

    "case"                        { return CASE; }
    "esac"                        { return ESAC; }
    "!"                           { return BANG; }
    "do"                          { return DO; }
    "done"                        { return DONE; }
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
    "let"                         { return LET; }

    "time"                        { return TIME; }

    "&"                           { return AMP; }
    ";"                           { return SEMI; }
    ","                           { return COMMA; }

    "&&"                          { return AND_AND; }
    "||"                          { return OR_OR; }

    "="                           { return EQ; }
    "+="                          { return ADD_EQ; }

    "[[ "                         { return LEFT_DOUBLE_BRACKET; }
    " ]]"                         { return RIGHT_DOUBLE_BRACKET; }

    " ]"                          { return EXPR_CONDITIONAL_RIGHT; }
    "[ "                          { return EXPR_CONDITIONAL_LEFT; }
    "&&"                          { return AND_AND; }
    "||"                          { return OR_OR; }
    "$"                           { return DOLLAR; }
    "("                           { return LEFT_PAREN; }
    ")"                           { return RIGHT_PAREN; }
    "{"                           { return LEFT_CURLY; }
    "}"                           { return RIGHT_CURLY; }
    "["                           { return LEFT_SQUARE; }
    "]"                           { return RIGHT_SQUARE; }
    ">"                           { return GT; }
    "<"                           { return LT; }
    "<<"                          { return SHIFT_LEFT; }
    ">>"                          { return SHIFT_RIGHT; }
    ">="                          { return GE; }
    "<="                          { return LE; }
    "!="                          { return ARITH_NE; }


    "<<"                          { return SHIFT_LEFT; }

//        "!"                           { return ARITH_NEGATE; } // todo: never match wtf?



    "`"                           { return BACKQUOTE; }
    "|"                           { return PIPE; }

  "<>"                            { return REDIRECT_LESS_GREATER; }
  "<&"                            { return REDIRECT_LESS_AMP; }
  ">&"                            { return REDIRECT_GREATER_AMP; }
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

    {Filedescriptor}              { return FILEDESCRIPTOR; }


    {AssignListWord}              { return WORD; }

    {Shebang}                     { return SHEBANG; }
    {Comment}                     { return COMMENT; }

    {WhiteSpaceLineContinuation}+ { return WHITESPACE; }
    {LineContinuation}+           { return WHITESPACE; }
    {LineTerminator}              { return LINEFEED; }
    {Variable}                    { return VAR; }

    {Quote}                       { return QUOTE; }

    {RawString}                   { return RAW_STRING; }
}

<YYINITIAL> {
    {Word}                        { return WORD; }
    "(("                          { yybegin(EXPRESSIONS); return LEFT_DOUBLE_PAREN; }
}



[^]                               { return BAD_CHARACTER; }

///************* STATES ************/
///* If in a conditional expression */
//%state S_TEST
//
///* If in a conditional command  [[  ]]*/
//%state S_TEST_COMMAND
//
///*  If in an arithmetic expression */
//%state S_ARITH
//
///*  If in an arithmetic expression */
//%state S_ARITH_SQUARE_MODE
//
///*  If in an arithmetic expression in an array reference */
//%state S_ARITH_ARRAY_MODE
//
///*  If in a case */
//%state S_CASE
//
///*  If in a case pattern */
//%state S_CASE_PATTERN
//
///*  If in a subshell */
//%state S_SUBSHELL
//
///*  If in the start of a subshell pre expression, i.e. after DOLLAR of $( . The same rules apply as for S_SUBSHELL except that the first ( expression does not open up a new subshell expression
//    This is done by switching into the S_SUBSHELL state right after the first LEFT_PAREN token encountered.
//*/
//%state S_DOLLAR_PREFIXED
//
///*  If in an array reference, e.g. a[0]=x */
//%state S_ARRAY
//
///*  If in an array list init, e.g. a=(first second) */
//%state S_ASSIGNMENT_LIST
//
///*  If currently a string is parsed */
//%xstate X_STRINGMODE
//
///*  To match tokens in pattern expansion mode ${...} . Needs special parsing of # */
//%state S_PARAM_EXPANSION
//
///*  To match patterns included in parameter expansions */
//%state S_PARAM_EXPANSION_PATTERN
//
///*  To match patterns included in parameter expansions */
//%state S_PARAM_EXPANSION_DELIMITER
//
///*  To match patterns included in parameter expansions */
//%state S_PARAM_EXPANSION_REPLACEMENT
//
///* To match tokens which are in between backquotes. Necessary for nested lexing, e.g. inside of conditional expressions */
//%state S_BACKQUOTE
//
///* To match heredoc documents */
//%xstate X_HEREDOC_MARKER
//%xstate X_HEREDOC_MARKER_IGNORE_TABS
//%xstate X_HEREDOC
//
///* To match here-strings */
//%xstate X_HERE_STRING
//
//%%
///***************************** INITIAL STAATE ************************************/
//<YYINITIAL, S_CASE, S_CASE_PATTERN, S_SUBSHELL, S_ASSIGNMENT_LIST> {
          //!!!!!!!!!!!!  {Shebang}                     { return SHEBANG; }
          //!!!!!!!!!!!!  {Comment}                     { return COMMENT; }
//}
//
//<X_HEREDOC_MARKER, X_HEREDOC_MARKER_IGNORE_TABS> {
                                        //    {WhiteSpaceLineCont}+        { return WHITESPACE; }
                                        //    {LineContinuation}+          { return WHITESPACE; }
                                        //    {LineTerminator}             { return LINEFEED; }
//
//      ("$"? "'" [^\']+ "'")+
//    | ("$"? \" [^\"]+ \")+
//    | [^ \t\n\r\f;&|]+ {
//        heredocState().pushMarker(yytext(), yystate() == X_HEREDOC_MARKER_IGNORE_TABS);
//        backToPreviousState();
//
//        return HEREDOC_MARKER_START;
//    }
//
//    .                            { return BAD_CHARACTER; }
//}
//
//<X_HEREDOC> {
//    {LineTerminator}+           { if (!heredocState().isEmpty()) {
//                                            return HEREDOC_LINE;
//                                      }
//                                      return LINEFEED;
//                                    }
//
//    //escaped dollar
//    \\ "$" ?                    { return HEREDOC_LINE; }
//
//    {Variable} {
//            if (heredocState().isNextMarker(yytext())) {
//                boolean ignoreTabs = heredocState().isIgnoringTabs();
//
//                heredocState().popMarker(yytext());
//                popStates(X_HEREDOC);
//
//                return ignoreTabs ? HEREDOC_MARKER_IGNORING_TABS_END : HEREDOC_MARKER_END;
//            }
//
//            return yystate() == X_HEREDOC && heredocState().isExpectingEvaluatingHeredoc() && !"$".equals(yytext().toString())
//                ? VAR
//                : HEREDOC_LINE;
//    }
//
//    [^$\n\r\\]+  {
//            //support end marker followed by a backtick if nested in a backtick command
//            CharSequence markerText = yytext();
//            boolean dropLastChar = false;
//            if (isInState(S_BACKQUOTE) && yylength() >= 2 && yycharat(yylength()-1) == '`') {
//                markerText = markerText.subSequence(0, yylength()-1);
//                dropLastChar = true;
//            }
//
//            if (heredocState().isNextMarker(markerText)) {
//                boolean ignoreTabs = heredocState().isIgnoringTabs();
//
//                heredocState().popMarker(markerText);
//                popStates(X_HEREDOC);
//
//                if (dropLastChar) {
//                    yypushback(1);
//                }
//
//                return ignoreTabs ? HEREDOC_MARKER_IGNORING_TABS_END : HEREDOC_MARKER_END;
//            }
//
//            return HEREDOC_LINE;
//    }
//
//    "$"  {
//            if (heredocState().isNextMarker(yytext())) {
//                boolean ignoreTabs = heredocState().isIgnoringTabs();
//
//                heredocState().popMarker(yytext());
//                popStates(X_HEREDOC);
//
//                return ignoreTabs ? HEREDOC_MARKER_IGNORING_TABS_END : HEREDOC_MARKER_END;
//         }
//
//         return HEREDOC_LINE;
//     }
//
//    .                            { return BAD_CHARACTER; }
//}
//
//<YYINITIAL, S_CASE, S_SUBSHELL, S_BACKQUOTE> {
//  "[ ]"                         { yypushback(1); goToState(S_TEST); setEmptyConditionalCommand(true); return EXPR_CONDITIONAL_LEFT; }
//  "[ "                          { goToState(S_TEST); setEmptyConditionalCommand(false); return EXPR_CONDITIONAL_LEFT; }
//

//
//   <S_ARITH, S_ARITH_SQUARE_MODE, S_ARITH_ARRAY_MODE, X_HERE_STRING> {
//       "&&"                         { return AND_AND; }
//
//       "||"                         { return OR_OR; }
//   }
//}
//
//<S_ARRAY> {
//    "["     { backToPreviousState(); goToState(S_ARITH_ARRAY_MODE); return LEFT_SQUARE; }
//}
//
//<S_ARITH_ARRAY_MODE> {
//    "]" / "=("|"+=("        { backToPreviousState(); goToState(S_ASSIGNMENT_LIST); return RIGHT_SQUARE; }
//    "]"                     { backToPreviousState(); return RIGHT_SQUARE; }
//}
//
//// Parenthesis lexing
//<X_STRINGMODE, X_HEREDOC, S_ARITH, S_ARITH_SQUARE_MODE, S_ARITH_ARRAY_MODE, S_CASE, X_HERE_STRING, S_ASSIGNMENT_LIST> {
//    "$" / "("               { if (yystate() == X_HEREDOC && !heredocState().isExpectingEvaluatingHeredoc()) return HEREDOC_LINE; goToState(S_DOLLAR_PREFIXED); return DOLLAR; }
//    "$" / "["               { if (yystate() == X_HEREDOC && !heredocState().isExpectingEvaluatingHeredoc()) return HEREDOC_LINE; goToState(S_DOLLAR_PREFIXED); return DOLLAR; }
//}
//
//<X_HERE_STRING> {
//    {Variable}              { if (!isInHereStringContent()) enterHereStringContent(); return VAR; }
//
//    "("                     { return LEFT_PAREN; }
//    ")"                     { yypushback(1); backToPreviousState(); }
//
//    "[" | "]" | "{" | "}" |
//    {Word}                  { if (!isInHereStringContent()) enterHereStringContent(); return WORD; }
//
//    {WhiteSpace}            { if (isInHereStringContent()) { leaveHereStringContent(); backToPreviousState(); } return WHITESPACE; }
//}
//
//<YYINITIAL, S_BACKQUOTE, S_DOLLAR_PREFIXED, S_TEST, S_CASE> {
//    //this is not lexed in state S_SUBSHELL, because BashSupport treats ((((x)))) as subshell>arithmetic and not as subshell>subshell>arithmetic
//    //this is different to the official Bash interpreter
//    //currently it's too much effort to rewrite the lexer and parser for this feature
//    <S_PARAM_EXPANSION> {
//        "((("                   { if (yystate() == S_DOLLAR_PREFIXED) backToPreviousState(); yypushback(2); goToState(S_SUBSHELL); return LEFT_PAREN; }
//    }
//
//    <S_SUBSHELL, S_PARAM_EXPANSION> {
//        "(("                { if (yystate() == S_DOLLAR_PREFIXED) backToPreviousState(); goToState(S_ARITH); return LEFT_DOUBLE_PAREN; }
//        "("                 { if (yystate() == S_DOLLAR_PREFIXED) backToPreviousState(); stringParsingState().enterSubshell(); goToState(S_SUBSHELL); return LEFT_PAREN; }
//    }
//
//    <S_SUBSHELL> {
//        "["                 { if (yystate() == S_DOLLAR_PREFIXED) backToPreviousState(); goToState(S_ARITH_SQUARE_MODE); return ARITH_SQUARE_LEFT; }
//    }
//}
//
//<YYINITIAL, S_CASE> {

//}
//<S_SUBSHELL> {
//    ")"                     { backToPreviousState(); if (stringParsingState().isInSubshell()) stringParsingState().leaveSubshell(); return RIGHT_PAREN; }
//}
//<S_CASE_PATTERN> {
//    "("                     { return LEFT_PAREN; }
//    ")"                     { backToPreviousState(); return RIGHT_PAREN; }
//}
//
//
//<S_ARITH, S_ARITH_SQUARE_MODE, S_ARITH_ARRAY_MODE> {
//  "))"                          { if (openParenthesisCount() > 0) {
//                                        decOpenParenthesisCount();
//                                        yypushback(1);
//
//                                        return RIGHT_PAREN;
//                                      } else {
//                                        backToPreviousState();
//
//                                        return RIGHT_DOUBLE_PAREN;
//                                      }
//                                    }
//
//  "("                           { incOpenParenthesisCount(); return LEFT_PAREN; }
//  ")"                           { decOpenParenthesisCount(); return RIGHT_PAREN; }
//}
//
//
//<YYINITIAL, S_ARITH, S_ARITH_SQUARE_MODE, S_CASE, S_SUBSHELL, S_BACKQUOTE> {
//   /* The long followed-by match is necessary to have at least the same length as to global Word rule to make sure this rules matches */
//   {AssignmentWord} / "[" {ArithExpr} "]"
//                                      { goToState(S_ARRAY); return ASSIGNMENT_WORD; }
//
//   {AssignmentWord} / "=("|"+=("      { goToState(S_ASSIGNMENT_LIST); return ASSIGNMENT_WORD; }
//   {AssignmentWord} / "="|"+="        { return ASSIGNMENT_WORD; }
//}
//
//<YYINITIAL, S_CASE, S_SUBSHELL, S_BACKQUOTE> {
//    <S_ARITH, S_ARITH_SQUARE_MODE> {
//                                         "="                                { return EQ; }
//   }
//
                    //   "+="                               { return ADD_EQ; }
//}
//
//<S_ASSIGNMENT_LIST> {
//  "("                             { return LEFT_PAREN; }
//  ")"                             { backToPreviousState(); return RIGHT_PAREN; }
//  "+="                            { return ADD_EQ; }
//  "="                             { return EQ; }
//
// "["                              { goToState(S_ARITH_ARRAY_MODE); return LEFT_SQUARE; }
//  {AssignListWord}                { return WORD; }
//}
//
//<S_ARITH, S_ARITH_SQUARE_MODE, S_ARITH_ARRAY_MODE> {
//  ","                             { return COMMA; }
//}
//
//<YYINITIAL, S_CASE, S_SUBSHELL, S_BACKQUOTE> {
///* keywords and expressions */
//  "case"                        { setInCaseBody(false); goToState(S_CASE); return CASE; }
//
//  "!"                           { return BANG; }
//  "do"                          { return DO; }
//  "done"                        { return DONE; }
//  "elif"                        { return ELIF; }
//  "else"                        { return ELSE; }
//  "fi"                          { return FI; }
//  "for"                         { return FOR; }
//  "function"                    { return FUNCTION; }
//  "if"                          { return IF; }
//  "select"                      { return SELECT; }
//  "then"                        { return THEN; }
//  "until"                       { return UNTIL; }
//  "while"                       { return WHILE; }
//  "[[ "                         { goToState(S_TEST_COMMAND); return LEFT_DOUBLE_BRACKET; }
//  "trap"                        { return TRAP; }
//  "let"                         { return LET; }
//}
///***************** _______ END OF INITIAL STATE _______ **************************/
//
//<S_TEST_COMMAND> {
//  " ]]"                         { backToPreviousState(); return RIGHT_DOUBLE_BRACKET; }
//  "&&"                          { return AND_AND; }
//  "||"                          { return OR_OR; }
//  "$" / "("                     { goToState(S_DOLLAR_PREFIXED); return DOLLAR; }
//  "("                           { return LEFT_PAREN; }
//  ")"                           { return RIGHT_PAREN; }
//}
//
//<S_TEST> {
//  "]"                          { if (isEmptyConditionalCommand()) {
//                                      setEmptyConditionalCommand(false);
//                                      backToPreviousState();
//                                      return EXPR_CONDITIONAL_RIGHT;
//                                   } else {
//                                      setEmptyConditionalCommand(false);
//                                      return WORD;
//                                   }
//                                 }
//  " ]"                         { backToPreviousState(); setEmptyConditionalCommand(false); return EXPR_CONDITIONAL_RIGHT; }
//}
//
//<S_TEST, S_TEST_COMMAND> {
//  {WhiteSpaceLineCont}         { return WHITESPACE; }
//
//  /*** Test / conditional expressions ***/
//
//  /* param expansion operators */
//  "=="                         { return COND_OP_EQ_EQ; }
//
//  /* regex operator */
//  "=~"                         { return COND_OP_REGEX; }
//
//  /* misc */
//  "!"                          { return COND_OP_NOT; }
//  "-a"                         |
//  "-o"                         |
//  "-eq"                        |
//  "-ne"                        |
//  "-lt"                        |
//  "-le"                        |
//  "-gt"                        |
//  "-ge"                        |
//
//  /* string operators */
//  "!="                         |
//  ">"                          |
//  "<"                          |
//  "="                          |
//  "-n"                         |
//  "-z"                         |
//
//  /* conditional operators */
//  "-nt"                        |
//  "-ot"                        |
//  "-ef"                        |
//  "-n"                         |
//  "-o"                         |
//  "-qq"                        |
//  "-a"                         |
//  "-b"                         |
//  "-c"                         |
//  "-d"                         |
//  "-e"                         |
//  "-f"                         |
//  "-g"                         |
//  "-h"                         |
//  "-k"                         |
//  "-p"                         |
//  "-r"                         |
//  "-s"                         |
//  "-t"                         |
//  "-u"                         |
//  "-w"                         |
//  "-x"                         |
//  "-O"                         |
//  "-G"                         |
//  "-L"                         |
//  "-S"                         |
//  "-N"                         { return COND_OP; }
//}
//
///*** Arithmetic expressions *************/
//<S_ARITH> {
//    "["                           { return LEFT_SQUARE; }
//    "]"                           { return RIGHT_SQUARE; }
//}
//
//<S_ARITH_SQUARE_MODE> {
//  "["                           { return ARITH_SQUARE_LEFT; }
//  "]"                           { backToPreviousState(); return ARITH_SQUARE_RIGHT; }
//}
//
//<S_ARITH_ARRAY_MODE> {
//  "]"                           { backToPreviousState(); return RIGHT_SQUARE; }
//}
//
//<S_ARITH, S_ARITH_SQUARE_MODE, S_ARITH_ARRAY_MODE> {
//  {HexIntegerLiteral}           { return HEX; }
//  {OctalIntegerLiteral}         { return OCTAL; }
//  {IntegerLiteral}              { return NUMBER; }
//

//
//  "*="                          { return ARITH_ASS_MUL; }
//  "/="                          { return ARITH_ASS_DIV; }
//  "%="                          { return ARITH_ASS_MOD; }
//  "+="                          { return ARITH_ASS_PLUS; }
//  "-="                          { return ARITH_ASS_MINUS; }
//  ">>="                         { return ARITH_ASS_SHIFT_RIGHT; }
//  "<<="                         { return ARITH_ASS_SHIFT_LEFT; }
//  "&="                          { return ARITH_ASS_BIT_AND; }
//  "|="                          { return ARITH_ASS_BIT_OR; }
//  "^="                          { return ARITH_ASS_BIT_XOR; }
//
//  "+"                           { return ARITH_PLUS; }
//  "++"                          { return ARITH_PLUS_PLUS; }
//  "-"                           { return ARITH_MINUS; }
//
//  "--"/"-"
//                                { yypushback(1); return ARITH_MINUS; }
//
//  "--"/{WhiteSpace}+"-"
//                                { yypushback(1); return ARITH_MINUS; }
//
//  "--"/({HexIntegerLiteral}|{OctalIntegerLiteral}|{IntegerLiteral})
//                                { yypushback(1); return ARITH_MINUS; }
//
//  "--"/{WhiteSpace}+({HexIntegerLiteral}|{OctalIntegerLiteral}|{IntegerLiteral})
//                                { yypushback(1); return ARITH_MINUS; }
//
//  "--"                          { return ARITH_MINUS_MINUS; }
//  "=="                          { return ARITH_EQ; }
//
//  "**"                          { return EXPONENT; }
//  "*"                           { return MULT; }
//  "/"                           { return DIV; }
//  "%"                           { return MOD; }
//  "<<"                          { return SHIFT_LEFT; }
//
//  "!"                           { return ARITH_NEGATE; }
//
//  "&"                           { return ARITH_BITWISE_AND; }
//  "~"                           { return ARITH_BITWISE_NEGATE; }
//  "^"                           { return ARITH_BITWISE_XOR; }
//
//  "?"                           { return ARITH_QMARK; }
//  ":"                           { return ARITH_COLON; }
//'
//  "#"                           { return ARITH_BASE_CHAR; }
//
//  {AssignmentWord} / "["
//                                { goToState(S_ARRAY); return ASSIGNMENT_WORD; }
//
//  {ArithWord}                   { return WORD; }
//}
//
//<S_CASE> {
//  "esac"                       { backToPreviousState(); return ESAC; }
//
//  ";&"                         { goToState(S_CASE_PATTERN);
//                                 if (isBash4()) {
//                                    return CASE_END;
//                                 }
//                                 else {
//                                    yypushback(1);
//                                    return SEMI;
//                                 }
//                               }
//
//  ";;&"                        { goToState(S_CASE_PATTERN);
//                                 if (!isBash4()) {
//                                    yypushback(1);
//                                 }
//                                 return CASE_END;
//                               }
//
//  ";;"                         { goToState(S_CASE_PATTERN); return CASE_END; }
//  "in"                         { if (!isInCaseBody()) {
//                                   setInCaseBody(true);
//                                   goToState(S_CASE_PATTERN);
//                                 }
//                                 return WORD;
//                               }
//}
//
//<S_CASE_PATTERN> {
//  "esac"                        { backToPreviousState(); yypushback(yylength()); }
//}
//
////////////////////// END OF STATE TEST_EXPR /////////////////////
//
///* string literals */
//<X_STRINGMODE> {
//  \"                            { if (!stringParsingState().isInSubstring() && stringParsingState().isSubstringAllowed()) {
//                                    stringParsingState().enterString();
//                                    goToState(X_STRINGMODE);
//                                    return QUOTE;
//                                  }
//
//                                  stringParsingState().leaveString();
//                                  backToPreviousState();
//                                  return QUOTE;
//                                }
//
//  /* Backquote expression inside of evaluated strings */
//  `                           { if (yystate() == S_BACKQUOTE) {
//                                    backToPreviousState();
//                                }
//                                else {
//                                    goToState(S_BACKQUOTE);
//                                }
//                                return BACKQUOTE; }
//
//  {EscapedChar}               { return STRING_DATA; }
//  [^\"]                       { return STRING_DATA; }
//}
//
//<YYINITIAL, S_BACKQUOTE, S_SUBSHELL, S_CASE> {
//  /* Bash 4 */
//    "&>>"                         { if (isBash4()) {
//                                        return REDIRECT_AMP_GREATER_GREATER;
//                                    } else {
//                                        yypushback(2);
//                                        return AMP;
//                                    }
//                                  }
//
//    "&>"                          { if (isBash4()) {
//                                        return REDIRECT_AMP_GREATER;
//                                    } else {
//                                        yypushback(1);
//                                        return AMP;
//                                    }
//                                  }
//
//  /* Bash v3 */
//  "<<<"                         { goToState(X_HERE_STRING); return REDIRECT_HERE_STRING; }
//  "<>"                          { return REDIRECT_LESS_GREATER; }
//
//  "<&" / {ArithWord}            { return REDIRECT_LESS_AMP; }
//  ">&" / {ArithWord}            { return REDIRECT_GREATER_AMP; }
//  "<&" / {WhiteSpaceLineCont}   { return REDIRECT_LESS_AMP; }
//  ">&" / {WhiteSpaceLineCont}   { return REDIRECT_GREATER_AMP; }
//
//  ">|"                          { return REDIRECT_GREATER_BAR; }
//
//}
//
//<S_PARAM_EXPANSION> {
//  "!"                           { return PARAM_EXPANSION_OP_EXCL; }
//  ":="                          { return PARAM_EXPANSION_OP_COLON_EQ; }
//  "="                           { return PARAM_EXPANSION_OP_EQ; }
//
//  ":-"                          { return PARAM_EXPANSION_OP_COLON_MINUS; }
//  "-"                           { return PARAM_EXPANSION_OP_MINUS; }
//
//  ":+"                          { return PARAM_EXPANSION_OP_COLON_PLUS; }
//  "+"                           { return PARAM_EXPANSION_OP_PLUS; }
//
//  ":?"                          { return PARAM_EXPANSION_OP_COLON_QMARK; }
//
//  ":"                           { return PARAM_EXPANSION_OP_COLON; }
//
//  "//"                          { goToState(S_PARAM_EXPANSION_PATTERN); return PARAM_EXPANSION_OP_SLASH_SLASH; }
//  "/"                           { goToState(S_PARAM_EXPANSION_PATTERN); return PARAM_EXPANSION_OP_SLASH;  }
//
//  "##"                          { setParamExpansionHash(isParamExpansionWord()); return PARAM_EXPANSION_OP_HASH_HASH; }
//  "#"                           { setParamExpansionHash(isParamExpansionWord()); return PARAM_EXPANSION_OP_HASH; }
//  "@"                           { return PARAM_EXPANSION_OP_AT; }
//  "*"                           { return PARAM_EXPANSION_OP_STAR; }
//  "%"                           { setParamExpansionOther(true); return PARAM_EXPANSION_OP_PERCENT; }
//  "?"                           { setParamExpansionOther(true); return PARAM_EXPANSION_OP_QMARK; }
//  "."                           { setParamExpansionOther(true); return PARAM_EXPANSION_OP_DOT; }
//  "^"                           { setParamExpansionOther(true); return PARAM_EXPANSION_OP_UPPERCASE_FIRST; }
//  "^^"                          { setParamExpansionOther(true); return PARAM_EXPANSION_OP_UPPERCASE_ALL; }
//  ","                           { setParamExpansionOther(true); return PARAM_EXPANSION_OP_LOWERCASE_FIRST; }
//  ",,"                          { setParamExpansionOther(true); return PARAM_EXPANSION_OP_LOWERCASE_ALL; }
//
//  "[" / [@*]                    { return LEFT_SQUARE; }
//  "["                           { if (!isParamExpansionOther() && (!isParamExpansionWord() || !isParamExpansionHash())) {
//                                    // If we expect an array reference parse the next tokens as arithmetic expression
//                                    goToState(S_ARITH_ARRAY_MODE);
//                                  }
//
//                                  return LEFT_SQUARE;
//                                }
//
//  "]"                           { return RIGHT_SQUARE; }
//
//  "{"                           { setParamExpansionWord(false); setParamExpansionHash(false); setParamExpansionOther(false);
//                                  return LEFT_CURLY;
//                                }
//  "}"                           { setParamExpansionWord(false); setParamExpansionHash(false); setParamExpansionOther(false);
//                                  backToPreviousState();
//                                  return RIGHT_CURLY;
//                                }
//
//  {EscapedChar}                 { setParamExpansionWord(true); return WORD; }
//  {IntegerLiteral}              { setParamExpansionWord(true); return WORD; }
//  {ParamExpansionWord}          { setParamExpansionWord(true); return WORD; }
//}
//
//<S_PARAM_EXPANSION_PATTERN> {
//  // pattern followed by the delimiter
//  ({EscapedChar} | {LineTerminator} | [^/}])+ / "/" { backToPreviousState(); goToState(S_PARAM_EXPANSION_DELIMITER); return PARAM_EXPANSION_PATTERN; }
//
//  //no delimiter and no replacement
//  ({EscapedChar} | {LineTerminator} | [^/}])+     { backToPreviousState(); return PARAM_EXPANSION_PATTERN; }
//
//  //empty pattern
//  .                           { yypushback(1); backToPreviousState(); }
//}
//
//// matches just the delimiter and then changes into the replacement state
//<S_PARAM_EXPANSION_DELIMITER> {
//    //with replacement
//    "/"                         { backToPreviousState(); goToState(S_PARAM_EXPANSION_REPLACEMENT); return PARAM_EXPANSION_OP_SLASH; }
//
//    //no replacement
//    "}"                         { yypushback(1); backToPreviousState(); }
//}
//
//<S_PARAM_EXPANSION_REPLACEMENT> {
//    [^}]+                       { backToPreviousState(); return WORD; }
//
//    //probably an empty replacement
//    .                           { yypushback(1); backToPreviousState(); }
//}
//
///** Match in all except of string */
//<YYINITIAL, S_ARITH, S_ARITH_SQUARE_MODE, S_ARITH_ARRAY_MODE, S_CASE, S_CASE_PATTERN, S_SUBSHELL, S_ASSIGNMENT_LIST, S_PARAM_EXPANSION, S_BACKQUOTE, X_STRINGMODE> {
//    /*
//     Do NOT match for Whitespace+ , we have some whitespace sensitive tokens like " ]]" which won't match
//     if we match repeated whtiespace!
//    */
//    {WhiteSpace}                 { return WHITESPACE; }
//    {LineContinuation}+          { return LINE_CONTINUATION; }
//}
//
//<YYINITIAL, S_TEST, S_TEST_COMMAND, S_ARITH, S_ARITH_SQUARE_MODE, S_ARITH_ARRAY_MODE, S_CASE, S_CASE_PATTERN, S_SUBSHELL, S_ASSIGNMENT_LIST, S_PARAM_EXPANSION, S_BACKQUOTE> {
//    <X_HERE_STRING> {
//        {StringStart}                 { stringParsingState().enterString(); if (yystate() == X_HERE_STRING && !isInHereStringContent()) enterHereStringContent();
//goToState(X_STRINGMODE); return QUOTE; }
//
//        "$"\'{SingleCharacter}*\'     |
//        \'{UnescapedCharacter}*\'        { if (yystate() == X_HERE_STRING && !isInHereStringContent()) enterHereStringContent(); return RAW_STRING; }
//
//    /* Single line feeds are required to properly parse heredocs */
//        {LineTerminator}             {
//                                                    if (yystate() == X_HERE_STRING) {
//                                                        return LINEFEED;
//                                                    } else if ((yystate() == S_PARAM_EXPANSION || yystate() == S_SUBSHELL || yystate() == S_ARITH || yystate() == S_ARITH_SQUARE_MODE) && isInState(X_HEREDOC)) {
//                                                        backToPreviousState();
//                                                        return LINEFEED;
//                                                    }
//
//                                                    if (!heredocState().isEmpty()) {
//                                                        // first linebreak after the start marker
//                                                        goToState(X_HEREDOC);
//                                                        return LINEFEED;
//                                                    }
//
//                                                   return LINEFEED;
//                                             }
//
//        /* Backquote expression */
//        `                             { if (yystate() == S_BACKQUOTE) backToPreviousState(); else goToState(S_BACKQUOTE); return BACKQUOTE; }
//    }
//
//
//  /* Bash reserved keywords */
//    "{"                           { return LEFT_CURLY; }
//
//    "|&"                          { if (isBash4()) {
//                                        return PIPE_AMP;
//                                     } else {
//                                        yypushback(1);
//                                        return PIPE;
//                                     }
//                                  }
//    "|"                           { return PIPE; }
//
//  /** Misc expressions */
//    "@"                           { return AT; }
//    "$"                           { return DOLLAR; }
//    <X_HERE_STRING> {
//        "&"                           { return AMP; }
//        ";"                           { return SEMI; }
//    }
//    "<<-" {
//        goToState(X_HEREDOC_MARKER_IGNORE_TABS);
//        return HEREDOC_MARKER_TAG;
//    }
//    "<<" {
//        goToState(X_HEREDOC_MARKER);
//        return HEREDOC_MARKER_TAG;
//    }
//    ">"                           { return GT; }
//    "<"                           { return LT; }
//    ">>"                          { return SHIFT_RIGHT; }
//
//    <X_STRINGMODE> {
//        {Variable}                { return VAR; }
//    }
//
//    "$["                          { yypushback(1); goToState(S_ARITH_SQUARE_MODE); return DOLLAR; }
//
//    "\\"                          { return BACKSLASH; }
//}
//
//<YYINITIAL, X_HEREDOC, S_PARAM_EXPANSION, S_TEST, S_TEST_COMMAND, S_CASE, S_CASE_PATTERN, S_SUBSHELL, S_ARITH, S_ARITH_SQUARE_MODE, S_ARITH_ARRAY_MODE, S_ARRAY, S_ASSIGNMENT_LIST, S_BACKQUOTE, X_STRINGMODE, X_HERE_STRING> {
//    "${"                        { if (yystate() == X_HEREDOC && !heredocState().isExpectingEvaluatingHeredoc()) return HEREDOC_LINE; goToState(S_PARAM_EXPANSION); yypushback(1); return DOLLAR; }
//    "}"                         { if (yystate() == X_HEREDOC && !heredocState().isExpectingEvaluatingHeredoc()) return HEREDOC_LINE; return RIGHT_CURLY; }
//}
//
//<S_CASE_PATTERN> {
//  {CasePattern}                 { return WORD; }
//}
//
//<YYINITIAL, S_CASE, S_SUBSHELL, S_BACKQUOTE, S_ARRAY> {
//    {IntegerLiteral}            { return INT; }
//}
//
//<YYINITIAL, S_CASE, S_TEST, S_TEST_COMMAND, S_SUBSHELL, S_BACKQUOTE> {
//  {Word}                       { return WORD; }
//  {WordAfter}+                 { return WORD; }
//}
//
///** END */
//
////all x-states
//<X_HERE_STRING, X_HEREDOC, X_HEREDOC_MARKER, X_HEREDOC_MARKER_IGNORE_TABS, X_STRINGMODE>{
//    [^]                        { return BAD_CHARACTER; }
//}



