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
  private int previousState = YYINITIAL;
    
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

  private boolean isAfterEol() {
    final char prev = previousChar();
    return prev == (char)-1 || prev == '\n';
  }

  private boolean isAfterSpace() {
    final char prev = previousChar();
    return prev == (char)-1 || prev == '\t' || prev == ' ';
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

ID =                            [^\n\-\ {}\[\]#][^\n{}\[\]>:#]*
KEY =                           [^,\n\-\ {}\[\]#]!(!([^\n{}>:,]*)|{LINE}{WHITE_SPACE_CHAR}"#"{LINE})":"
INJECTION =                     ("{{" {ID} "}"{0,2}) | ("%{" [^}\n]* "}"?)

ESCAPE_SEQUENCE=                \\[^\n]
DSTRING=                        \"([^\\\"]|{ESCAPE_SEQUENCE})*?\"?
STRING=                         '([^']|(''))*?'?

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////// STATES DECLARATIONS //////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate BRACES, VALUE, VALUE_OR_KEY, INDENT_VALUE

%%
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////// RULES declarations ////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<YYINITIAL, BRACES, VALUE, VALUE_OR_KEY> {

{COMMENT}                       {
                                  // YAML spec: when a comment follows another syntax element,
                                  //  it must be separated from it by space characters.
                                  return (isAfterEol() || isAfterSpace()) ? COMMENT : TEXT;
                                }

{EOL}                           {   if (braceCount == 0) {
                                      yybegin(YYINITIAL);
                                    }
                                    return EOL;
                                }
"{"                             {   braceCount++;
                                    if (braceCount != 0 && yystate() != BRACES) {
                                      previousState = yystate();
                                    }
                                    yybegin(braceCount == 0 ? previousState: BRACES);
                                    return LBRACE;
                                }
"}"                             {   if (braceCount == 0) {
                                      return TEXT;
                                    }
                                    braceCount--;
                                    if (yystate() == BRACES && braceCount == 0){
                                      yybegin(previousState);
                                    }
                                    return RBRACE;
                                }
"["                             {   braceCount++;
                                    if (braceCount != 0 && yystate() != BRACES) {
                                      previousState = yystate();
                                    }
                                    yybegin(braceCount == 0 ? previousState: BRACES);
                                    return LBRACKET;
                                }
"]"                             {   if (braceCount == 0) {
                                      return TEXT;
                                    }
                                    braceCount--;
                                    if (yystate() == BRACES && braceCount == 0){
                                      yybegin(previousState);
                                    }
                                    return RBRACKET;
                                }

","                             {   if (braceCount > 0) {
                                      yybegin(BRACES);
                                      return COMMA;
                                    }
                                    return TEXT;
                                }
":" / ({WHITE_SPACE} | {EOL})   {   return COLON; }
"?"                             {   return QUESTION; }

}

<YYINITIAL, BRACES, VALUE_OR_KEY> {


{STRING} ":" {
  return SCALAR_KEY;
}

{DSTRING} ":" {
  return SCALAR_KEY;
}

{KEY} / ({WHITE_SPACE} | {EOL}) {   yybegin(VALUE);
                                    return SCALAR_KEY;
                                }
{KEY}                           {   if (zzMarkedPos == zzEndRead){
                                      return SCALAR_KEY;
                                    }
                                    return TEXT;
                                }
}

<YYINITIAL, BRACES, VALUE, VALUE_OR_KEY> {

{WHITE_SPACE}                   { return isAfterEol() ? INDENT : WHITESPACE; }

}

<YYINITIAL, BRACES>{

"---"                           {   braceCount = 0;
                                    yybegin(YYINITIAL);
                                    return DOCUMENT_MARKER; }

"-" / ({WHITE_SPACE} | {EOL})   {   yybegin(VALUE_OR_KEY);
                                    return SEQUENCE_MARKER; }

.                               {   return TEXT; }
}


<BRACES, VALUE, VALUE_OR_KEY>{

{STRING}                        {   return SCALAR_STRING; }

{DSTRING}                       {   return SCALAR_DSTRING; }

}

<VALUE, VALUE_OR_KEY>{

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

({INJECTION} | [^ :\t\n,{\[|>]) ({INJECTION} | [^:\n#,}\]])* ({INJECTION} | [^ :\t\n#,}\]])
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
                                            return isAfterEol() ? INDENT : WHITESPACE;
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

