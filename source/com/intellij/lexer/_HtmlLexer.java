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
	public static final short END_TAG_NAME = 4;
	public static final short START_TAG_NAME = 3;
	public static final short ATTRIBUTE_VALUE_SQ = 8;
	public static final short DOC_TYPE = 1;
	public static final short ATTRIBUTE_VALUE_DQ = 7;
	public static final short YYINITIAL = 0;
	public static final short TAG_ATTRIBUTES = 5;
	public static final short COMMENT = 2;
	public static final short ATTRIBUTE_VALUE_START = 6;
	public static final short LAST_STATE = 9;
	private static final int yy_state_dtrans[] = {
		0,
		60,
		12,
		72,
		73,
		74,
		22,
		27,
		31
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
		YY_NOT_ACCEPT,
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
		6, 6, 8, 0, 9, 10, 11, 0,
		0, 12, 13, 14, 15, 16, 12, 12,
		17, 18, 12, 12, 19, 20, 12, 21,
		22, 12, 12, 12, 23, 24, 12, 12,
		12, 25, 12, 0, 26, 0, 0, 8,
		0, 12, 13, 14, 15, 16, 12, 12,
		17, 18, 12, 12, 19, 20, 12, 21,
		22, 12, 12, 12, 23, 24, 12, 12,
		12, 25, 12, 0, 0, 0, 0, 0
		
	};
	private static final int yy_rmap[] = {
		0, 1, 2, 3, 4, 4, 4, 4,
		4, 4, 4, 4, 5, 4, 6, 4,
		4, 7, 4, 4, 4, 4, 8, 4,
		4, 4, 4, 9, 4, 4, 10, 11,
		4, 12, 13, 14, 15, 16, 17, 18,
		10, 19, 4, 19, 20, 21, 22, 23,
		24, 25, 26, 27, 28, 29, 30, 31,
		32, 33, 34, 35, 36, 15, 21, 37,
		38, 39, 40, 41, 42, 43, 44, 45,
		46, 47, 48 
	};
	private static final int yy_nxt[][] = unpackFromString(49,27,
"33,1,33:7,2,33:18,1,33:7,3,33:19,35,33:4,4,33:4,-1:14,33:3,44,33:4,-1,33:4,-1:14,33,-1:27,37:5,53,37:21,-1:5,14:2,-1,14,-1:3,14:14,-1:6,17:2,-1,17,-1:3,17:14,-1,39,34,39,23,24,39:2,55,39:3,25,39:15,40:3,28,40:22,46,40:3,-1,40:23,43:4,32,43:21,47,33:9,3,33:17,-1,34,-1:25,33:5,48,33:9,50,33:11,61:3,9,61:23,37:5,69,37:21,-1:11,21,-1:15,39,-1,39,-1:2,39:2,-1,39:3,-1,39:15,43:4,-1,43:22,33:5,52,33:9,-1,33:11,62:4,9,62:22,40:3,29,30,40:22,43:3,41,42,43:22,33:5,5,33:21,-1:23,63,-1:24,54,-1:29,64,-1:2,33:5,-1,33:21,37:5,70,37:21,-1:14,56,-1:23,26,-1:38,57,-1:28,58,-1:23,59,-1:20,6,-1:10,7,34,7,36,45,7:6,8,7:5,49,7:4,51,7:4,-1:20,65,-1:19,66,-1:32,10,-1:26,67,-1:25,68,-1:22,11,-1:12,37:5,71,37:32,13,37:26,-1,37:15,7:8,14,7:3,14:14,7:9,14,15,7:2,14:14,7,16,34,16:5,38,17,18,19,20,17:14,16");
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
						{ myTokenType = XmlTokenType.XML_COMMENT_START; yy_lexical_state = COMMENT; return; }
					case -6:
						break;
					case 6:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_START; yy_lexical_state = DOC_TYPE; return; }
					case -7:
						break;
					case 7:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -8:
						break;
					case 8:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_END; yy_lexical_state = YYINITIAL; return; }
					case -9:
						break;
					case 9:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
					case -10:
						break;
					case 10:
						{ myTokenType = XmlTokenType.XML_NAME;  return; }
					case -11:
						break;
					case 11:
						{ myTokenType = XmlTokenType.XML_DOCTYPE_PUBLIC;  return; }
					case -12:
						break;
					case 12:
						{ myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
					case -13:
						break;
					case 13:
						{ myTokenType = XmlTokenType.XML_COMMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -14:
						break;
					case 14:
						{ myTokenType = XmlTokenType.XML_NAME; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -15:
						break;
					case 15:
						{ yy_lexical_state = YYINITIAL; --yy_buffer_index; break; }
					case -16:
						break;
					case 16:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -17:
						break;
					case 17:
						{ myTokenType = XmlTokenType.XML_NAME; return; }
					case -18:
						break;
					case 18:
						{ yy_lexical_state = YYINITIAL; --yy_buffer_index; }
					case -19:
						break;
					case 19:
						{ myTokenType = XmlTokenType.XML_EQ; yy_lexical_state = ATTRIBUTE_VALUE_START; return; }
					case -20:
						break;
					case 20:
						{ myTokenType = XmlTokenType.XML_TAG_END; yy_lexical_state = YYINITIAL; return; }
					case -21:
						break;
					case 21:
						{ myTokenType = XmlTokenType.XML_EMPTY_ELEMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -22:
						break;
					case 22:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -23:
						break;
					case 23:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = ATTRIBUTE_VALUE_DQ; return; }
					case -24:
						break;
					case 24:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = ATTRIBUTE_VALUE_SQ; return; }
					case -25:
						break;
					case 25:
						{ myTokenType = XmlTokenType.XML_TAG_END; yy_lexical_state = YYINITIAL; return; }
					case -26:
						break;
					case 26:
						{ myTokenType = XmlTokenType.XML_EMPTY_ELEMENT_END; yy_lexical_state = YYINITIAL; return; }
					case -27:
						break;
					case 27:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -28:
						break;
					case 28:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -29:
						break;
					case 29:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
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
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -33:
						break;
					case 33:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -34:
						break;
					case 34:
						{ myTokenType = XmlTokenType.XML_WHITE_SPACE; return; }
					case -35:
						break;
					case 36:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -36:
						break;
					case 37:
						{ myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
					case -37:
						break;
					case 38:
						{ myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
					case -38:
						break;
					case 39:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; yy_lexical_state = TAG_ATTRIBUTES; return; }
					case -39:
						break;
					case 40:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -40:
						break;
					case 41:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
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
					case 45:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -44:
						break;
					case 46:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -45:
						break;
					case 47:
						{ myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
					case -46:
						break;
					case 49:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -47:
						break;
					case 51:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -48:
						break;
					case 53:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -49:
						break;
					case 55:
						{ myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
					case -50:
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
