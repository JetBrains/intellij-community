/* It's an automatically generated code. Do not modify it. */
package com.intellij.ide.fileTemplates.impl;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;


class FileTemplateTextLexer implements Lexer, Cloneable {
	private static final int YY_F = -1;
	private static final int YY_NO_STATE = -1;
	private static final int YY_NOT_ACCEPT = 0;
	private static final int YY_START = 1;
	private static final int YY_END = 2;
	private static final int YY_NO_ANCHOR = 4;
	private static final char YYEOF = '\uFFFF';

  private IElementType myTokenType;
  public final void start(char[] buffer){
    start(buffer, 0, buffer.length);
  }
  public final void start(char[] buffer, int startOffset, int endOffset){
    yy_buffer = buffer;
    yy_buffer_index = startOffset;
    yy_buffer_length = endOffset;
    myTokenType = null;
  }
  public final void start(char[] buffer, int startOffset, int endOffset, int initialState){
    start(buffer, startOffset, endOffset);
  }
  public final int getState(){
    return 0;
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
  public int getSmartUpdateShift() {
    return -1;
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

	public FileTemplateTextLexer () {
		yy_lexical_state = YYINITIAL;

    myTokenType = null;
	}

	private boolean yy_eof_done = false;
	public static final short YYINITIAL = 0;
	public static final short LAST_STATE = 1;
	private static final int yy_state_dtrans[] = {
		0
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
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR,
		YY_NO_ANCHOR,
		YY_NOT_ACCEPT,
		YY_NO_ANCHOR
	};
	private static final int yy_cmap[] = {
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 1, 2, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		3, 3, 3, 3, 3, 3, 3, 3,
		3, 3, 0, 0, 0, 0, 0, 0,
		0, 4, 4, 4, 4, 4, 4, 4,
		4, 4, 4, 4, 4, 4, 4, 4,
		4, 4, 4, 4, 4, 4, 4, 4,
		4, 4, 4, 0, 0, 0, 0, 4,
		0, 4, 4, 4, 4, 4, 4, 4,
		4, 4, 4, 4, 4, 4, 4, 4,
		4, 4, 4, 4, 4, 4, 4, 4,
		4, 4, 4, 5, 0, 6, 0, 0
		
	};
	private static final int yy_rmap[] = {
		0, 1, 2, 3, 4, 2, 1, 5,
		6 
	};
	private static final int yy_nxt[][] = unpackFromString(7,7,
"1,5,8,1:4,-1:11,2,-1:5,3:2,-1:5,7:2,-1:5,7:2,-1,6,-1:3,3:2,4,-1");
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
					case 1:
						{ myTokenType = FileTemplateTokenType.TEXT; return; }
					case -2:
						break;
					case 2:
						{ myTokenType = FileTemplateTokenType.DIRECTIVE; return; }
					case -3:
						break;
					case 3:
						{ myTokenType = FileTemplateTokenType.MACRO; return; }
					case -4:
						break;
					case 5:
						{ myTokenType = FileTemplateTokenType.TEXT; return; }
					case -5:
						break;
					case 6:
						{ myTokenType = FileTemplateTokenType.MACRO; return; }
					case -6:
						break;
					case 8:
						{ myTokenType = FileTemplateTokenType.TEXT; return; }
					case -7:
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
