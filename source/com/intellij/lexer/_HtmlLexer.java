/* It's an automatically generated code. Do not modify it. */
package com.intellij.lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;


public class _HtmlLexer implements Lexer, Cloneable {
	private static final int YY_F = -1;
	private static final int YY_NO_STATE = -1;
	private static final int YY_NOT_ACCEPT = 0;
	private static final int YY_START = 1;
	private static final int YY_END = 2;
	private static final int YY_NO_ANCHOR = 4;
	private static final char YYEOF = '\uFFFF';

  private IElementType myTokenType;
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
		64,
		13,
		76,
		77,
		78,
		23,
		28,
		32,
		34
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
		YY_NOT_ACCEPT,
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
		1, 2, 3, 0, 0, 0, 0, 4,
		0, 0, 0, 0, 0, 5, 6, 7,
		6, 6, 6, 6, 6, 6, 6, 6,
		6, 6, 8, 0, 9, 10, 11, 12,
		0, 13, 14, 15, 16, 17, 13, 13,
		18, 19, 13, 13, 20, 21, 13, 22,
		23, 13, 13, 13, 24, 25, 13, 13,
		13, 26, 13, 0, 27, 0, 0, 8,
		0, 13, 14, 15, 16, 17, 13, 13,
		18, 19, 13, 13, 20, 21, 13, 22,
		23, 13, 13, 13, 24, 25, 13, 13,
		13, 26, 13, 0, 0, 0, 0, 0

	};
	private static final int yy_rmap[] = {
		0, 1, 2, 3, 4, 4, 4, 4,
		4, 4, 4, 4, 4, 5, 4, 6,
		4, 4, 7, 4, 4, 4, 4, 8,
		4, 4, 4, 4, 9, 4, 4, 10,
		11, 4, 12, 4, 13, 14, 15, 16,
		17, 18, 10, 19, 4, 19, 20, 21,
		22, 23, 24, 25, 26, 27, 28, 29,
		30, 31, 32, 33, 34, 35, 36, 37,
		38, 15, 22, 39, 40, 41, 42, 43,
		44, 45, 46, 47, 48, 49, 50
	};
	private static final int yy_nxt[][] = unpackFromString(51,28,
"36,1,36:7,2,36:18,-1,1,-1:26,36,-1,37,36:4,4,36:4,5,-1:14,36:2,-1,47,36:4,-1,36:4,-1:15,36,-1:28,39:5,56,39:22,-1:5,15:2,-1,15,-1:4,15:14,-1:6,18:2,-1,18,-1:4,18:14,-1,41,1,41,24,25,41:2,58,41:3,26,41:16,42:3,29,42:23,49,42:3,-1,42:24,45:4,33,45:22,50,46:12,60,46:15,36,-1,36:7,3,36:19,-1,36:3,51,36:10,53,36:11,65:3,10,65:24,39:5,73,39:22,-1:11,22,-1:16,41,-1,41,-1:2,41:2,-1,41:3,-1,41:16,45:4,-1,45:23,46:12,-1,46:15,36,-1,36:3,55,36:10,-1,36:11,66:4,10,66:23,42:3,30,31,42:23,45:3,43,44,45:23,36,-1,36:3,6,36:22,-1:24,67,-1:25,57,-1:30,68,-1:2,36,-1,36:3,-1,36:22,39:5,74,39:22,-1:15,59,-1:23,27,-1:40,61,-1:14,35,-1:42,62,-1:24,63,-1:21,7,-1:10,8,1,8,38,48,8:6,9,8:6,52,8:4,54,8:4,-1:21,69,-1:20,70,-1:33,11,-1:27,71,-1:26,72,-1:23,12,-1:12,39:5,75,39:33,14,39:27,-1,39:16,8:8,15,8:4,15:14,8:9,15,16,8:3,15:14,8,17,1,17:5,40,18,19,20,21,17,18:14,17");
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
						{ myTokenType = XmlTokenType.XML_END_TAG_START; yy_lexical_state = END_TAG_NAME; return; }
					case -5:
						break;
					case 5:
						{ myTokenType = XmlTokenType.XML_PI_START; yy_lexical_state = PROCESSING_INSTRUCTION; return; }
					case -6:
						break;
					case 6:
						{ myTokenType = XmlTokenType.XML_COMMENT_START; yy_lexical_state = COMMENT; return; }
					case -7:
						break;
					case 7:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_START; yy_lexical_state = DOC_TYPE; return; }
					case -8:
						break;
					case 8:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -9:
						break;
					case 9:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_END; yy_lexical_state = YYINITIAL; return; }
					case -10:
						break;
					case 10:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
					case -11:
						break;
					case 11:
						{ myTokenType = XmlTokenType.XML_NAME;  return; }
					case -12:
						break;
					case 12:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_PUBLIC;  return; }
					case -13:
						break;
					case 13:
						{ myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
					case -14:
						break;
					case 14:
						{ myTokenType = XmlTokenType.XML_COMMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -15:
						break;
					case 15:
						{ myTokenType = XmlTokenType.XML_NAME; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -16:
						break;
					case 16:
						{ yy_lexical_state = YYINITIAL; --yy_buffer_index; break; }
					case -17:
						break;
					case 17:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -18:
						break;
					case 18:
						{ myTokenType = XmlTokenType.XML_NAME; return; }
					case -19:
						break;
					case 19:
						{ yy_lexical_state = YYINITIAL; --yy_buffer_index; }
					case -20:
						break;
					case 20:
						{ myTokenType = XmlTokenType.XML_EQ; yy_lexical_state = ATTRIBUTE_VALUE_START; return; }
					case -21:
						break;
					case 21:
						{ myTokenType = XmlTokenType.XML_TAG_END; yy_lexical_state = YYINITIAL; return; }
					case -22:
						break;
					case 22:
						{ myTokenType = XmlTokenType.XML_EMPTY_ELEMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -23:
						break;
					case 23:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -24:
						break;
					case 24:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = ATTRIBUTE_VALUE_DQ; return; }
					case -25:
						break;
					case 25:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = ATTRIBUTE_VALUE_SQ; return; }
					case -26:
						break;
					case 26:
						{ myTokenType = XmlTokenType.XML_TAG_END; yy_lexical_state = YYINITIAL; return; }
					case -27:
						break;
					case 27:
						{ myTokenType = XmlTokenType.XML_EMPTY_ELEMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -28:
						break;
					case 28:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -29:
						break;
					case 29:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -30:
						break;
					case 30:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
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
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -34:
						break;
					case 34:
						{ myTokenType = XmlTokenType.XML_PI_TARGET; return; }
					case -35:
						break;
					case 35:
						{ myTokenType = XmlTokenType.XML_PI_END; yy_lexical_state = YYINITIAL; return; }
					case -36:
						break;
					case 36:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -37:
						break;
					case 38:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -38:
						break;
					case 39:
						{ myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
					case -39:
						break;
					case 40:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -40:
						break;
					case 41:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -41:
						break;
					case 42:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -42:
						break;
					case 43:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
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
						{ myTokenType = XmlTokenType.XML_PI_TARGET; return; }
					case -46:
						break;
					case 48:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -47:
						break;
					case 49:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -48:
						break;
					case 50:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -49:
						break;
					case 52:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -50:
						break;
					case 54:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -51:
						break;
					case 56:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -52:
						break;
					case 58:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -53:
						break;
					case 60:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -54:
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
