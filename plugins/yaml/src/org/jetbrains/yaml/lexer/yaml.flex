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
  private int currentLineIndent = 0;
  private int valueIndent = -1;
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

  private IElementType getWhitespaceTypeAndUpdateIndent() {
    if (isAfterEol()) {
      currentLineIndent = yylength();
      return INDENT;
    }
    else {
      return WHITESPACE;
    }
  }

  private boolean isAfterEol() {
    final char prev = previousChar();
    return prev == (char)-1 || prev == '\n';
  }

  private boolean isAfterSpace() {
    final char prev = previousChar();
    return prev == (char)-1 || prev == '\t' || prev == ' ';
  }

  private void yyBegin(int newState) {
    //System.out.println("yybegin(): " + newState);
    yybegin(newState);
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
NS_HEX_DIGIT = [[:digit:]a-fA-F]
NS_WORD_CHAR = [:digit:] | "-" | [a-zA-Z]
NS_URI_CHAR =  “%” {NS_HEX_DIGIT} {NS_HEX_DIGIT} | {NS_WORD_CHAR} | [#;\/?:@&=+$,_.!~*'()\[\]]
C_VERBATIM_TAG = “!” “<” {NS_URI_CHAR}+ “>”
NS_TAG_CHAR = “%” {NS_HEX_DIGIT} {NS_HEX_DIGIT} | {NS_WORD_CHAR} | [#;\/?:@&=+$_.~*'()]
C_TAG_HANDLE = “!” {NS_WORD_CHAR}+ “!” | "!" "!" | "!"
C_NS_SHORTHAND_TAG = {C_TAG_HANDLE} {NS_TAG_CHAR}+
C_NON_SPECIFIC_TAG = "!"
C_NS_TAG_PROPERTY = {C_VERBATIM_TAG} | {C_NS_SHORTHAND_TAG} | {C_NON_SPECIFIC_TAG}
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
                                      yyBegin(YYINITIAL);
                                    }
                                    currentLineIndent = 0;
                                    return EOL;
                                }
"["                             {   braceCount++;
                                    if (braceCount != 0 && yystate() != BRACES) {
                                      previousState = yystate();
                                    }
                                    yyBegin(braceCount == 0 ? previousState: BRACES);
                                    return LBRACKET;
                                }
"]"                             {   if (braceCount == 0) {
                                      yyBegin(VALUE);
                                      return TEXT;
                                    }
                                    braceCount--;
                                    if (yystate() == BRACES && braceCount == 0){
                                      yyBegin(previousState);
                                    }
                                    return RBRACKET;
                                }

","                             {   if (braceCount > 0) {
                                      yyBegin(BRACES);
                                      return COMMA;
                                    }
                                    yyBegin(VALUE);
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

{KEY} / ({WHITE_SPACE} | {EOL}) {   yyBegin( VALUE);
                                    return SCALAR_KEY;
                                }
{KEY}                           {   if (zzMarkedPos == zzEndRead){
                                      return SCALAR_KEY;
                                    }
                                    yyBegin(VALUE);
                                    return TEXT;
                                }
}

<YYINITIAL, BRACES, VALUE, VALUE_OR_KEY> {

{WHITE_SPACE}                   { return getWhitespaceTypeAndUpdateIndent(); }

}

<YYINITIAL, BRACES>{

"---"                           {   braceCount = 0;
                                    yyBegin(YYINITIAL);
                                    return DOCUMENT_MARKER; }

"-" / ({WHITE_SPACE} | {EOL})   {   yyBegin(VALUE_OR_KEY);
                                    return SEQUENCE_MARKER; }

}


<BRACES, VALUE, VALUE_OR_KEY>{

({C_NS_TAG_PROPERTY} {WHITE_SPACE}+)? {STRING} {
 return SCALAR_STRING;
}

({C_NS_TAG_PROPERTY} {WHITE_SPACE}+)? {DSTRING} {
 return SCALAR_DSTRING;
}

}

<VALUE, VALUE_OR_KEY>{

">"/ ({WHITE_SPACE} | {EOL})      {   yyBegin(INDENT_VALUE);
                                    valueIndent = currentLineIndent;
                                    valueTokenType = SCALAR_TEXT;
                                    yypushback(1);
                                }

({C_NS_TAG_PROPERTY} {WHITE_SPACE}+)? ("|"("-"|"+")?) / ({WHITE_SPACE} | {EOL})
                                {   yyBegin(INDENT_VALUE);
                                    valueIndent = currentLineIndent;
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
}

<YYINITIAL, BRACES, VALUE, VALUE_OR_KEY> {
"{"                             {   braceCount++;
                                    if (braceCount != 0 && yystate() != BRACES) {
                                      previousState = yystate();
                                    }
                                    yyBegin(braceCount == 0 ? previousState: BRACES);
                                    return LBRACE;
                                }
"}"                             {   if (braceCount == 0) {
                                      yyBegin(VALUE);
                                      return TEXT;
                                    }
                                    braceCount--;
                                    if (yystate() == BRACES && braceCount == 0){
                                      yyBegin(previousState);
                                    }
                                    return RBRACE;
                                }
}

<VALUE, VALUE_OR_KEY>{
.                               {   return TEXT; }
}

<YYINITIAL, BRACES> {
. {
  yyBegin(VALUE);
  return TEXT;
}
}

<INDENT_VALUE> {
{WHITE_SPACE}? {EOL}                    {
                                            currentLineIndent = 0;
                                            return EOL;
                                        }
{WHITE_SPACE}                           {   IElementType type = getWhitespaceTypeAndUpdateIndent();
                                            if (currentLineIndent <= valueIndent) {
                                              yyBegin(YYINITIAL);
                                            }
                                            return type;
                                        }
[^ \n\t] {LINE}?                        {   if (isAfterEol()){
                                                yypushback(yylength());
                                                yyBegin(YYINITIAL);

                                            } else {
                                                //if (valueIndent < 0) {
                                                //    yyBegin(VALUE);
                                                //    return TEXT;
                                                //}
                                                return valueTokenType;
                                            }
                                        }
}

