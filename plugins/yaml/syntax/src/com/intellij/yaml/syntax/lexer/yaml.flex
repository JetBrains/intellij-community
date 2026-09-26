package com.intellij.yaml.syntax.lexer

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.util.lexer.FlexLexer
import com.intellij.yaml.syntax.YamlSyntaxTokenTypes

/* Auto generated File */
%%

// Language specification could be found here: http://www.yaml.org/spec/1.2/spec.html

%class _YamlLexer
%implements FlexLexer
%unicode
%public
%column

%function advance
%type SyntaxElementType

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// USER CODE //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{
  /**
   * The number of open but not closed braces.
   * Note: lexer does not distinguish braces from brackets while counting them.
   */
  private var braceCount = 0
  
  /** A token type of parsed block scalar */
  private var blockScalarType: SyntaxElementType? = null

  /** A state to be returned in (or it is used to calculate next state) */
  private var returnState = YYINITIAL

  /**
   * An indent of block composite element (key or sequence marker).
   * It is used to identify range of block scalars and plain text scalars.
   */
  private var prevElementIndent = 0

  /** This flag is set after the first plain scalar line until it ends */
  private var possiblePlainTextScalarContinue = false

  //-------------------------------------------------------------------------------------------------------------------

  fun isCleanState(): Boolean = yystate() == YYINITIAL
    && braceCount == 0
    && prevElementIndent == 0
    && !possiblePlainTextScalarContinue

  fun cleanMyState() {
    braceCount = 0
    blockScalarType = null

    yycolumn = 0
    returnState = YYINITIAL

    prepareForNewDocument()
    yybegin(YYINITIAL)
  }

  private fun prepareForNewDocument() {
    prevElementIndent = 0
    possiblePlainTextScalarContinue = false
  }

  //-------------------------------------------------------------------------------------------------------------------

  /** @param offset offset from currently matched token start (could be negative) */
  private fun charAt(offset: Int): Char {
    val loc = getTokenStart() + offset
    return zzBuffer.getOrElse(loc) { (-1).toChar() }
  }

  private fun isAfterEol(): Boolean {
    val prev = charAt(-1)
    return prev == (-1).toChar() || prev == '\n'
  }

  private fun getWhitespaceType() = if (isAfterEol()) YamlSyntaxTokenTypes.INDENT else YamlSyntaxTokenTypes.WHITESPACE

  private fun goToState(state: Int) {
    yybegin(state)
    yypushback(yylength())
  }

  //-------------------------------------------------------------------------------------------------------------------

  /**
   * @param indentLen The length of indent in the current line
   * @return the next state
   */
  private fun getStateAfterLineStart(indentLen: Int) = if (
      possiblePlainTextScalarContinue &&
      yycolumn + indentLen > prevElementIndent
  ) {
      POSSIBLE_PLAIN_TEXT_STATE
  }
  else {
      possiblePlainTextScalarContinue = false
      BLOCK_STATE
  }


  private fun getStateAfterBlockScalar() = if (returnState == BLOCK_STATE) LINE_START_STATE else FLOW_STATE

  private fun openBrace() {
    braceCount += 1
    if (braceCount != 0) {
      prevElementIndent = 0
      possiblePlainTextScalarContinue = false
      yybegin(FLOW_STATE)
    }
  }

  private fun closeBrace() {
    if (braceCount > 0) {
      braceCount -= 1
    }
    if (braceCount == 0){
      yybegin(BLOCK_STATE)
    }
  }

  /**
   * This method stores return lexer state, stores indent information and moves to the key mode state
   * @return scalar key token
   */
  private fun processScalarKey(newReturnState: Int): SyntaxElementType {
    prevElementIndent = yycolumn
    returnState = newReturnState
    yybegin(KEY_MODE)
    return YamlSyntaxTokenTypes.SCALAR_KEY
  }

  private fun processScalarKey() = processScalarKey(yystate())
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
NS_PLAIN_FIRST_second_line = [^\n\t\r\ :] | ( ":" {NS_PLAIN_SAFE_block} )

NS_PLAIN_CHAR_flow  = {NS_CHAR} "#" | !(!{NS_PLAIN_SAFE_flow}|[:#])  | ":" {NS_PLAIN_SAFE_flow}
NS_PLAIN_CHAR_block = {NS_CHAR} "#" | !(!{NS_PLAIN_SAFE_block}|[:#]) | ":" {NS_PLAIN_SAFE_block}

NB_NS_PLAIN_IN_LINE_flow  = "#"* ({WHITE_SPACE_CHAR}* {NS_PLAIN_CHAR_flow})*
NB_NS_PLAIN_IN_LINE_block = "#"* ({WHITE_SPACE_CHAR}* {NS_PLAIN_CHAR_block})*

NS_PLAIN_ONE_LINE_flow  = {NS_PLAIN_FIRST_flow}  {NB_NS_PLAIN_IN_LINE_flow}
NS_PLAIN_ONE_LINE_block = {NS_PLAIN_FIRST_block} {NB_NS_PLAIN_IN_LINE_block}

EOL =                           "\n"
WHITE_SPACE_CHAR =              [ \t]
SPACE_SEPARATOR_CHAR =          !(![ \t\n])
WHITE_SPACE =                   {WHITE_SPACE_CHAR}+

LINE =                          [^\n]*

// YAML spec: when a comment follows another syntax element,
//  it must be separated from it by space characters.
// See http://www.yaml.org/spec/1.2/spec.html#comment
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
// It seems colon is not permitted as a part of anchor name. See RUBY-23179
// But the standard id not mentioned it directly.
// Through online YAML validator and ruby YAML implementation both don't allow colon in anchor names.
NS_ANCHOR_NAME = [^:,\[\]\{\}\s]+

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

// Main states flow:
//
//       | -----------------------------
//       |                              \
//      \/                              |
// LINE_START_STATE ---->BLOCK_STATE ----
//       /\         |      /\  /\       |
//       |          |      |   |       \/
//       |          |     |    ---FLOW_STATE
//       |          |    |
//       |          |   | (syntax error)
//       \         \/  |
//        ----POSSIBLE_PLAIN_TEXT_STATE

// Main states
%xstate LINE_START_STATE, BLOCK_STATE, FLOW_STATE, POSSIBLE_PLAIN_TEXT_STATE

// Small technical one-token states
%xstate ANCHOR_MODE, ALIAS_MODE, KEY_MODE

// Block scalar states
%xstate BS_HEADER_TAIL_STATE, BS_BODY_STATE

%%
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////// RULES declarations ////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// State in the start of new line in block mode
<YYINITIAL, LINE_START_STATE> {
  // It is a text, go next state and process it there
  ("---" | "...") / {NS_CHAR} { goToState(getStateAfterLineStart(0)) }

  "---" {
        prepareForNewDocument()
        return YamlSyntaxTokenTypes.DOCUMENT_MARKER
      }

  "..." {
        return YamlSyntaxTokenTypes.DOCUMENT_END
      }

  {WHITE_SPACE} {
        yybegin(getStateAfterLineStart(yylength()))
        return getWhitespaceType()
      }

  [^] { goToState(getStateAfterLineStart(0)) }
}

<BLOCK_STATE> {
  {EOL} {
        if (!possiblePlainTextScalarContinue && prevElementIndent == 0) {
          // It is hard to find clean state in YAML lexer :(
          yybegin(YYINITIAL)
        }
        else {
          yybegin(LINE_START_STATE)
        }
        return YamlSyntaxTokenTypes.EOL
      }

  // It is JetBrains extention
  {INJECTION} {NS_PLAIN_ONE_LINE_block} {
        return YamlSyntaxTokenTypes.TEXT
      }
}

<FLOW_STATE> {
  // Need to consider end of file after document range markers
  {EOL} /  ( "---" | "..." ) {NS_CHAR}  {  return YamlSyntaxTokenTypes.EOL }
  {EOL} / ( "---" | "..." ) {
          cleanMyState()
          return YamlSyntaxTokenTypes.EOL
          }
  {EOL} { return YamlSyntaxTokenTypes.EOL }

  // It is JetBrains extention
  {INJECTION} {NS_PLAIN_ONE_LINE_flow} {
        return YamlSyntaxTokenTypes.TEXT
      }

  "," { return YamlSyntaxTokenTypes.COMMA } // do not move to another state
}

// Common block and flow rules
<BLOCK_STATE, FLOW_STATE> {
  {COMMENT} { return YamlSyntaxTokenTypes.COMMENT }

  {WHITE_SPACE} { return getWhitespaceType() }

  // If there is non-space symbol after COLON then another rule will be applied
  // Better do not use suffix here because of possible EOF after the COLON
  ":" {
        prevElementIndent = yycolumn
        return YamlSyntaxTokenTypes.COLON
      }

  //[101] c-ns-anchor-property ::= “&” ns-anchor-name
  & / {NS_ANCHOR_NAME} {
        returnState = yystate()
        yybegin(ANCHOR_MODE)
        return YamlSyntaxTokenTypes.AMPERSAND
     }

  //[104] c-ns-alias-node ::= “*” ns-anchor-name
  "*" / {NS_ANCHOR_NAME} {
        returnState = yystate()
        yybegin(ALIAS_MODE)
        return YamlSyntaxTokenTypes.STAR
      }

  {C_NS_TAG_PROPERTY} / ({WHITE_SPACE} | {EOL}) {
        return YamlSyntaxTokenTypes.TAG
      }

  "[" {
        openBrace()
        return YamlSyntaxTokenTypes.LBRACKET
      }
  "]" {
        closeBrace()
        return YamlSyntaxTokenTypes.RBRACKET
      }

  "{" {
        openBrace()
        return YamlSyntaxTokenTypes.LBRACE
      }
  "}" {
        closeBrace()
        return YamlSyntaxTokenTypes.RBRACE
      }

  "?" {
        prevElementIndent = yycolumn
        return YamlSyntaxTokenTypes.QUESTION
      } // do not move to another state

  // The compact notation may be used when the entry is itself a nested block collection.
  // In this case, both the “-” indicator and the following spaces are considered to be part of the indentation of the nested collection.
  // See 8.2.1. Block Sequences http://www.yaml.org/spec/1.2/spec.html#id2797382
  "-" / ({WHITE_SPACE} | {EOL}) {
        prevElementIndent = yycolumn
        return YamlSyntaxTokenTypes.SEQUENCE_MARKER
      }

  //TODO: maybe block scalar rules should be moved in block specific mode

  // See 8.1.3. Folded Style
  // [174] 	c-l+folded(n) ::= “>” c-b-block-header(m,t) l-folded-content(n+m,t)
  ">" {C_B_BLOCK_HEADER} {
        returnState = yystate()
        yybegin(BS_HEADER_TAIL_STATE)
        blockScalarType = YamlSyntaxTokenTypes.SCALAR_TEXT
        return blockScalarType
      }

  // See 8.1.2. Literal Style
  // [170] c-l+literal(n) ::= “|” c-b-block-header(m,t) l-literal-content(n+m,t)
  "|" {C_B_BLOCK_HEADER} {
        returnState = yystate()
        yybegin(BS_HEADER_TAIL_STATE)
        blockScalarType = YamlSyntaxTokenTypes.SCALAR_LIST
        return blockScalarType
      }

  {STRING_SINGLE_LINE} | {DSTRING_SINGLE_LINE} / {KEY_SUFIX} {
        return processScalarKey()
      }

  {STRING} {
        return YamlSyntaxTokenTypes.SCALAR_STRING
      }

  {DSTRING} {
        return YamlSyntaxTokenTypes.SCALAR_DSTRING
      }
}

<BLOCK_STATE> {
  {KEY_block} / {KEY_SUFIX} {
        return processScalarKey()
      }

  {NS_PLAIN_ONE_LINE_block} {
        possiblePlainTextScalarContinue = true
        return YamlSyntaxTokenTypes.TEXT
      }

  [^] { return YamlSyntaxTokenTypes.TEXT }
}

<FLOW_STATE> {
  {KEY_flow} / {KEY_SUFIX} {
        return processScalarKey()
      }

  {NS_PLAIN_ONE_LINE_flow} {
        return YamlSyntaxTokenTypes.TEXT
      }

  [^] { return YamlSyntaxTokenTypes.TEXT }
}


<POSSIBLE_PLAIN_TEXT_STATE> {
  {EOL} {
        yybegin(LINE_START_STATE)
        return YamlSyntaxTokenTypes.EOL
      }

  {WHITE_SPACE} { return getWhitespaceType() }
  {COMMENT} { return YamlSyntaxTokenTypes.COMMENT }

  // If there is non-space symbol after COLON then another rule will be applied
  // Better do not use suffix here because of possible EOF after the COLON
  ":" { goToState(BLOCK_STATE) }

  {STRING_SINGLE_LINE} | {DSTRING_SINGLE_LINE} / {KEY_SUFIX} {
        return processScalarKey(BLOCK_STATE)
      }

  {KEY_block} / {KEY_SUFIX} {NS_PLAIN_SAFE_block} { return YamlSyntaxTokenTypes.TEXT }

  {KEY_block} / {KEY_SUFIX} {
        return processScalarKey(BLOCK_STATE)
      }

  {NS_PLAIN_FIRST_second_line} {NB_NS_PLAIN_IN_LINE_block} {
        return YamlSyntaxTokenTypes.TEXT
      }

  [^] { return YamlSyntaxTokenTypes.TEXT }
}

//----------Small states---------------

//TODO: merge these states
<ANCHOR_MODE> {
  {NS_ANCHOR_NAME} {
        yybegin(returnState)
        return YamlSyntaxTokenTypes.ANCHOR
      }
  [^] { return YamlSyntaxTokenTypes.TEXT } // It is a bug here. TODO: how to report it
}

<ALIAS_MODE> {
  {NS_ANCHOR_NAME} {
        yybegin(returnState)
        return YamlSyntaxTokenTypes.ALIAS
      }
  [^] { return YamlSyntaxTokenTypes.TEXT } // It is a bug here. TODO: how to report it
}

<KEY_MODE> {
  {WHITE_SPACE} { return getWhitespaceType() }

  ":" {
        yybegin(returnState)
        return YamlSyntaxTokenTypes.COLON
      }
  [^] { return YamlSyntaxTokenTypes.TEXT } // It is a bug here. TODO: how to report it
}

//----------Block scalar states---------------

<BS_HEADER_TAIL_STATE> {
  {WHITE_SPACE} { return getWhitespaceType() }
  {COMMENT} { return YamlSyntaxTokenTypes.COMMENT }

  {BS_HEADER_ERR_WORD} ([ \t]* {BS_HEADER_ERR_WORD})* { return YamlSyntaxTokenTypes.TEXT }

  {EOL} {
          goToState(BS_BODY_STATE)
        }
}

<BS_BODY_STATE> {
  // First comment with ident less then block scalar ident should be after the end of this block.
  // So another EOL type is used to recognize such situation from the parser.
  // Exclude last EOL from block scalar to proper folding and other IDE functionality
  {EOL} {WHITE_SPACE_CHAR}* / {NS_CHAR} {
        val indent = yylength() - 1
        yypushback(indent)
        if (indent <= prevElementIndent) {
          yybegin(getStateAfterBlockScalar())
          return YamlSyntaxTokenTypes.EOL
        }
        else {
          return YamlSyntaxTokenTypes.SCALAR_EOL
        }
      }

  {EOL} { return YamlSyntaxTokenTypes.SCALAR_EOL }

  {WHITE_SPACE} { return getWhitespaceType() }

  [^ \n\t] {LINE}? {
        require(yycolumn > prevElementIndent)
        return blockScalarType
      }

  [^] { return YamlSyntaxTokenTypes.TEXT } // It is a bug here. TODO: how to report it
}
