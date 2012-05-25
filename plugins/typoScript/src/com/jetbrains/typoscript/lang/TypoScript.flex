package com.jetbrains.typoscript.lang;
import com.intellij.lexer.FlexLexer;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import java.io.Reader;

%%

%{

  public _TypoScriptLexer() {
    this((Reader)null);
  }
%}

%public
%class _TypoScriptLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%eof{  return;
%eof}

NOT_WHITE_SPACE=[^\ \n\r\t\f]
WHITE_SPACE_CHARS=[\ \n\r\t\f]*
WHITESPACE_WITH_NEW_LINE = [\ \t\f]*(\n|\r|\r\n){WHITE_SPACE_CHARS}
STRING_TAIL = [^\r\n]*
ONE_LINE_COMMENT=("/"|"#"){STRING_TAIL}
C_STYLE_COMMENT="/*" ~((\n|\r|\r\n){WHITE_SPACE_CHARS}"*/")
ENDTRIMMED_STRING_TAIL = ({STRING_TAIL}{NOT_WHITE_SPACE})?
TRIMMED_STRING_TAIL = {NOT_WHITE_SPACE}{ENDTRIMMED_STRING_TAIL}

OBJECT_PATH_ENTITY = [A-Za-z0-9\-_]*

%state EXPRESSION_SIGN
%state ASSIGNMENT_VALUE
%state ONE_LINE_IGNORED_ZONE

%xstate COPYING_OPERATOR_VALUE

%xstate MULTILINE_AFTER_SIGN_ONE_LINE_IGNORED_ZONE
%xstate MULTILINE_OPERATOR_VALUE
%xstate MULTILINE_NEW_LINE

%xstate MODIFICATION_OPERATOR_VALUE
%xstate MODIFICATION_OPERATOR_FUNCTION_ARGUMENT
%%

{WHITESPACE_WITH_NEW_LINE}                            { yybegin(YYINITIAL); return TypoScriptTokenTypes.WHITE_SPACE_WITH_NEW_LINE; }
{WHITE_SPACE_CHARS}                                   { return TokenType.WHITE_SPACE; }

<YYINITIAL> {ONE_LINE_COMMENT}                        { return TypoScriptTokenTypes.ONE_LINE_COMMENT; }
<YYINITIAL> {C_STYLE_COMMENT}                         { yybegin(ONE_LINE_IGNORED_ZONE); return TypoScriptTokenTypes.C_STYLE_COMMENT; }
<YYINITIAL> {OBJECT_PATH_ENTITY}                      { yybegin(EXPRESSION_SIGN); return TypoScriptTokenTypes.OBJECT_PATH_ENTITY; }
<YYINITIAL> "."                                       { return TypoScriptTokenTypes.OBJECT_PATH_SEPARATOR; }
<YYINITIAL> "["{ENDTRIMMED_STRING_TAIL}               { return TypoScriptTokenTypes.CONDITION; }


<EXPRESSION_SIGN> {OBJECT_PATH_ENTITY}                { return TypoScriptTokenTypes.OBJECT_PATH_ENTITY; }
<EXPRESSION_SIGN> "."                                 { return TypoScriptTokenTypes.OBJECT_PATH_SEPARATOR; }

<COPYING_OPERATOR_VALUE> {OBJECT_PATH_ENTITY}         { return TypoScriptTokenTypes.OBJECT_PATH_ENTITY; }
<COPYING_OPERATOR_VALUE> "."                          { return TypoScriptTokenTypes.OBJECT_PATH_SEPARATOR; }
<COPYING_OPERATOR_VALUE> {WHITESPACE_WITH_NEW_LINE}   { yybegin(YYINITIAL); return TypoScriptTokenTypes.WHITE_SPACE_WITH_NEW_LINE; }
<COPYING_OPERATOR_VALUE> {WHITE_SPACE_CHARS}          { return TokenType.WHITE_SPACE; }

<EXPRESSION_SIGN>   "="                               { yybegin(ASSIGNMENT_VALUE); return TypoScriptTokenTypes.ASSIGNMENT_OPERATOR; }
<EXPRESSION_SIGN>   ":="                              { yybegin(MODIFICATION_OPERATOR_VALUE); return TypoScriptTokenTypes.MODIFICATION_OPERATOR; }
<EXPRESSION_SIGN>   ">"                               { yybegin(ONE_LINE_IGNORED_ZONE); return TypoScriptTokenTypes.UNSETTING_OPERATOR; }
<EXPRESSION_SIGN>   "<"                               { yybegin(COPYING_OPERATOR_VALUE); return TypoScriptTokenTypes.COPYING_OPERATOR; }
<YYINITIAL>         "<"{ENDTRIMMED_STRING_TAIL}       { return TypoScriptTokenTypes.INCLUDE_STATEMENT; }
            //todo create "=<" reference sign for typoscript templates; or handle < in assignment value
<EXPRESSION_SIGN>   "("                               { yybegin(MULTILINE_AFTER_SIGN_ONE_LINE_IGNORED_ZONE);
                                                      return TypoScriptTokenTypes.MULTILINE_VALUE_OPERATOR_BEGIN; }
<EXPRESSION_SIGN>   "{"                               { yybegin(ONE_LINE_IGNORED_ZONE); return TypoScriptTokenTypes.CODE_BLOCK_OPERATOR_BEGIN; }
<YYINITIAL>         "{"{ENDTRIMMED_STRING_TAIL}       { return TypoScriptTokenTypes.IGNORED_TEXT; }
<YYINITIAL>         "}"                               { yybegin(ONE_LINE_IGNORED_ZONE); return TypoScriptTokenTypes.CODE_BLOCK_OPERATOR_END; }


<ASSIGNMENT_VALUE>       {TRIMMED_STRING_TAIL}        { yybegin(YYINITIAL); return TypoScriptTokenTypes.ASSIGNMENT_VALUE; }
<ONE_LINE_IGNORED_ZONE>  {STRING_TAIL}                { yybegin(YYINITIAL); return TypoScriptTokenTypes.IGNORED_TEXT; }


<MULTILINE_AFTER_SIGN_ONE_LINE_IGNORED_ZONE>  {STRING_TAIL}                                       { yybegin(MULTILINE_OPERATOR_VALUE);
                                                                                                    return TypoScriptTokenTypes.IGNORED_TEXT; }
<MULTILINE_AFTER_SIGN_ONE_LINE_IGNORED_ZONE>  {WHITESPACE_WITH_NEW_LINE}                          { yybegin(MULTILINE_OPERATOR_VALUE);
                                                                                                    return TypoScriptTokenTypes.WHITE_SPACE_WITH_NEW_LINE; }
<MULTILINE_OPERATOR_VALUE>                    {WHITESPACE_WITH_NEW_LINE}                          { return TokenType.WHITE_SPACE; }
<MULTILINE_OPERATOR_VALUE>                    [^\ \n\r\t\f\)]{ENDTRIMMED_STRING_TAIL}   { return TypoScriptTokenTypes.MULTILINE_VALUE; }
<MULTILINE_OPERATOR_VALUE>                    ")"                                                 { yybegin(ONE_LINE_IGNORED_ZONE);
                                                                                                    return TypoScriptTokenTypes.MULTILINE_VALUE_OPERATOR_END; }

<MODIFICATION_OPERATOR_VALUE>               ([^" "\r\n"("][^\r\n"("]*[^" "\r\n"("])|([^" "\r\n"("])   { return TypoScriptTokenTypes.MODIFICATION_OPERATOR_FUNCTION; }
<MODIFICATION_OPERATOR_VALUE>               "("                                                       { yybegin(MODIFICATION_OPERATOR_FUNCTION_ARGUMENT);
                                                                                                        return TypoScriptTokenTypes.MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN;}
<MODIFICATION_OPERATOR_FUNCTION_ARGUMENT>   ([^" "\r\n")"][^\r\n")"]*[^" "\r\n")"])|([^" "\r\n")"])   { return TypoScriptTokenTypes.MODIFICATION_OPERATOR_FUNCTION_ARGUMENT;}
<MODIFICATION_OPERATOR_FUNCTION_ARGUMENT>   ")"                                                       { yybegin(ONE_LINE_IGNORED_ZONE);
                                                                                                        return TypoScriptTokenTypes.MODIFICATION_OPERATOR_FUNCTION_PARAM_END; }
<MODIFICATION_OPERATOR_VALUE, MODIFICATION_OPERATOR_FUNCTION_ARGUMENT>   {WHITESPACE_WITH_NEW_LINE}   { yybegin(YYINITIAL); return TypoScriptTokenTypes.WHITE_SPACE_WITH_NEW_LINE; }
<MODIFICATION_OPERATOR_VALUE, MODIFICATION_OPERATOR_FUNCTION_ARGUMENT>   {WHITE_SPACE_CHARS}          { return TokenType.WHITE_SPACE; }


<YYINITIAL, EXPRESSION_SIGN, ASSIGNMENT_VALUE, ONE_LINE_IGNORED_ZONE, COPYING_OPERATOR_VALUE, MULTILINE_AFTER_SIGN_ONE_LINE_IGNORED_ZONE,
 MULTILINE_OPERATOR_VALUE, MULTILINE_NEW_LINE, MODIFICATION_OPERATOR_VALUE, MODIFICATION_OPERATOR_FUNCTION_ARGUMENT> . {return TokenType.BAD_CHARACTER;}