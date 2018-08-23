/* It's an automatically generated code. Do not modify it. */
package com.jetbrains.python.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.intellij.util.containers.Stack;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.psi.PyStringLiteralUtil;

%%

%class _PythonLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
// TODO remove
%debug

DIGIT = [0-9]
NONZERODIGIT = [1-9]
OCTDIGIT = [0-7]
HEXDIGIT = [0-9A-Fa-f]
BINDIGIT = [01]

HEXINTEGER = 0[Xx]("_"?{HEXDIGIT})+
OCTINTEGER = 0[Oo]?("_"?{OCTDIGIT})+
BININTEGER = 0[Bb]("_"?{BINDIGIT})+
DECIMALINTEGER = (({NONZERODIGIT}("_"?{DIGIT})*)|0)
INTEGER = {DECIMALINTEGER}|{OCTINTEGER}|{HEXINTEGER}|{BININTEGER}
LONGINTEGER = {INTEGER}[Ll]

END_OF_LINE_COMMENT="#"[^\r\n]*

IDENT_START = [\w_--\d]
IDENT_CONTINUE = [\w_]
IDENTIFIER = {IDENT_START}{IDENT_CONTINUE}*

FLOATNUMBER=({POINTFLOAT})|({EXPONENTFLOAT})
POINTFLOAT=(({INTPART})?{FRACTION})|({INTPART}\.)
EXPONENTFLOAT=(({INTPART})|({POINTFLOAT})){EXPONENT}
INTPART = {DIGIT}("_"?{DIGIT})*
FRACTION = \.{INTPART}
EXPONENT = [eE][+\-]?{INTPART}

IMAGNUMBER=(({FLOATNUMBER})|({INTPART}))[Jj]

//STRING_LITERAL=[UuBb]?({RAW_STRING}|{QUOTED_STRING})
//RAW_STRING=[Rr]{QUOTED_STRING}
//QUOTED_STRING=({TRIPLE_APOS_LITERAL})|({QUOTED_LITERAL})|({DOUBLE_QUOTED_LITERAL})|({TRIPLE_QUOTED_LITERAL})

// If you change patterns for string literals, don't forget to update PyStringLiteralUtil!
// "c" prefix character is included for Cython
SINGLE_QUOTED_STRING=[UuBbCcRr]{0,3}({QUOTED_LITERAL} | {DOUBLE_QUOTED_LITERAL})
TRIPLE_QUOTED_STRING=[UuBbCcRr]{0,3}({TRIPLE_QUOTED_LITERAL}|{TRIPLE_APOS_LITERAL})

DOCSTRING_LITERAL=({SINGLE_QUOTED_STRING}|{TRIPLE_QUOTED_STRING})

QUOTED_LITERAL="'" ([^\\\'\r\n] | {ESCAPE_SEQUENCE} | (\\[\r\n]))* ("'"|\\)?
DOUBLE_QUOTED_LITERAL=\"([^\\\"\r\n]|{ESCAPE_SEQUENCE}|(\\[\r\n]))*?(\"|\\)?
ESCAPE_SEQUENCE=\\[^\r\n]

ANY_ESCAPE_SEQUENCE = \\[^]

THREE_QUO = (\"\"\")
ONE_TWO_QUO = (\"[^\\\"]) | (\"\\[^]) | (\"\"[^\\\"]) | (\"\"\\[^])
QUO_STRING_CHAR = [^\\\"] | {ANY_ESCAPE_SEQUENCE} | {ONE_TWO_QUO}
TRIPLE_QUOTED_LITERAL = {THREE_QUO} {QUO_STRING_CHAR}* {THREE_QUO}?

THREE_APOS = (\'\'\')
ONE_TWO_APOS = ('[^\\']) | ('\\[^]) | (''[^\\']) | (''\\[^])
APOS_STRING_CHAR = [^\\'] | {ANY_ESCAPE_SEQUENCE} | {ONE_TWO_APOS}
TRIPLE_APOS_LITERAL = {THREE_APOS} {APOS_STRING_CHAR}* {THREE_APOS}?

FSTRING_PREFIX = [UuBbCcRr]{0,3}[fF][UuBbCcRr]{0,3}
FSTRING_START = {FSTRING_PREFIX} (\"\"\"|'''|\"|')
FSTRING_END = (\"\"\"|'''|\"|')
FSTRING_ESCAPED_LBRACE = "{{"
// TODO report it in annotation
//FSTRING_ESCAPED_RBRACE = "}}"
FSTRING_TEXT = ([^\\\'\r\n{] | {ESCAPE_SEQUENCE} | {FSTRING_ESCAPED_LBRACE} | (\\[\r\n]))+
FSTRING_FORMAT_TEXT = ([^\\\'\r\n{}] | (\\[\r\n]))+

%state PENDING_DOCSTRING
%state IN_DOCSTRING_OWNER
%state FSTRING
%state FSTRING_FRAGMENT
%state FSTRING_FRAGMENT_FORMAT
%{
static final class FragmentState {
  private final int oldState;
  private final int oldBraceBalance;

  FragmentState(int state, int braceBalance) {
    oldState = state;
    oldBraceBalance = braceBalance;
  }
}

void pushFStringFragment() {
  fragmentStates.push(new FragmentState(yystate(), braceBalance));
  braceBalance = 0;
  yybegin(FSTRING_FRAGMENT);
}

void popFStringFragment() {
  FragmentState state = fragmentStates.pop();
  braceBalance = state.oldBraceBalance;
  yybegin(state.oldState);
}

static final class FStringState {
  private final int oldState;
  private final String quotes;

  FStringState(int state, String quotes) {
    oldState = state;
    this.quotes = quotes;
  }
}

void pushFString() {
  final String prefixAndQuotes = yytext().toString();
  final int prefixLength = PyStringLiteralUtil.getPrefixLength(prefixAndQuotes);
  fStringStates.push(new FStringState(yystate(), prefixAndQuotes.substring(prefixLength)));
  yybegin(FSTRING);
}

void popFString() {
  final FStringState state = fStringStates.pop();
  yybegin(state.oldState);
}

private final Stack<FragmentState> fragmentStates = new Stack<>();
private final Stack<FStringState> fStringStates = new Stack<>();

private int braceBalance = 0;

private int getSpaceLength(CharSequence string) {
String string1 = string.toString();
string1 = StringUtil.trimEnd(string1, "\\");
string1 = StringUtil.trimEnd(string1, ";");
final String s = StringUtil.trimTrailing(string1);
return yylength()-s.length();

}
%}

%%

[\ ]                        { return PyTokenTypes.SPACE; }
[\t]                        { return PyTokenTypes.TAB; }
[\f]                        { return PyTokenTypes.FORMFEED; }
"\\"                        { return PyTokenTypes.BACKSLASH; }

<FSTRING> {
  {FSTRING_TEXT} { return PyTokenTypes.FSTRING_TEXT; }
  {FSTRING_END} { popFString(); return PyTokenTypes.FSTRING_END; }
  "{" { pushFStringFragment(); return PyTokenTypes.FSTRING_FRAGMENT_START; }
}

<FSTRING_FRAGMENT> {
  "(" { braceBalance++; return PyTokenTypes.LPAR; }
  ")" { braceBalance--; return PyTokenTypes.RPAR; }
  
  "[" { braceBalance++; return PyTokenTypes.LBRACKET; }
  "]" { braceBalance--; return PyTokenTypes.RBRACKET; }
  
  "{" { braceBalance++; return PyTokenTypes.LBRACE; }
  "}" { if (braceBalance == 0) { popFStringFragment(); return PyTokenTypes.FSTRING_FRAGMENT_END; }
        else { braceBalance--; return PyTokenTypes.RBRACE; } }
        
  ":" { if (braceBalance == 0) { yybegin(FSTRING_FRAGMENT_FORMAT); return PyTokenTypes.FSTRING_FRAGMENT_FORMAT_START; }
        else { return PyTokenTypes.COLON; } }
}

<FSTRING_FRAGMENT_FORMAT> {
  {FSTRING_FORMAT_TEXT} { return PyTokenTypes.FSTRING_TEXT; }
  "{" { pushFStringFragment(); return PyTokenTypes.FSTRING_FRAGMENT_START; }
  "}" { if (braceBalance == 0) { popFStringFragment(); return PyTokenTypes.FSTRING_FRAGMENT_END; }
        else { braceBalance--; return PyTokenTypes.RBRACE; } }
}

<YYINITIAL> {
[\n]                        { if (zzCurrentPos == 0) yybegin(PENDING_DOCSTRING); return PyTokenTypes.LINE_BREAK; }
{END_OF_LINE_COMMENT}       { if (zzCurrentPos == 0) yybegin(PENDING_DOCSTRING); return PyTokenTypes.END_OF_LINE_COMMENT; }

{SINGLE_QUOTED_STRING}          { if (zzInput == YYEOF && zzStartRead == 0) return PyTokenTypes.DOCSTRING;
                                 else return PyTokenTypes.SINGLE_QUOTED_STRING; }
{TRIPLE_QUOTED_STRING}          { if (zzInput == YYEOF && zzStartRead == 0) return PyTokenTypes.DOCSTRING;
                                 else return PyTokenTypes.TRIPLE_QUOTED_STRING; }

{SINGLE_QUOTED_STRING}[\ \t]*[\n;]   { yypushback(getSpaceLength(yytext())); if (zzCurrentPos != 0) return PyTokenTypes.SINGLE_QUOTED_STRING;
return PyTokenTypes.DOCSTRING; }

{TRIPLE_QUOTED_STRING}[\ \t]*[\n;]   { yypushback(getSpaceLength(yytext())); if (zzCurrentPos != 0) return PyTokenTypes.TRIPLE_QUOTED_STRING;
return PyTokenTypes.DOCSTRING; }

{SINGLE_QUOTED_STRING}[\ \t]*"\\"  {
 yypushback(getSpaceLength(yytext())); if (zzCurrentPos != 0) return PyTokenTypes.SINGLE_QUOTED_STRING;
 yybegin(PENDING_DOCSTRING); return PyTokenTypes.DOCSTRING; }

{TRIPLE_QUOTED_STRING}[\ \t]*"\\"  {
 yypushback(getSpaceLength(yytext())); if (zzCurrentPos != 0) return PyTokenTypes.TRIPLE_QUOTED_STRING;
 yybegin(PENDING_DOCSTRING); return PyTokenTypes.DOCSTRING; }

}

[\n]                        { return PyTokenTypes.LINE_BREAK; }
{END_OF_LINE_COMMENT}       { return PyTokenTypes.END_OF_LINE_COMMENT; }

<YYINITIAL, IN_DOCSTRING_OWNER, FSTRING_FRAGMENT> {
{LONGINTEGER}         { return PyTokenTypes.INTEGER_LITERAL; }
{INTEGER}             { return PyTokenTypes.INTEGER_LITERAL; }
{FLOATNUMBER}         { return PyTokenTypes.FLOAT_LITERAL; }
{IMAGNUMBER}          { return PyTokenTypes.IMAGINARY_LITERAL; }

{SINGLE_QUOTED_STRING} { return PyTokenTypes.SINGLE_QUOTED_STRING; }
{TRIPLE_QUOTED_STRING} { return PyTokenTypes.TRIPLE_QUOTED_STRING; }

"and"                 { return PyTokenTypes.AND_KEYWORD; }
"assert"              { return PyTokenTypes.ASSERT_KEYWORD; }
"break"               { return PyTokenTypes.BREAK_KEYWORD; }
"class"               { yybegin(IN_DOCSTRING_OWNER); return PyTokenTypes.CLASS_KEYWORD; }
"continue"            { return PyTokenTypes.CONTINUE_KEYWORD; }
"def"                 { yybegin(IN_DOCSTRING_OWNER); return PyTokenTypes.DEF_KEYWORD; }
"del"                 { return PyTokenTypes.DEL_KEYWORD; }
"elif"                { return PyTokenTypes.ELIF_KEYWORD; }
"else"                { return PyTokenTypes.ELSE_KEYWORD; }
"except"              { return PyTokenTypes.EXCEPT_KEYWORD; }
"finally"             { return PyTokenTypes.FINALLY_KEYWORD; }
"for"                 { return PyTokenTypes.FOR_KEYWORD; }
"from"                { return PyTokenTypes.FROM_KEYWORD; }
"global"              { return PyTokenTypes.GLOBAL_KEYWORD; }
"if"                  { return PyTokenTypes.IF_KEYWORD; }
"import"              { return PyTokenTypes.IMPORT_KEYWORD; }
"in"                  { return PyTokenTypes.IN_KEYWORD; }
"is"                  { return PyTokenTypes.IS_KEYWORD; }
"lambda"              { return PyTokenTypes.LAMBDA_KEYWORD; }
"not"                 { return PyTokenTypes.NOT_KEYWORD; }
"or"                  { return PyTokenTypes.OR_KEYWORD; }
"pass"                { return PyTokenTypes.PASS_KEYWORD; }
"raise"               { return PyTokenTypes.RAISE_KEYWORD; }
"return"              { return PyTokenTypes.RETURN_KEYWORD; }
"try"                 { return PyTokenTypes.TRY_KEYWORD; }
"while"               { return PyTokenTypes.WHILE_KEYWORD; }
"yield"               { return PyTokenTypes.YIELD_KEYWORD; }

{IDENTIFIER}          { return PyTokenTypes.IDENTIFIER; }

"+="                  { return PyTokenTypes.PLUSEQ; }
"-="                  { return PyTokenTypes.MINUSEQ; }
"**="                 { return PyTokenTypes.EXPEQ; }
"*="                  { return PyTokenTypes.MULTEQ; }
"@="                  { return PyTokenTypes.ATEQ; }
"//="                 { return PyTokenTypes.FLOORDIVEQ; }
"/="                  { return PyTokenTypes.DIVEQ; }
"%="                  { return PyTokenTypes.PERCEQ; }
"&="                  { return PyTokenTypes.ANDEQ; }
"|="                  { return PyTokenTypes.OREQ; }
"^="                  { return PyTokenTypes.XOREQ; }
">>="                 { return PyTokenTypes.GTGTEQ; }
"<<="                 { return PyTokenTypes.LTLTEQ; }
"<<"                  { return PyTokenTypes.LTLT; }
">>"                  { return PyTokenTypes.GTGT; }
"**"                  { return PyTokenTypes.EXP; }
"//"                  { return PyTokenTypes.FLOORDIV; }
"<="                  { return PyTokenTypes.LE; }
">="                  { return PyTokenTypes.GE; }
"=="                  { return PyTokenTypes.EQEQ; }
"!="                  { return PyTokenTypes.NE; }
"<>"                  { return PyTokenTypes.NE_OLD; }
"->"                  { return PyTokenTypes.RARROW; }
"+"                   { return PyTokenTypes.PLUS; }
"-"                   { return PyTokenTypes.MINUS; }
"*"                   { return PyTokenTypes.MULT; }
"/"                   { return PyTokenTypes.DIV; }
"%"                   { return PyTokenTypes.PERC; }
"&"                   { return PyTokenTypes.AND; }
"|"                   { return PyTokenTypes.OR; }
"^"                   { return PyTokenTypes.XOR; }
"~"                   { return PyTokenTypes.TILDE; }
"<"                   { return PyTokenTypes.LT; }
">"                   { return PyTokenTypes.GT; }
"("                   { return PyTokenTypes.LPAR; }
")"                   { return PyTokenTypes.RPAR; }
"["                   { return PyTokenTypes.LBRACKET; }
"]"                   { return PyTokenTypes.RBRACKET; }
"{"                   { return PyTokenTypes.LBRACE; }
"}"                   { return PyTokenTypes.RBRACE; }
"@"                   { return PyTokenTypes.AT; }
","                   { return PyTokenTypes.COMMA; }
":"                   { return PyTokenTypes.COLON; }

"."                   { return PyTokenTypes.DOT; }
"`"                   { return PyTokenTypes.TICK; }
"="                   { return PyTokenTypes.EQ; }
";"                   { return PyTokenTypes.SEMICOLON; }

{FSTRING_START}       { pushFString(); return PyTokenTypes.FSTRING_START; }

[^]                   { return PyTokenTypes.BAD_CHARACTER; }
}

<IN_DOCSTRING_OWNER> {
":"(\ )*{END_OF_LINE_COMMENT}?"\n"          { yypushback(yylength()-1); yybegin(PENDING_DOCSTRING); return PyTokenTypes.COLON; }
}

<PENDING_DOCSTRING> {
{SINGLE_QUOTED_STRING}          { if (zzInput == YYEOF) return PyTokenTypes.DOCSTRING;
                                 else yybegin(YYINITIAL); return PyTokenTypes.SINGLE_QUOTED_STRING; }
{TRIPLE_QUOTED_STRING}          { if (zzInput == YYEOF) return PyTokenTypes.DOCSTRING;
                                 else yybegin(YYINITIAL); return PyTokenTypes.TRIPLE_QUOTED_STRING; }
{DOCSTRING_LITERAL}[\ \t]*[\n;] { yypushback(getSpaceLength(yytext())); yybegin(YYINITIAL); return PyTokenTypes.DOCSTRING; }
{DOCSTRING_LITERAL}[\ \t]*"\\"  { yypushback(getSpaceLength(yytext())); return PyTokenTypes.DOCSTRING; }
.                               { yypushback(1); yybegin(YYINITIAL); }
}
