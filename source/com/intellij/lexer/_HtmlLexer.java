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
  private IElementType elTokenType = XmlTokenType.XML_DATA_CHARACTERS;
  private IElementType elTokenType2 = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN;
  public void setElTypes(IElementType _elTokenType,IElementType _elTokenType2) {
    elTokenType = _elTokenType;
    elTokenType2 = _elTokenType2;
  }
  private int myState;
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
	public static final short END_TAG_NAME = 4;
	public static final short START_TAG_NAME = 3;
	public static final short ATTRIBUTE_VALUE_SQ = 8;
	public static final short DOC_TYPE = 1;
	public static final short ATTRIBUTE_VALUE_DQ = 7;
	public static final short YYINITIAL = 0;
	public static final short TAG_ATTRIBUTES = 5;
	public static final short COMMENT = 2;
	public static final short ATTRIBUTE_VALUE_START = 6;
	public static final short LAST_STATE = 10;
	private static final int yy_state_dtrans[] = {
		0,
		81,
		14,
		93,
		94,
		95,
		24,
		29,
		34,
		36
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
		YY_NOT_ACCEPT,
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
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
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
		2, 3, 4, 0, 5, 0, 6, 7,
		0, 0, 0, 0, 0, 8, 9, 10,
		9, 9, 9, 9, 9, 9, 9, 9,
		9, 9, 11, 0, 12, 13, 14, 15,
		0, 16, 17, 18, 19, 20, 16, 16,
		21, 22, 16, 16, 23, 24, 16, 25,
		26, 16, 16, 16, 27, 28, 16, 16,
		16, 29, 16, 0, 30, 0, 0, 11,
		0, 16, 17, 18, 19, 20, 16, 16,
		21, 22, 16, 16, 23, 24, 16, 25,
		26, 16, 16, 16, 27, 28, 16, 16,
		16, 29, 16, 31, 0, 32, 0, 0

	};
	private static final int yy_rmap[] = {
		0, 1, 2, 3, 4, 5, 5, 5,
		5, 5, 5, 5, 5, 5, 6, 5,
		7, 5, 5, 8, 5, 5, 5, 5,
		9, 5, 5, 5, 5, 10, 5, 5,
		11, 5, 12, 5, 13, 5, 14, 5,
		15, 16, 17, 18, 11, 19, 5, 19,
		20, 21, 22, 23, 24, 25, 26, 27,
		28, 29, 30, 31, 32, 33, 34, 35,
		36, 37, 38, 39, 40, 41, 42, 43,
		44, 45, 46, 47, 48, 49, 50, 51,
		52, 53, 22, 28, 54, 55, 56, 57,
		58, 59, 60, 61, 62, 63, 64, 65,
		66
	};
	private static final int yy_nxt[][] = unpackFromString(67,33,
"38,1:2,38:2,2,38:6,3,38:17,49,38:2,-1,1:2,-1:30,38:31,51,38:2,-1:2,57,38:6,5,38:4,6,-1:14,38:34,-1,38,-1:33,41:8,71,41:24,-1:8,16:2,-1,16,-1:4,16:14,-1:11,19:2,-1,19,-1:4,19:14,-1:3,43,1:2,43,25,43:2,26,43:2,73,43:3,27,43:18,44:4,30,52,39,44:23,58,44:6,-1:3,44:26,47:5,53,39,35,47:22,59,47:2,48,54,1,48:12,75,48:17,38,-1:2,38:2,4,38:6,40,38:17,49,38:3,-1:2,62,38:6,-1,38:4,-1:15,38:3,41:8,90,41:24,-1:14,23,-1:18,43,-1:2,43,-1,43:2,-1,43:2,-1,43:3,-1,43:18,47:5,-1:3,47:25,48:2,-1,48:12,-1,48:17,38,-1:2,38:2,55,38:6,40,38:17,49,38:2,82:4,11,82:28,51:32,7,63:4,-1,68,-1,63:24,96,63,64:5,69,-1:2,64:23,96,64,48,54,1,48:12,-1,48:17,38:5,55,38:6,60,38:17,49,38:2,83:7,11,83:25,38,-1:2,38:5,67,38:10,72,38:13,44:4,31,-1:2,32,44:25,47:4,45,-1:2,46,47:25,38,-1:2,65,38,55,38:6,60,38:17,49,38:2,-1:27,84,-1:5,38,-1:2,38:5,74,38:10,-1,38:13,-1:5,68,-1:32,69,-1:27,38,-1:2,38:2,55,38:2,70,38:3,60,38:17,49,38:2,-1:28,85,-1:4,38,-1:2,38:5,8,38:24,63:4,-1,68,-1,63:24,-1,63,64:5,69,-1:2,64:23,-1,64,38,-1:2,38:2,55,38:6,60,38:17,49,38:2,41:8,91,41:24,-1:25,76,-1:21,28,-1:18,38,-1:2,38:5,-1,38:24,-1:14,37,-1:36,77,-1:41,78,-1:34,79,-1:29,80,-1:26,9,-1:12,39,1:2,39,50,39:2,56,39:6,10,39:6,61,39:4,66,39:6,-1:24,86,-1:25,87,-1:38,12,-1:32,88,-1:31,89,-1:28,13,-1:14,41:8,92,41:38,15,41:32,-1,41:18,39:11,16,39:4,16:14,39:14,16,17,39:3,16:14,39:3,18,1:2,18:7,42,19,20,21,22,18,19:14,18:3,96:32,33");
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
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -4:
						break;
					case 3:
						{ myTokenType = XmlTokenType.XML_START_TAG_START; yy_lexical_state = START_TAG_NAME; return; }
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
						{
  myTokenType = elTokenType;
  return;
}
					case -8:
						break;
					case 8:
						{ myTokenType = XmlTokenType.XML_COMMENT_START; yy_lexical_state = COMMENT; return; }
					case -9:
						break;
					case 9:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_START; yy_lexical_state = DOC_TYPE; return; }
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
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -32:
						break;
					case 32:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -33:
						break;
					case 33:
						{
  myTokenType = elTokenType2;
  return;
}
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
						{ myTokenType = XmlTokenType.XML_PI_TARGET; return; }
					case -37:
						break;
					case 37:
						{ myTokenType = XmlTokenType.XML_PI_END; yy_lexical_state = YYINITIAL; return; }
					case -38:
						break;
					case 38:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -39:
						break;
					case 39:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -40:
						break;
					case 41:
						{ myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
					case -41:
						break;
					case 42:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -42:
						break;
					case 43:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -43:
						break;
					case 44:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -44:
						break;
					case 45:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -45:
						break;
					case 46:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -46:
						break;
					case 47:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -47:
						break;
					case 48:
						{ myTokenType = XmlTokenType.XML_PI_TARGET; return; }
					case -48:
						break;
					case 49:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -49:
						break;
					case 50:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -50:
						break;
					case 52:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -51:
						break;
					case 53:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -52:
						break;
					case 54:
						{ myTokenType = XmlTokenType.XML_PI_TARGET; return; }
					case -53:
						break;
					case 55:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -54:
						break;
					case 56:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -55:
						break;
					case 58:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -56:
						break;
					case 59:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -57:
						break;
					case 60:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -58:
						break;
					case 61:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -59:
						break;
					case 63:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -60:
						break;
					case 64:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -61:
						break;
					case 65:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -62:
						break;
					case 66:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -63:
						break;
					case 68:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -64:
						break;
					case 69:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -65:
						break;
					case 70:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -66:
						break;
					case 71:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -67:
						break;
					case 73:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -68:
						break;
					case 75:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -69:
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
