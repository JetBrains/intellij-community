/* It's an automatically generated code. Do not modify it. */
package com.intellij.lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.*;
import com.intellij.psi.xml.*;


public class _HtmlLexer implements Lexer, Cloneable {
	private static final int YY_F = -1;
	private static final int YY_NO_STATE = -1;
	private static final int YY_NOT_ACCEPT = 0;
	private static final int YY_START = 1;
	private static final int YY_END = 2;
	private static final int YY_NO_ANCHOR = 4;
	private static final char YYEOF = '\uFFFF';

  private IElementType myTokenType;
  private IElementType elStartType = XmlTokenType.XML_DATA_CHARACTERS;
  private IElementType elEndType = XmlTokenType.XML_DATA_CHARACTERS;
  private IElementType elValueType = XmlTokenType.XML_DATA_CHARACTERS;
  private int myState;
  public void setElTypes(IElementType _elStartType,IElementType _elValueType,IElementType _elEndType) {
    elStartType = _elStartType;
    elEndType = _elEndType;
    elValueType = _elValueType;
  }
  public final void start(char[] buffer){
    start(buffer, 0, buffer.length);
  }
  public final void start(char[] buffer, int startOffset, int endOffset){
    start(buffer, startOffset, endOffset, (short)YYINITIAL);
  }
  public final void start(char[] buffer, int startOffset, int endOffset, int initialState){
    yy_buffer = buffer;
    yy_buffer_index = startOffset;
    yy_buffer_length = endOffset;
    yy_lexical_state = initialState;
    myTokenType = null;
    myState = initialState;
  }
  public final int getState(){
    return myState;
  }
  public final int getLastState() {
    return LAST_STATE;
  }
  public final IElementType getTokenType(){
    locateToken();
    return myTokenType;
  }
  public final int getTokenStart(){
    if (myTokenType != null){
      return yy_buffer_start;
    }
    else{
      return yy_buffer_index;
    }
  }
  public final int getTokenEnd(){
    locateToken();
    return yy_buffer_end;
  }
  public final void advance(){
    locateToken();
    myTokenType = null;
    myState = yy_lexical_state;
  }
  public final char[] getBuffer(){
    return yy_buffer;
  }
  public final int getBufferEnd(){
    return yy_buffer_length;
  }
  protected final void locateToken(){
    if (myTokenType != null) return;
    _locateToken();
  }
  private boolean checkAhead(String text){
    int index = yy_buffer_index;
    for(int i = 0; i < text.length(); i++){
      if (index >= yy_buffer_length) return false;
      if (yy_buffer[index++] != text.charAt(i)) return false;
    }
    return true;
  }
  public int getSmartUpdateShift() {
    return 8; // to handle "</script.."
  }
  public Object clone() {
    try{
      return super.clone();
    }
    catch(CloneNotSupportedException e){
      return null;
    }
  }
	private int yy_buffer_index;
	private int yy_buffer_start;
	private int yy_buffer_end;
	private char yy_buffer[];
	private int yy_buffer_length;
	private int yy_lexical_state;

	public _HtmlLexer () {
		yy_lexical_state = YYINITIAL;

    myTokenType = null;
	}

	private boolean yy_eof_done = false;
	public static final short PROCESSING_INSTRUCTION = 9;
	public static final short EL_STATE3 = 12;
	public static final short EL_STATE2 = 11;
	public static final short END_TAG_NAME = 4;
	public static final short START_TAG_NAME = 3;
	public static final short ATTRIBUTE_VALUE_SQ = 8;
	public static final short DOC_TYPE = 1;
	public static final short ATTRIBUTE_VALUE_DQ = 7;
	public static final short EL_STATE = 10;
	public static final short YYINITIAL = 0;
	public static final short TAG_ATTRIBUTES = 5;
	public static final short COMMENT = 2;
	public static final short ATTRIBUTE_VALUE_START = 6;
	public static final short LAST_STATE = 13;
	private static final int yy_state_dtrans[] = {
		0,
		82,
		14,
		94,
		95,
		96,
		24,
		29,
		34,
		37,
		39,
		41,
		43
	};
	private static final int YY_E_INTERNAL = 0;
	private static final int YY_E_MATCH = 1;
	private java.lang.String yy_error_string[] = {
		"Error: Internal error.\n",
		"Error: Unmatched input.\n"
	};
	private void yy_error (int code,boolean fatal) {
		java.lang.System.out.print(yy_error_string[code]);
		java.lang.System.out.flush();
		if (fatal) {
			throw new Error("Fatal Error.\n");
		}
	}
private static int [][] unpackFromString(int size1, int size2, String st)
    {
      int colonIndex = -1;
      String lengthString;
      int sequenceLength = 0;
      int sequenceInteger = 0;
      int commaIndex;
      String workString;
      int res[][] = new int[size1][size2];
      for (int i= 0; i < size1; i++)
    for (int j= 0; j < size2; j++)
      {
        if (sequenceLength == 0)
          {
            commaIndex = st.indexOf(',');
            if (commaIndex == -1)
              workString = st;
            else
              workString = st.substring(0, commaIndex);
            st = st.substring(commaIndex+1);
            colonIndex = workString.indexOf(':');
            if (colonIndex == -1)
              {
                res[i][j] = Integer.parseInt(workString);
              }
            else
              {
                lengthString = workString.substring(colonIndex+1);
                sequenceLength = Integer.parseInt(lengthString);
                workString = workString.substring(0,colonIndex);
                sequenceInteger = Integer.parseInt(workString);
                res[i][j] = sequenceInteger;
                sequenceLength--;
              }
          }
        else
          {
            res[i][j] = sequenceInteger;
            sequenceLength--;
          }
      }
      return res;
    }
	private static final int yy_acpt[] = {
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT,
		YY_NOT_ACCEPT
	};
	private static final int yy_cmap[] = {
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 1, 1, 0, 1, 1, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		2, 3, 4, 0, 5, 0, 0, 6,
		0, 0, 0, 0, 0, 7, 8, 9,
		8, 8, 8, 8, 8, 8, 8, 8,
		8, 8, 10, 0, 11, 12, 13, 14,
		0, 15, 16, 17, 18, 19, 15, 15,
		20, 21, 15, 15, 22, 23, 15, 24,
		25, 15, 15, 15, 26, 27, 15, 15,
		15, 28, 15, 0, 29, 0, 0, 10,
		0, 15, 16, 17, 18, 19, 15, 15,
		20, 21, 15, 15, 22, 23, 15, 24,
		25, 15, 15, 15, 26, 27, 15, 15,
		15, 28, 15, 30, 0, 31, 0, 0

	};
	private static final int yy_rmap[] = {
		0, 1, 2, 3, 4, 5, 5, 5,
		5, 5, 5, 5, 5, 5, 6, 5,
		7, 5, 5, 8, 5, 5, 5, 5,
		9, 5, 5, 5, 5, 10, 5, 5,
		5, 11, 12, 13, 5, 14, 5, 15,
		5, 16, 5, 17, 5, 4, 18, 19,
		20, 21, 22, 23, 11, 13, 13, 24,
		25, 26, 27, 28, 29, 30, 31, 32,
		33, 34, 35, 36, 37, 38, 39, 40,
		41, 42, 43, 44, 45, 46, 47, 48,
		49, 50, 51, 20, 31, 52, 53, 54,
		55, 56, 57, 58, 59, 60, 61, 62,
		63, 64, 65
	};
	private static final int yy_nxt[][] = unpackFromString(66,32,
"45,1:2,45:2,59,45:5,2,45:20,-1,1:2,-1:29,45,-1:2,47,45:5,5,45:4,6,-1:14,45:4,-1:2,61,45:5,-1,45:4,-1:15,45:4,-1:2,45:8,3,45:20,-1:32,49:7,72,49:24,-1:7,16:2,-1,16,-1:4,16:14,-1:10,19:2,-1,19,-1:4,19:14,-1:3,51,1:2,51,25,51,26,51:2,74,51:3,27,51:18,52:4,30,76,52:23,63,52:6,-1,97,52:26,54:4,9,78,35,54:22,64,54:6,-1,98,54:26,55,65,1,55:11,80,55:17,56,66,1,56:28,40,57,46,1,57:28,42,58,60,1,58:28,44,57,46,1,57:28,-1,45,-1:2,45:4,67,45:10,69,45:13,83:4,11,83:27,49:7,91,49:24,-1:13,23,-1:18,51,-1:2,51,-1,51,-1,51:2,-1,51:3,-1,51:18,55:2,-1,55:11,-1,55:17,56:2,-1,56:28,-1,57:2,-1,57:28,-1,58:2,-1,58:28,-1,45,-1:2,45:8,3,45:18,4,45,58,60,1,58:28,-1,45,-1:2,45:4,71,45:10,-1,45:13,84:6,11,84:25,52:4,32,97,33,52:25,54:4,32,98,53,54:25,55,65,1,55:11,-1,55:17,56,66,1,56:28,-1,45,-1:2,45:4,7,45:24,-1:26,85,-1:29,73,-1:34,86,-1:4,45,-1:2,45:4,-1,45:24,49:7,92,49:24,-1:17,75,-1:27,28,-1:44,77,-1:5,52:4,-1,52:25,31,52,-1:28,79,-1:3,54:4,-1,54:25,36,54,-1:25,81,-1:19,38,-1:37,8,-1:12,9,1:2,9,48,9,62,9:6,10,9:6,68,9:4,70,9:6,-1:23,87,-1:24,88,-1:37,12,-1:31,89,-1:30,90,-1:27,13,-1:14,49:7,93,49:37,15,49:31,-1,49:18,9:10,16,9:4,16:14,9:13,16,17,9:3,16:14,9:3,18,1:2,18:6,50,19,20,21,22,18,19:14,18:3,52:4,-1,52:25,-1,52,54:4,-1,54:25,-1,54");
	public void _locateToken ()
 {
		char yy_lookahead;
		int yy_anchor = YY_NO_ANCHOR;
		int yy_state = yy_state_dtrans[yy_lexical_state];
		int yy_next_state = YY_NO_STATE;
		int yy_last_accept_state = YY_NO_STATE;
		boolean yy_initial = true;
		int yy_this_accept;

		yy_buffer_start = yy_buffer_index;
		yy_this_accept = yy_acpt[yy_state];
		if (YY_NOT_ACCEPT != yy_this_accept) {
			yy_last_accept_state = yy_state;
		yy_buffer_end = yy_buffer_index;
		}
		while (true) {
			if (yy_buffer_index < yy_buffer_length){
			yy_lookahead = yy_buffer[yy_buffer_index++];
			if (yy_lookahead < 0 || yy_lookahead > 127){
			if (Character.isJavaIdentifierStart(yy_lookahead)){
			yy_lookahead = 'A';
			}
			else if (Character.isJavaIdentifierPart(yy_lookahead)){
			yy_lookahead = '9';
			}
			else{
			yy_lookahead = '#';
			}
			}
			}
			else{
			yy_lookahead = YYEOF;
			}
			yy_next_state = YY_F;
			if (YYEOF != yy_lookahead) {
				yy_next_state = yy_nxt[yy_rmap[yy_state]][yy_cmap[yy_lookahead]];
			}
			if (YY_F != yy_next_state) {
				yy_state = yy_next_state;
				yy_initial = false;
				yy_this_accept = yy_acpt[yy_state];
				if (YY_NOT_ACCEPT != yy_this_accept) {
					yy_last_accept_state = yy_state;
		yy_buffer_end = yy_buffer_index;
				}
			}
			else {
				if (YYEOF == yy_lookahead && true == yy_initial) {
  myTokenType = null; return;
				}
				else if (YY_NO_STATE == yy_last_accept_state) {
					throw (new Error("Lexical Error: Unmatched Input."));
				}
				else {
		yy_buffer_index = yy_buffer_end;
					yy_anchor = yy_acpt[yy_last_accept_state];
					if (0 != (YY_END & yy_anchor)) {
		--yy_buffer_end;
					}
					if (0 != (YY_START & yy_anchor)) {
		++yy_buffer_start;
					}
					switch (yy_last_accept_state) {
					case 0:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -2:
						break;
					case 1:
						{ myTokenType = XmlTokenType.XML_WHITE_SPACE; return; }
					case -3:
						break;
					case 2:
						{ myTokenType = XmlTokenType.XML_START_TAG_START; yy_lexical_state = START_TAG_NAME; return; }
					case -4:
						break;
					case 4:
						{ myTokenType = elStartType; yy_lexical_state = EL_STATE; return; }
					case -5:
						break;
					case 5:
						{ myTokenType = XmlTokenType.XML_END_TAG_START; yy_lexical_state = END_TAG_NAME; return; }
					case -6:
						break;
					case 6:
						{ myTokenType = XmlTokenType.XML_PI_START; yy_lexical_state = PROCESSING_INSTRUCTION; return; }
					case -7:
						break;
					case 7:
						{ myTokenType = XmlTokenType.XML_COMMENT_START; yy_lexical_state = COMMENT; return; }
					case -8:
						break;
					case 8:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_START; yy_lexical_state = DOC_TYPE; return; }
					case -9:
						break;
					case 9:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -10:
						break;
					case 10:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_END; yy_lexical_state = YYINITIAL; return; }
					case -11:
						break;
					case 11:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
					case -12:
						break;
					case 12:
						{ myTokenType = XmlTokenType.XML_NAME;  return; }
					case -13:
						break;
					case 13:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_PUBLIC;  return; }
					case -14:
						break;
					case 14:
						{ myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
					case -15:
						break;
					case 15:
						{ myTokenType = XmlTokenType.XML_COMMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -16:
						break;
					case 16:
						{ myTokenType = XmlTokenType.XML_NAME; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -17:
						break;
					case 17:
						{ yy_lexical_state = YYINITIAL; --yy_buffer_index; break; }
					case -18:
						break;
					case 18:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -19:
						break;
					case 19:
						{ myTokenType = XmlTokenType.XML_NAME; return; }
					case -20:
						break;
					case 20:
						{ yy_lexical_state = YYINITIAL; --yy_buffer_index; }
					case -21:
						break;
					case 21:
						{ myTokenType = XmlTokenType.XML_EQ; yy_lexical_state = ATTRIBUTE_VALUE_START; return; }
					case -22:
						break;
					case 22:
						{ myTokenType = XmlTokenType.XML_TAG_END; yy_lexical_state = YYINITIAL; return; }
					case -23:
						break;
					case 23:
						{ myTokenType = XmlTokenType.XML_EMPTY_ELEMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -24:
						break;
					case 24:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -25:
						break;
					case 25:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = ATTRIBUTE_VALUE_DQ; return; }
					case -26:
						break;
					case 26:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = ATTRIBUTE_VALUE_SQ; return; }
					case -27:
						break;
					case 27:
						{ myTokenType = XmlTokenType.XML_TAG_END; yy_lexical_state = YYINITIAL; return; }
					case -28:
						break;
					case 28:
						{ myTokenType = XmlTokenType.XML_EMPTY_ELEMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -29:
						break;
					case 29:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -30:
						break;
					case 30:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -31:
						break;
					case 31:
						{ myTokenType = elStartType; yy_lexical_state = EL_STATE3; return; }
					case -32:
						break;
					case 32:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -33:
						break;
					case 33:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -34:
						break;
					case 34:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -35:
						break;
					case 35:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -36:
						break;
					case 36:
						{ myTokenType = elStartType; yy_lexical_state = EL_STATE2; return; }
					case -37:
						break;
					case 37:
						{ myTokenType = XmlTokenType.XML_PI_TARGET; return; }
					case -38:
						break;
					case 38:
						{ myTokenType = XmlTokenType.XML_PI_END; yy_lexical_state = YYINITIAL; return; }
					case -39:
						break;
					case 39:
						{ myTokenType = elValueType; return; }
					case -40:
						break;
					case 40:
						{ myTokenType = elEndType; return; }
					case -41:
						break;
					case 41:
						{ myTokenType = elValueType; return; }
					case -42:
						break;
					case 42:
						{ myTokenType = elEndType; return; }
					case -43:
						break;
					case 43:
						{ myTokenType = elValueType; return; }
					case -44:
						break;
					case 44:
						{ myTokenType = elEndType; return; }
					case -45:
						break;
					case 45:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -46:
						break;
					case 46:
						{ myTokenType = XmlTokenType.XML_WHITE_SPACE; return; }
					case -47:
						break;
					case 48:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -48:
						break;
					case 49:
						{ myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
					case -49:
						break;
					case 50:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -50:
						break;
					case 51:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -51:
						break;
					case 52:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -52:
						break;
					case 53:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -53:
						break;
					case 54:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -54:
						break;
					case 55:
						{ myTokenType = XmlTokenType.XML_PI_TARGET; return; }
					case -55:
						break;
					case 56:
						{ myTokenType = elValueType; return; }
					case -56:
						break;
					case 57:
						{ myTokenType = elValueType; return; }
					case -57:
						break;
					case 58:
						{ myTokenType = elValueType; return; }
					case -58:
						break;
					case 59:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -59:
						break;
					case 60:
						{ myTokenType = XmlTokenType.XML_WHITE_SPACE; return; }
					case -60:
						break;
					case 62:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -61:
						break;
					case 63:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -62:
						break;
					case 64:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -63:
						break;
					case 65:
						{ myTokenType = XmlTokenType.XML_PI_TARGET; return; }
					case -64:
						break;
					case 66:
						{ myTokenType = elValueType; return; }
					case -65:
						break;
					case 68:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -66:
						break;
					case 70:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -67:
						break;
					case 72:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -68:
						break;
					case 74:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -69:
						break;
					case 76:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -70:
						break;
					case 78:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -71:
						break;
					case 80:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -72:
						break;
					default:
						yy_error(YY_E_INTERNAL,false);
					case -1:
					}
					yy_initial = true;
					yy_state = yy_state_dtrans[yy_lexical_state];
					yy_next_state = YY_NO_STATE;
					yy_last_accept_state = YY_NO_STATE;
		yy_buffer_start = yy_buffer_index;
					yy_this_accept = yy_acpt[yy_state];
					if (YY_NOT_ACCEPT != yy_this_accept) {
						yy_last_accept_state = yy_state;
					}
				}
			}
		}
	}
}
