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

  public boolean isCleanState() {
    return yystate() == YYINITIAL
      && currentLineIndent == 0
      && braceCount == 0;
  }

  public void cleanMyState() {
    currentLineIndent = 0;
    braceCount = 0;
  }

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

  private boolean startsWith(CharSequence haystack, CharSequence needle) {
    for (int i = Math.min(haystack.length(), needle.length()) - 1; i >= 0; i--) {
      if (haystack.charAt(i) != needle.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  private IElementType tokenOrForbidden(IElementType tokenType) {
    if (!isAfterEol() || yylength() < 3) {
      return tokenType;
    }

    if (startsWith(yytext(), "---")) {
      braceCount = 0;
      yyBegin(YYINITIAL);
      yypushback(yylength() - 3);
      return DOCUMENT_MARKER;
    }
    if (startsWith(yytext(), "...")) {
      braceCount = 0;
      yyBegin(YYINITIAL);
      yypushback(yylength() - 3);
      return DOCUMENT_END;
    }
    return tokenType;
  }
%}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////// REGEXPS DECLARATIONS //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// NB !(!a|b) is "a - b"
// From the spec
ANY_CHAR = [^\n] | "\n"

NS_CHAR = [^\n\t\r\ ]
NS_INDICATOR = [-?:,\[\]\{\}#&*!|>'\"%@`]

NS_PLAIN_SAFE_flow  = [^\n\r\t\ ,\[\]\{\}] // NS_CHAR - flow indicators
NS_PLAIN_SAFE_block = {NS_CHAR}

NS_PLAIN_FIRST_flow  = !(!{NS_CHAR}|{NS_INDICATOR}) | [?:-] {NS_PLAIN_SAFE_flow}
NS_PLAIN_FIRST_block = !(!{NS_CHAR}|{NS_INDICATOR}) | [?:-] {NS_PLAIN_SAFE_block}

NS_PLAIN_CHAR_flow  = {NS_CHAR} "#" | !(!{NS_PLAIN_SAFE_flow}|[:#])  | ":" {NS_PLAIN_SAFE_flow}
NS_PLAIN_CHAR_block = {NS_CHAR} "#" | !(!{NS_PLAIN_SAFE_block}|[:#]) | ":" {NS_PLAIN_SAFE_block}

NB_NS_PLAIN_IN_LINE_flow  = ({WHITE_SPACE_CHAR}* {NS_PLAIN_CHAR_flow})*
NB_NS_PLAIN_IN_LINE_block = ({WHITE_SPACE_CHAR}* {NS_PLAIN_CHAR_block})*

NS_PLAIN_ONE_LINE_flow  = {NS_PLAIN_FIRST_flow}  {NB_NS_PLAIN_IN_LINE_flow}
NS_PLAIN_ONE_LINE_block = {NS_PLAIN_FIRST_block} {NB_NS_PLAIN_IN_LINE_block}

EOL =                           "\n"
WHITE_SPACE_CHAR =              [ \t]
WHITE_SPACE =                   {WHITE_SPACE_CHAR}+

LINE =                          .*
COMMENT =                       "#"{LINE}

ID =                            [^\n\-\ {}\[\]#][^\n{}\[\]>:#]*

KEY_flow = {NS_PLAIN_ONE_LINE_flow} {WHITE_SPACE_CHAR}* ":"
KEY_block = {NS_PLAIN_ONE_LINE_block} {WHITE_SPACE_CHAR}* ":"

INJECTION =                     ("{{" {ID} "}"{0,2}) | ("%{" [^}\n]* "}"?)

ESCAPE_SEQUENCE=                \\[^\n]
DSTRING_SINGLE_LINE=            \"([^\\\"\n]|{ESCAPE_SEQUENCE})*\"
DSTRING=                        \"([^\\\"]|{ESCAPE_SEQUENCE}|\\\n)*\"
STRING_SINGLE_LINE=             '([^'\n]|'')*'
STRING=                         '([^']|'')*'
NS_HEX_DIGIT = [[:digit:]a-fA-F]
NS_WORD_CHAR = [:digit:] | "-" | [a-zA-Z]
NS_URI_CHAR =  "%" {NS_HEX_DIGIT} {NS_HEX_DIGIT} | {NS_WORD_CHAR} | [#;\/?:@&=+$,_.!~*'()\[\]]
C_VERBATIM_TAG = "!" "<" {NS_URI_CHAR}+ ">"
NS_TAG_CHAR = "%" {NS_HEX_DIGIT} {NS_HEX_DIGIT} | {NS_WORD_CHAR} | [#;\/?:@&=+$_.~*'()]
C_TAG_HANDLE = "!" {NS_WORD_CHAR}+ "!" | "!" "!" | "!"
C_NS_SHORTHAND_TAG = {C_TAG_HANDLE} {NS_TAG_CHAR}+
C_NON_SPECIFIC_TAG = "!"
C_NS_TAG_PROPERTY = {C_VERBATIM_TAG} | {C_NS_SHORTHAND_TAG} | {C_NON_SPECIFIC_TAG}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////// STATES DECLARATIONS //////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate BRACES, VALUE, VALUE_OR_KEY, VALUE_BRACE, INDENT_VALUE

%%
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////// RULES declarations ////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<YYINITIAL, BRACES, VALUE, VALUE_BRACE, VALUE_OR_KEY> {

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

{C_NS_TAG_PROPERTY} / ({WHITE_SPACE} | {EOL}) {
  return TAG;
}

}

<YYINITIAL, BRACES, VALUE_OR_KEY> {


{STRING_SINGLE_LINE} ":" {
  return SCALAR_KEY;
}

{DSTRING_SINGLE_LINE} ":" {
  return SCALAR_KEY;
}


}

<BRACES> {
{KEY_flow} / !(!{ANY_CHAR}|{NS_PLAIN_SAFE_flow}) {
  yyBegin(VALUE_BRACE);
  return SCALAR_KEY;
}

}

<YYINITIAL, VALUE_OR_KEY> {
{KEY_block} / !(!{ANY_CHAR}|{NS_PLAIN_SAFE_block}) {
  yyBegin(VALUE);
  return SCALAR_KEY;
}
}

<YYINITIAL, BRACES, VALUE, VALUE_BRACE, VALUE_OR_KEY> {

{WHITE_SPACE}                   { return getWhitespaceTypeAndUpdateIndent(); }

}

<YYINITIAL, BRACES>{

"---" |
"..." {
   return tokenOrForbidden(TEXT);
}

"-" / ({WHITE_SPACE} | {EOL})   {   yyBegin(VALUE_OR_KEY);
                                    return SEQUENCE_MARKER; }

}


<YYINITIAL, BRACES, VALUE, VALUE_BRACE, VALUE_OR_KEY>{

{STRING} {
 return SCALAR_STRING;
}

{DSTRING} {
 return SCALAR_DSTRING;
}

}

<YYINITIAL, VALUE, VALUE_BRACE, VALUE_OR_KEY>{

">"("-"|"+")? / ({WHITE_SPACE} | {EOL})      {
                                    yyBegin(INDENT_VALUE);
                                    valueIndent = currentLineIndent;
                                    valueTokenType = SCALAR_TEXT;
                                    return valueTokenType;
                                }

"|"("-"|"+")? / ({WHITE_SPACE} | {EOL})
                                {   yyBegin(INDENT_VALUE);
                                    valueIndent = currentLineIndent;
                                    valueTokenType = SCALAR_LIST;
                                    return valueTokenType;
                                }

}

<YYINITIAL, VALUE, VALUE_OR_KEY> {
  {INJECTION}? {NS_PLAIN_ONE_LINE_block} {
    return tokenOrForbidden(TEXT);
  }
}

<BRACES, VALUE_BRACE> {
  {INJECTION}? {NS_PLAIN_ONE_LINE_flow} {
    return tokenOrForbidden(TEXT);
  }
}

<YYINITIAL, BRACES, VALUE, VALUE_BRACE, VALUE_OR_KEY> {
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

<VALUE, VALUE_BRACE, VALUE_OR_KEY>{
.                               {   return TEXT; }
}

<YYINITIAL, BRACES> {
. {
  return TEXT;
}
}

<INDENT_VALUE> {

{EOL} {
          currentLineIndent = 0;
          return EOL;
      }

{WHITE_SPACE} / {EOL}                    {
                                            return getWhitespaceTypeAndUpdateIndent();
                                        }
{WHITE_SPACE}                           {   IElementType type = getWhitespaceTypeAndUpdateIndent();
                                            if (currentLineIndent <= valueIndent) {
                                              yyBegin(YYINITIAL);
                                            }
                                            return type;
                                        }
[^ \n\t] {LINE}?                        {   if (currentLineIndent <= valueIndent) {
                                                yypushback(yylength());
                                                yyBegin(YYINITIAL);
                                                break;
                                            } else {
                                                return valueTokenType;
                                            }
                                        }
}

// Rules for matching EOLs
<BRACES> {

{KEY_flow} {
  if (zzMarkedPos == zzEndRead){
    return SCALAR_KEY;
  }
  yyBegin(VALUE);
  return tokenOrForbidden(TEXT);
}

}

<YYINITIAL, VALUE_OR_KEY> {
{KEY_block} {
  if (zzMarkedPos == zzEndRead){
    return SCALAR_KEY;
  }
  yyBegin(VALUE);
  return tokenOrForbidden(TEXT);
}
}