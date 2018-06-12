package org.jetbrains.yaml.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.yaml.YAMLTokenTypes;

/* Auto generated File */
%%

// Language specification could be found here: http://www.yaml.org/spec/1.2/spec.html

%class _YAMLLexer
%implements FlexLexer, YAMLTokenTypes
%unicode
%public
%column

%function advance
%type IElementType

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// USER CODE //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{
  private int currentLineIndent = 0;
  private int valueIndent = -1;
  private int braceCount = 0;
  private IElementType valueTokenType = null;
  private int previousState = YYINITIAL;
  private int previousAnchorState = YYINITIAL;
  private int nextState = YYINITIAL;

  protected int yycolumn = 0;

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

  // The compact notation may be used when the entry is itself a nested block collection.
  // In this case, both the “-” indicator and the following spaces are considered to be part of the indentation of the nested collection.
  // See 8.2.1. Block Sequences http://www.yaml.org/spec/1.2/spec.html#id2797382
  private IElementType getScalarKey(int nextState) {
    this.nextState = nextState;
    yyBegin(KEY_MODE);
    currentLineIndent = yycolumn;
    return SCALAR_KEY;
  }
%}
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////// REGEXPS DECLARATIONS //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// NB !(!a|b) is "a - b"
// From the spec
ANY_CHAR = [^]

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

LINE =                          [^\n]*
COMMENT =                       "#"{LINE}

ID =                            [^\n\-\ {}\[\]#][^\n{}\[\]>:#]*

KEY_flow = {NS_PLAIN_ONE_LINE_flow}
KEY_block = {NS_PLAIN_ONE_LINE_block}
KEY_SUFIX = {WHITE_SPACE_CHAR}* ":"

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

//[102] ns-anchor-char ::= ns-char - c-flow-indicator
//[103] ns-anchor-name ::= ns-anchor-char+
NS_ANCHOR_NAME = [^,\[\]\{\}\s]+

BS_HEADER_ERR_WORD = [^ \t#\n] [^ \t\n]*

/*
[162] c-b-block-header(m,t) ::= ( (c-indentation-indicator(m) c-chomping-indicator(t))
                                | (c-chomping-indicator(t) c-indentation-indicator(m)) )
                                s-b-comment
[163] c-indentation-indicator(m) ::= ns-dec-digit ⇒ m = ns-dec-digit - #x30
                                        Empty     ⇒ m = auto-detect()
[164] c-chomping-indicator(t) ::= “-”    ⇒ t = strip
                                  “+”    ⇒ t = keep
                                  Empty  ⇒ t = clip

Better to support more general c-indentation-indicator as sequence of digits and check it later
*/
C_B_BLOCK_HEADER = ( [:digit:]* ( "-" | "+" )? ) | ( ( "-" | "+" )? [:digit:]* )

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////////// STATES DECLARATIONS //////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate BRACES, VALUE, VALUE_OR_KEY, VALUE_BRACE, INDENT_VALUE, BS_HEADER_TAIL, ANCHOR_MODE, ALIAS_MODE, KEY_MODE

%%
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////// RULES declarations ////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<YYINITIAL, BRACES, VALUE, VALUE_BRACE, VALUE_OR_KEY, BS_HEADER_TAIL> {
{COMMENT}                       {
                                  // YAML spec: when a comment follows another syntax element,
                                  //  it must be separated from it by space characters.
                                  // See http://www.yaml.org/spec/1.2/spec.html#comment
                                  return (isAfterEol() || isAfterSpace()) ? COMMENT : TEXT;
                                }
}

<YYINITIAL, BRACES, VALUE, VALUE_BRACE, VALUE_OR_KEY> {

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

//[101] c-ns-anchor-property ::= “&” ns-anchor-name
& / {NS_ANCHOR_NAME}            {   previousAnchorState = yystate();
                                    yyBegin(ANCHOR_MODE);
                                    return AMPERSAND;
                                }
}

<KEY_MODE> {
":"                             {   yyBegin(nextState);
                                    return COLON;
                                }
}

<ANCHOR_MODE> {
{NS_ANCHOR_NAME}                {   yyBegin(previousAnchorState);
                                    return ANCHOR;
                                }
}

<YYINITIAL, BRACES, VALUE_OR_KEY> {


{STRING_SINGLE_LINE} | {DSTRING_SINGLE_LINE} / {KEY_SUFIX}
                                {   return getScalarKey(yystate()); }

}

<BRACES> {
//look ahead: {NS_PLAIN_SAFE_flow} symbol
{KEY_flow} / {KEY_SUFIX} {NS_PLAIN_SAFE_flow} {
  yyBegin(VALUE);
  return TEXT;
}
//look ahead: different from {NS_PLAIN_SAFE_flow} symbol or EOF
{KEY_flow} / {KEY_SUFIX} {
  return getScalarKey(VALUE_BRACE);
}
}

<YYINITIAL, VALUE_OR_KEY> {
//look ahead: {NS_PLAIN_SAFE_block} symbol
{KEY_block} / {KEY_SUFIX} {NS_PLAIN_SAFE_block} {
  yyBegin(VALUE);
  return TEXT;
}
//look ahead: different from {NS_PLAIN_SAFE_block} symbol or EOF
{KEY_block} / {KEY_SUFIX} {
  return getScalarKey(VALUE);
}
}

<YYINITIAL, BRACES, VALUE, VALUE_BRACE, VALUE_OR_KEY, BS_HEADER_TAIL, KEY_MODE> {

{WHITE_SPACE}                   { return getWhitespaceTypeAndUpdateIndent(); }

}

<YYINITIAL, BRACES>{

"---" |
"..." {
   return tokenOrForbidden(TEXT);
}

}

<YYINITIAL, BRACES, VALUE_OR_KEY> {

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

//[104] c-ns-alias-node ::= “*” ns-anchor-name
\* / {NS_ANCHOR_NAME}           {   previousAnchorState = yystate();
                                    yyBegin(ALIAS_MODE);
                                    return STAR;
                                }
}

<ALIAS_MODE> {
{NS_ANCHOR_NAME}                {   yyBegin(previousAnchorState);
                                    return ALIAS;
                                }
}

// See 8.1 Block Scalar Styles
<YYINITIAL, VALUE, VALUE_BRACE, VALUE_OR_KEY>{

// See 8.1.3. Folded Style
// [174] 	c-l+folded(n) ::= “>” c-b-block-header(m,t) l-folded-content(n+m,t)
">" {C_B_BLOCK_HEADER}          {   yyBegin(BS_HEADER_TAIL);
                                    valueTokenType = SCALAR_TEXT;
                                    return valueTokenType;
                                }

// See 8.1.2. Literal Style
// [170] c-l+literal(n) ::= “|” c-b-block-header(m,t) l-literal-content(n+m,t)
"|" {C_B_BLOCK_HEADER}          {   yyBegin(BS_HEADER_TAIL);
                                    valueTokenType = SCALAR_LIST;
                                    return valueTokenType;
                                }

}

<BS_HEADER_TAIL>{
{BS_HEADER_ERR_WORD} ([ \t]* {BS_HEADER_ERR_WORD})*
                                { return TEXT; }

{EOL}                           {   yyBegin(INDENT_VALUE);
                                    valueIndent = currentLineIndent;
                                    return EOL;
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
[^]                               {   return TEXT; }
}

<YYINITIAL, BRACES> {
[^] {
  return TEXT;
}
}

<INDENT_VALUE> {

{EOL} {
          currentLineIndent = 0;
          // First comment with ident less then block scalar ident should be after the end of this block.
          // So another EOL type is used to recognize such situation from the parser.
          return SCALAR_EOL;
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
