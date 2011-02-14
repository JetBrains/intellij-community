package org.jetbrains.yaml.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.yaml.YAMLTokenTypes;

/* Auto generated File */
%%

%class _YAMLLexer
%implements FlexLexer, YAMLTokenTypes
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// USER CODE //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{
  private int valueIndent = 0;
  private boolean afterEOL = false;
  private int braceCount = 0;
  private IElementType valueTokenType = null;
    
  private char previousChar() {
    return getChar(-1);
  }

  private char getChar(final int offset) {
    final int loc = getTokenStart()  + offset;
    return 0 <= loc && loc < zzBuffer.length() ? zzBuffer.charAt(loc) : (char) -1;
  }

  private char getCharAfter(final int offset) {
    final int loc = getTokenEnd()  + offset;
    return 0 <= loc && loc < zzBuffer.length() ? zzBuffer.charAt(loc) : (char) -1;
  }

%}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////// REGEXPS DECLARATIONS //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


EOL =                           "\n"
WHITE_SPACE_CHAR =              [ \t]
WHITE_SPACE =                   {WHITE_SPACE_CHAR}+

LINE =                          .*
COMMENT =                       "#"{LINE}

ID =                            [a-zA-Z_]([a-zA-Z0-9\-_ ]*[a-zA-Z0-9_])?
KEY =                           {ID}":"
INJECTION =                     ("{{" {ID} "}"{0,2}) | ("%{" [^}\n]* "}"?)

ESCAPE_SEQUENCE=                \\[^\n]
DSTRING=                        \"([^\\\"]|{ESCAPE_SEQUENCE})*?\"?
STRING=                         '([^\\']|{ESCAPE_SEQUENCE}|(''))*?'?

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////// STATES DECLARATIONS //////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate BRACES, VALUE, VALUE_OR_KEY, INDENT_VALUE

%%
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////// RULES declarations ////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<YYINITIAL, BRACES, VALUE, VALUE_OR_KEY> {

{COMMENT}                       {   return COMMENT; }

{EOL}                           {   yybegin(YYINITIAL);
                                    return EOL;
                                }
"{"                             {   braceCount++;
                                    yybegin(braceCount == 0 ? YYINITIAL: BRACES);
                                    return LBRACE;
                                }
"}"                             {   braceCount--;
                                    if (yystate() == BRACES && braceCount == 0){
                                      yybegin(YYINITIAL);
                                    }
                                    return RBRACE;
                                }
"["                             {   braceCount++;
                                    yybegin(braceCount == 0 ? YYINITIAL: BRACES);
                                    return LBRACKET;
                                }
"]"                             {   braceCount--;
                                    if (yystate() == BRACES && braceCount == 0){
                                      yybegin(YYINITIAL);
                                    }
                                    return RBRACKET;
                                }

","                             {   if (braceCount > 0) {
                                      yybegin(braceCount == 0 ? YYINITIAL: BRACES);
                                      return COMMA;
                                    }
                                    return TEXT;
                                }
":"                             {   yybegin(braceCount == 0 ? YYINITIAL: BRACES);
                                    return COLON;
                                }
"?"                             {   yybegin(braceCount == 0 ? YYINITIAL: BRACES);
                                    return QUESTION;
                                }

}

<YYINITIAL, BRACES, VALUE_OR_KEY> {

{KEY}                           {   yybegin(VALUE);
                                    return SCALAR_KEY;
                                }
}

<YYINITIAL, BRACES>{

"---"                           {   braceCount = 0;
                                    yybegin(YYINITIAL);
                                    return DOCUMENT_MARKER; }

{WHITE_SPACE}                   {   final char prev = previousChar();
                                    return prev == (char)-1 || prev == '\n' ? INDENT : WHITESPACE;
                                }
"-" / ({WHITE_SPACE} | {EOL})   {   yybegin(VALUE_OR_KEY);
                                    return SEQUENCE_MARKER; }

.                               {   return TEXT; }
}


<VALUE, VALUE_OR_KEY>{

{WHITE_SPACE}                   {   return WHITESPACE; }

{STRING}                        {   return SCALAR_STRING; }

{DSTRING}                       {   return SCALAR_DSTRING; }

">"/ ({WHITE_SPACE} | {EOL})      {   yybegin(INDENT_VALUE);
                                    //System.out.println("Started SCALAR_TEXT state");
                                    valueIndent = 0; // initialization
                                    afterEOL = false;
                                    valueTokenType = SCALAR_TEXT;
                                    yypushback(1);
                                }

("|"("-"|"+")?) / ({WHITE_SPACE} | {EOL})
                                {   yybegin(INDENT_VALUE);
                                    //System.out.println("Started SCALAR_LIST state");
                                    valueIndent = 0; // initialization
                                    afterEOL = false;
                                    valueTokenType = SCALAR_LIST;
                                    yypushback(yylength());
                                }

({INJECTION} | [^ \t\n,{\[|>]) ({INJECTION} | [^\n#,}\]])* ({INJECTION} | [^ \t\n#,}\]])
                                {   if (braceCount <= 0) {
                                      char c;
                                      while ((c = getCharAfter(0)) == ' ' || c == ','){
                                        zzMarkedPos++;
                                      }
                                    }
                                    return TEXT; }
.                               {   return TEXT; }
}

<INDENT_VALUE> {
{WHITE_SPACE}                           {   afterEOL = false;
                                            //System.out.println("Matched WHITESPACE:" + yytext());
                                            final int matched = yylength();
                                            if (valueIndent < 0){
                                                valueIndent = matched;
                                                //System.out.println("Indent selected:" + valueIndent);
                                            }
                                            else if (valueIndent > matched) {
                                                yybegin(YYINITIAL);
                                                //System.out.println("return to initial state");
                                            }
                                            return previousChar() == '\n' ? INDENT : WHITESPACE;
                                        }
[^ \n\t] {LINE}?                        {   if (afterEOL){
                                                yypushback(yylength());
                                                yybegin(YYINITIAL);
                                                //System.out.println("return to initial state");

                                            } else {
                                                afterEOL = false;
                                                if (valueIndent < 0) {
                                                    //System.out.println("Matched TEXT:" + yytext());
                                                    return TEXT;
                                                }
                                                //System.out.println("Matched ValueContext:" + yytext());
                                                return valueTokenType;
                                            }
                                        }
{EOL}                                   {   afterEOL = true;
                                            //System.out.println("Matched EOL:");
                                            if (valueIndent < 0) {
                                                yybegin(YYINITIAL);
                                                //System.out.println("return to initial state");
                                            }
                                            else if (valueIndent == 0) {
                                                valueIndent --;
                                            }
                                            return EOL;
                                        }
}

