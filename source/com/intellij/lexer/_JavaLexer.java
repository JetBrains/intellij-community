/* It's an automatically generated code. Do not modify it. */
package com.intellij.lexer;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;


class _JavaLexer implements Lexer, Cloneable {
	private static final int YY_F = -1;
	private static final int YY_NO_STATE = -1;
	private static final int YY_NOT_ACCEPT = 0;
	private static final int YY_START = 1;
	private static final int YY_END = 2;
	private static final int YY_NO_ANCHOR = 4;
	private static final char YYEOF = '\uFFFF';

  private IElementType myTokenType;
  private boolean myAssertKeywordEnabled;
  private boolean myJdk15Enabled;
  public _JavaLexer(boolean isAssertKeywordEnabled, boolean jdk15Enabled){
    myAssertKeywordEnabled = isAssertKeywordEnabled;
    myJdk15Enabled = jdk15Enabled;
  }
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
    return 0;
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
    return 1;
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

	public _JavaLexer () {
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
		YY_NOT_ACCEPT,
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
		YY_NO_ANCHOR
	};
	private static final int yy_cmap[] = {
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 1, 2, 0, 1, 2, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0,
		1, 3, 4, 0, 5, 6, 7, 8,
		9, 10, 11, 12, 13, 14, 15, 16,
		17, 18, 18, 18, 18, 18, 18, 18,
		19, 19, 20, 21, 22, 23, 24, 25,
		26, 27, 27, 27, 28, 29, 30, 5,
		5, 5, 5, 5, 31, 5, 5, 5,
		5, 5, 5, 5, 5, 5, 5, 5,
		32, 5, 5, 33, 34, 35, 36, 5,
		0, 37, 38, 39, 40, 41, 42, 43,
		44, 45, 5, 46, 47, 48, 49, 50,
		51, 5, 52, 53, 54, 55, 56, 57,
		58, 59, 60, 61, 62, 63, 64, 0
		
	};
	private static final int yy_rmap[] = {
		0, 1, 2, 3, 4, 5, 6, 7,
		8, 1, 1, 9, 10, 1, 11, 12,
		13, 14, 1, 1, 15, 16, 1, 1,
		1, 1, 1, 17, 1, 18, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1,
		1, 19, 20, 21, 1, 1, 1, 22,
		1, 1, 1, 23, 5, 1, 1, 1,
		24, 1, 5, 25, 5, 5, 5, 5,
		5, 5, 5, 5, 5, 5, 5, 5,
		5, 5, 5, 5, 5, 5, 26, 5,
		5, 5, 27, 5, 5, 5, 5, 5,
		5, 5, 5, 5, 5, 5, 5, 5,
		5, 5, 5, 5, 5, 5, 5, 5,
		5, 5, 5, 5, 5, 28, 1, 29,
		1, 30, 1, 31, 32, 33, 34, 35,
		36, 37, 38, 1, 1, 39, 40, 41,
		42, 43, 44, 45, 46, 47, 48, 49,
		50, 51, 52, 53, 54, 55, 56, 57,
		58, 59, 60, 61, 62, 63, 64, 65,
		66, 67, 68, 69, 70, 71, 72, 73,
		74, 75, 76, 77, 78, 79, 80, 81,
		82, 83, 84, 85, 86, 87, 88, 89,
		90, 91, 92, 93, 94, 42, 95, 96,
		97, 98, 99, 100, 101, 102, 103, 104,
		105, 106, 107, 108, 109, 110, 111, 112,
		113, 114, 115, 116, 117, 118, 119, 120,
		121, 122, 123, 124, 125, 126, 127, 128,
		129, 130, 131, 132, 133, 134, 135, 136,
		137, 138, 139, 140, 141, 142, 143, 144,
		145, 146, 147, 148, 149, 150, 151, 152,
		153, 154, 155, 156, 157, 158, 159, 160,
		161, 162, 163, 164, 165, 166, 167, 168,
		169, 170, 171, 172, 173, 174, 175, 176,
		177, 178, 179, 180, 181, 182, 183, 184,
		185, 186, 187, 188, 189, 190, 191, 192,
		193, 194, 195, 196, 197, 198, 199, 200,
		201, 202, 203, 204, 205, 206, 207, 208,
		209, 210, 211, 212, 213, 214, 215, 216,
		217, 218, 219, 220, 221, 222, 223, 224,
		225, 226, 227, 228, 229, 230 
	};
	private static final int yy_nxt[][] = unpackFromString(231,65,
"1,2:2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,113:2,18,19,20,21,22,23,24,5:6,25,1,26,27,290,228,229,111,230,180,231,5,119,5,232,5,182,5,291,292,266,183,5,233,267,5:3,28,29,30,31,-1:66,2:2,-1:85,32,-1:41,4:2,-1,4,110,4:29,118,4:30,-1:5,5,-1:11,5:3,-1:7,5:6,-1:4,5:24,-1:27,33,-1:48,34,-1:15,35,-1:41,8:2,-1,8:5,112,8:25,120,8:30,-1:23,36,-1:53,37,-1:10,38,-1:55,39,-1:8,40,-1:56,109,-1,41:3,-1:56,42,-1:4,43,-1:6,44,-1:56,181,-1,121:2,117,-1:8,114,122,45,46,127,-1:7,114,122,45,-1:4,46,-1:10,127,-1:28,47,48,-1:64,49,-1:64,50,-1:64,53,-1:38,54,-1:19,41:3,-1:8,114,122,45,-1:9,114,122,45,-1:22,115:11,56,115:53,43:2,-1,43:62,-1:23,57,-1:46,5,-1:11,5:3,-1:7,5:6,-1:4,5:18,244,5:5,-1:4,116:11,56,116:4,124,116:48,-1:5,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,301,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,167,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,92,5:7,-1:19,55,-1:54,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,294,5:8,51,5:10,-1:19,181,-1,113:3,-1:8,114,122,45,46,-1:8,114,122,45,-1:4,46,-1:17,115:11,125,115:53,116:11,129,116:53,-1:15,181,-1,117:3,-1:8,114,122,45,-1:9,114,122,45,-1:22,4:2,-1,4:62,-1:5,5,-1:11,5:3,-1:7,5:6,-1:4,5:5,52,5:5,269,130,5:11,-1:4,8:2,-1,8:62,-1:15,181,-1,121:2,117,-1:8,114,122,45,46,-1:8,114,122,45,-1:4,46,-1:29,131,-1,131,-1:2,131:3,-1:8,114,-1,45,-1:9,114,-1,45,-1:22,115:11,125,115:4,123,115:48,-1:5,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,58,5:8,-1:21,127:3,-1:7,127:4,46,-1:5,127:6,-1:4,46,-1:34,128:3,-1:8,114,122,45,-1:9,114,122,45,-1:22,116:11,129,116:4,124,116:48,-1:5,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,313,59,5:6,-1:21,131:3,-1:8,114,-1,45,-1:9,114,-1,45,-1:27,5,-1:11,5:3,-1:7,5:6,-1:4,5:20,60,5:3,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,309,5:17,143,5:3,61,5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,62,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,63,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,64,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,65,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:11,66,5:12,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,67,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:6,68,5:17,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,69,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,70,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,71,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:3,72,5:20,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:9,73,5:14,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:7,74,5:16,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,75,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,76,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,77,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,78,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,79,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,80,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,81,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:20,82,5:3,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,83,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,84,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,85,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,86,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,87,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,88,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,89,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,90,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:7,91,5:16,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,93,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,94,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,95,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:22,96,5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,97,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,98,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,99,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,100,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:14,101,5:9,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,102,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,103,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:3,104,5:20,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,105,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,106,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:5,107,5:18,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:3,108,5:20,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,237,5:7,238,5,239,5:2,126,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,270,5:3,132,5:13,191,5:5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:7,192,5:7,133,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,134,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,135,195,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,136,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,137,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:18,138,5:5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,139,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,140,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,141,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,142,5:6,203,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,144,5,300,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,145,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,146,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,147,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,148,281,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,149,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,150,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,151,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,152,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,153,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,154,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,155,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,156,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,157,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,158,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:19,159,5:4,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,160,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,161,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,162,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,163,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,164,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,165,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:3,166,5:20,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:6,168,5:17,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,169,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,170,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:18,171,5:5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:5,172,5:18,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,173,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,174,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,175,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,176,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,177,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,178,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,179,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,293,5,234,5:6,184,5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,185,5:6,186,5:2,235,5:2,236,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,187,5,188,5:8,295,5:2,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,189,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,190,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,193,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,194,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,196,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,197,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,198,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,199,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,200,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,201,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:14,202,5:9,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,204,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,205,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5,206,5:22,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,310,5:2,207,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,208,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,209,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:18,210,5:5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,211,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,212,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,213,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:18,214,5:5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,215,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,216,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,217,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,218,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,219,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,220,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,221,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,222,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,223,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,224,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,225,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,226,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:23,227,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:7,240,5:9,273,241,5,274,5,317,5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:7,242,5:16,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,243,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:14,245,5:9,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,246,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5,247,5:22,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,248,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,249,5:14,299,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,250,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:10,251,5:13,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,252,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,253,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:9,254,5:14,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:19,255,5:4,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,256,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,257,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,258,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,259,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:5,260,5:18,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,261,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,262,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,263,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,264,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,265,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5,307,5:14,268,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,296,5:14,297,5:2,271,5:5,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,272,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,275,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:5,276,5:18,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,277,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,278,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,279,5:4,308,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,280,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:8,282,5:15,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,283,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,284,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,285,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,286,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:11,287,5:12,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,288,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,289,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:16,298,5:7,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,302,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,303,5:11,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:4,304,5:19,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,305,5:23,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:13,306,5:10,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:17,311,5:6,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:15,312,5:8,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:7,314,5:16,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:2,315,5:21,-1:9,5,-1:11,5:3,-1:7,5:6,-1:4,5:12,316,5:11,-1:4");
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
						{ myTokenType = JavaTokenType.BAD_CHARACTER; return; }
					case -2:
						break;
					case 2:
						{ myTokenType = JavaTokenType.WHITE_SPACE; return; }
					case -3:
						break;
					case 3:
						{ myTokenType = JavaTokenType.EXCL; return; }
					case -4:
						break;
					case 4:
						{ myTokenType = JavaTokenType.STRING_LITERAL; return; }
					case -5:
						break;
					case 5:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -6:
						break;
					case 6:
						{ myTokenType = JavaTokenType.PERC; return; }
					case -7:
						break;
					case 7:
						{ myTokenType = JavaTokenType.AND; return; }
					case -8:
						break;
					case 8:
						{ myTokenType = JavaTokenType.CHARACTER_LITERAL; return; }
					case -9:
						break;
					case 9:
						{ myTokenType = JavaTokenType.LPARENTH; return; }
					case -10:
						break;
					case 10:
						{ myTokenType = JavaTokenType.RPARENTH; return; }
					case -11:
						break;
					case 11:
						{ myTokenType = JavaTokenType.ASTERISK; return; }
					case -12:
						break;
					case 12:
						{ myTokenType = JavaTokenType.PLUS; return; }
					case -13:
						break;
					case 13:
						{ myTokenType = JavaTokenType.COMMA; return; }
					case -14:
						break;
					case 14:
						{ myTokenType = JavaTokenType.MINUS; return; }
					case -15:
						break;
					case 15:
						{ myTokenType = JavaTokenType.DOT; return; }
					case -16:
						break;
					case 16:
						{ myTokenType = JavaTokenType.DIV; return; }
					case -17:
						break;
					case 17:
						{ myTokenType = JavaTokenType.INTEGER_LITERAL; return; }
					case -18:
						break;
					case 18:
						{ myTokenType = JavaTokenType.COLON; return; }
					case -19:
						break;
					case 19:
						{ myTokenType = JavaTokenType.SEMICOLON; return; }
					case -20:
						break;
					case 20:
						{ myTokenType = JavaTokenType.LT; return; }
					case -21:
						break;
					case 21:
						{ myTokenType = JavaTokenType.EQ; return; }
					case -22:
						break;
					case 22:
						{ myTokenType = JavaTokenType.GT; return; }
					case -23:
						break;
					case 23:
						{ myTokenType = JavaTokenType.QUEST; return; }
					case -24:
						break;
					case 24:
						{ myTokenType = JavaTokenType.AT; return; }
					case -25:
						break;
					case 25:
						{ myTokenType = JavaTokenType.LBRACKET; return; }
					case -26:
						break;
					case 26:
						{ myTokenType = JavaTokenType.RBRACKET; return; }
					case -27:
						break;
					case 27:
						{ myTokenType = JavaTokenType.XOR; return; }
					case -28:
						break;
					case 28:
						{ myTokenType = JavaTokenType.LBRACE; return; }
					case -29:
						break;
					case 29:
						{ myTokenType = JavaTokenType.OR; return; }
					case -30:
						break;
					case 30:
						{ myTokenType = JavaTokenType.RBRACE; return; }
					case -31:
						break;
					case 31:
						{ myTokenType = JavaTokenType.TILDE; return; }
					case -32:
						break;
					case 32:
						{ myTokenType = JavaTokenType.NE; return; }
					case -33:
						break;
					case 33:
						{ myTokenType = JavaTokenType.PERCEQ; return; }
					case -34:
						break;
					case 34:
						{ myTokenType = JavaTokenType.ANDAND; return; }
					case -35:
						break;
					case 35:
						{ myTokenType = JavaTokenType.ANDEQ; return; }
					case -36:
						break;
					case 36:
						{ myTokenType = JavaTokenType.ASTERISKEQ; return; }
					case -37:
						break;
					case 37:
						{ myTokenType = JavaTokenType.PLUSPLUS; return; }
					case -38:
						break;
					case 38:
						{ myTokenType = JavaTokenType.PLUSEQ; return; }
					case -39:
						break;
					case 39:
						{ myTokenType = JavaTokenType.MINUSMINUS; return; }
					case -40:
						break;
					case 40:
						{ myTokenType = JavaTokenType.MINUSEQ; return; }
					case -41:
						break;
					case 41:
						{ myTokenType = JavaTokenType.DOUBLE_LITERAL; return; }
					case -42:
						break;
					case 42:
						{ myTokenType = JavaTokenType.C_STYLE_COMMENT; return; }
					case -43:
						break;
					case 43:
						{ myTokenType = JavaTokenType.END_OF_LINE_COMMENT; return; }
					case -44:
						break;
					case 44:
						{ myTokenType = JavaTokenType.DIVEQ; return; }
					case -45:
						break;
					case 45:
						{ myTokenType = JavaTokenType.FLOAT_LITERAL; return; }
					case -46:
						break;
					case 46:
						{ myTokenType = JavaTokenType.LONG_LITERAL; return; }
					case -47:
						break;
					case 47:
						{ myTokenType = JavaTokenType.LTLT; return; }
					case -48:
						break;
					case 48:
						{ myTokenType = JavaTokenType.LE; return; }
					case -49:
						break;
					case 49:
						{ myTokenType = JavaTokenType.EQEQ; return; }
					case -50:
						break;
					case 50:
						{ myTokenType = JavaTokenType.XOREQ; return; }
					case -51:
						break;
					case 51:
						{ myTokenType = JavaTokenType.DO_KEYWORD; return; }
					case -52:
						break;
					case 52:
						{ myTokenType = JavaTokenType.IF_KEYWORD; return; }
					case -53:
						break;
					case 53:
						{ myTokenType = JavaTokenType.OREQ; return; }
					case -54:
						break;
					case 54:
						{ myTokenType = JavaTokenType.OROR; return; }
					case -55:
						break;
					case 55:
						{ myTokenType = JavaTokenType.ELLIPSIS; return; }
					case -56:
						break;
					case 56:
						{ myTokenType = JavaTokenType.DOC_COMMENT; return; }
					case -57:
						break;
					case 57:
						{ myTokenType = JavaTokenType.LTLTEQ; return; }
					case -58:
						break;
					case 58:
						{ myTokenType = JavaTokenType.FOR_KEYWORD; return; }
					case -59:
						break;
					case 59:
						{ myTokenType = JavaTokenType.INT_KEYWORD; return; }
					case -60:
						break;
					case 60:
						{ myTokenType = JavaTokenType.NEW_KEYWORD; return; }
					case -61:
						break;
					case 61:
						{ myTokenType = JavaTokenType.TRY_KEYWORD; return; }
					case -62:
						break;
					case 62:
						{ myTokenType = JavaTokenType.BYTE_KEYWORD; return; }
					case -63:
						break;
					case 63:
						{ myTokenType = JavaTokenType.CASE_KEYWORD; return; }
					case -64:
						break;
					case 64:
						{ myTokenType = JavaTokenType.CHAR_KEYWORD; return; }
					case -65:
						break;
					case 65:
						{ myTokenType = JavaTokenType.ELSE_KEYWORD; return; }
					case -66:
						break;
					case 66:
						{ myTokenType = myJdk15Enabled ? JavaTokenType.ENUM_KEYWORD : JavaTokenType.IDENTIFIER; return; }
					case -67:
						break;
					case 67:
						{ myTokenType = JavaTokenType.GOTO_KEYWORD; return; }
					case -68:
						break;
					case 68:
						{ myTokenType = JavaTokenType.LONG_KEYWORD; return; }
					case -69:
						break;
					case 69:
						{ myTokenType = JavaTokenType.NULL_KEYWORD; return; }
					case -70:
						break;
					case 70:
						{ myTokenType = JavaTokenType.THIS_KEYWORD; return; }
					case -71:
						break;
					case 71:
						{ myTokenType = JavaTokenType.TRUE_KEYWORD; return; }
					case -72:
						break;
					case 72:
						{ myTokenType = JavaTokenType.VOID_KEYWORD; return; }
					case -73:
						break;
					case 73:
						{ myTokenType = JavaTokenType.BREAK_KEYWORD; return; }
					case -74:
						break;
					case 74:
						{ myTokenType = JavaTokenType.CATCH_KEYWORD; return; }
					case -75:
						break;
					case 75:
						{ myTokenType = JavaTokenType.CLASS_KEYWORD; return; }
					case -76:
						break;
					case 76:
						{ myTokenType = JavaTokenType.CONST_KEYWORD; return; }
					case -77:
						break;
					case 77:
						{ myTokenType = JavaTokenType.FALSE_KEYWORD; return; }
					case -78:
						break;
					case 78:
						{ myTokenType = JavaTokenType.FINAL_KEYWORD; return; }
					case -79:
						break;
					case 79:
						{ myTokenType = JavaTokenType.FLOAT_KEYWORD; return; }
					case -80:
						break;
					case 80:
						{ myTokenType = JavaTokenType.SHORT_KEYWORD; return; }
					case -81:
						break;
					case 81:
						{ myTokenType = JavaTokenType.SUPER_KEYWORD; return; }
					case -82:
						break;
					case 82:
						{ myTokenType = JavaTokenType.THROW_KEYWORD; return; }
					case -83:
						break;
					case 83:
						{ myTokenType = JavaTokenType.WHILE_KEYWORD; return; }
					case -84:
						break;
					case 84:
						{ myTokenType = myAssertKeywordEnabled ? JavaTokenType.ASSERT_KEYWORD : JavaTokenType.IDENTIFIER; return; }
					case -85:
						break;
					case 85:
						{ myTokenType = JavaTokenType.DOUBLE_KEYWORD; return; }
					case -86:
						break;
					case 86:
						{ myTokenType = JavaTokenType.IMPORT_KEYWORD; return; }
					case -87:
						break;
					case 87:
						{ myTokenType = JavaTokenType.NATIVE_KEYWORD; return; }
					case -88:
						break;
					case 88:
						{ myTokenType = JavaTokenType.PUBLIC_KEYWORD; return; }
					case -89:
						break;
					case 89:
						{ myTokenType = JavaTokenType.RETURN_KEYWORD; return; }
					case -90:
						break;
					case 90:
						{ myTokenType = JavaTokenType.STATIC_KEYWORD; return; }
					case -91:
						break;
					case 91:
						{ myTokenType = JavaTokenType.SWITCH_KEYWORD; return; }
					case -92:
						break;
					case 92:
						{ myTokenType = JavaTokenType.THROWS_KEYWORD; return; }
					case -93:
						break;
					case 93:
						{ myTokenType = JavaTokenType.BOOLEAN_KEYWORD; return; }
					case -94:
						break;
					case 94:
						{ myTokenType = JavaTokenType.DEFAULT_KEYWORD; return; }
					case -95:
						break;
					case 95:
						{ myTokenType = JavaTokenType.EXTENDS_KEYWORD; return; }
					case -96:
						break;
					case 96:
						{ myTokenType = JavaTokenType.FINALLY_KEYWORD; return; }
					case -97:
						break;
					case 97:
						{ myTokenType = JavaTokenType.PACKAGE_KEYWORD; return; }
					case -98:
						break;
					case 98:
						{ myTokenType = JavaTokenType.PRIVATE_KEYWORD; return; }
					case -99:
						break;
					case 99:
						{ myTokenType = JavaTokenType.ABSTRACT_KEYWORD; return; }
					case -100:
						break;
					case 100:
						{ myTokenType = JavaTokenType.CONTINUE_KEYWORD; return; }
					case -101:
						break;
					case 101:
						{ myTokenType = JavaTokenType.STRICTFP_KEYWORD; return; }
					case -102:
						break;
					case 102:
						{ myTokenType = JavaTokenType.VOLATILE_KEYWORD; return; }
					case -103:
						break;
					case 103:
						{ myTokenType = JavaTokenType.INTERFACE_KEYWORD; return; }
					case -104:
						break;
					case 104:
						{ myTokenType = JavaTokenType.PROTECTED_KEYWORD; return; }
					case -105:
						break;
					case 105:
						{ myTokenType = JavaTokenType.TRANSIENT_KEYWORD; return; }
					case -106:
						break;
					case 106:
						{ myTokenType = JavaTokenType.IMPLEMENTS_KEYWORD; return; }
					case -107:
						break;
					case 107:
						{ myTokenType = JavaTokenType.INSTANCEOF_KEYWORD; return; }
					case -108:
						break;
					case 108:
						{ myTokenType = JavaTokenType.SYNCHRONIZED_KEYWORD; return; }
					case -109:
						break;
					case 110:
						{ myTokenType = JavaTokenType.STRING_LITERAL; return; }
					case -110:
						break;
					case 111:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -111:
						break;
					case 112:
						{ myTokenType = JavaTokenType.CHARACTER_LITERAL; return; }
					case -112:
						break;
					case 113:
						{ myTokenType = JavaTokenType.INTEGER_LITERAL; return; }
					case -113:
						break;
					case 114:
						{ myTokenType = JavaTokenType.DOUBLE_LITERAL; return; }
					case -114:
						break;
					case 115:
						{ myTokenType = JavaTokenType.C_STYLE_COMMENT; return; }
					case -115:
						break;
					case 116:
						{ myTokenType = JavaTokenType.DOC_COMMENT; return; }
					case -116:
						break;
					case 118:
						{ myTokenType = JavaTokenType.STRING_LITERAL; return; }
					case -117:
						break;
					case 119:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -118:
						break;
					case 120:
						{ myTokenType = JavaTokenType.CHARACTER_LITERAL; return; }
					case -119:
						break;
					case 121:
						{ myTokenType = JavaTokenType.INTEGER_LITERAL; return; }
					case -120:
						break;
					case 122:
						{ myTokenType = JavaTokenType.DOUBLE_LITERAL; return; }
					case -121:
						break;
					case 123:
						{ myTokenType = JavaTokenType.C_STYLE_COMMENT; return; }
					case -122:
						break;
					case 124:
						{ myTokenType = JavaTokenType.DOC_COMMENT; return; }
					case -123:
						break;
					case 126:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -124:
						break;
					case 127:
						{ myTokenType = JavaTokenType.INTEGER_LITERAL; return; }
					case -125:
						break;
					case 128:
						{ myTokenType = JavaTokenType.DOUBLE_LITERAL; return; }
					case -126:
						break;
					case 130:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -127:
						break;
					case 131:
						{ myTokenType = JavaTokenType.DOUBLE_LITERAL; return; }
					case -128:
						break;
					case 132:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -129:
						break;
					case 133:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -130:
						break;
					case 134:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -131:
						break;
					case 135:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -132:
						break;
					case 136:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -133:
						break;
					case 137:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -134:
						break;
					case 138:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -135:
						break;
					case 139:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -136:
						break;
					case 140:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -137:
						break;
					case 141:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -138:
						break;
					case 142:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -139:
						break;
					case 143:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -140:
						break;
					case 144:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -141:
						break;
					case 145:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -142:
						break;
					case 146:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -143:
						break;
					case 147:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -144:
						break;
					case 148:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -145:
						break;
					case 149:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -146:
						break;
					case 150:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -147:
						break;
					case 151:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -148:
						break;
					case 152:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -149:
						break;
					case 153:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -150:
						break;
					case 154:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -151:
						break;
					case 155:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -152:
						break;
					case 156:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -153:
						break;
					case 157:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -154:
						break;
					case 158:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -155:
						break;
					case 159:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -156:
						break;
					case 160:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -157:
						break;
					case 161:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -158:
						break;
					case 162:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -159:
						break;
					case 163:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -160:
						break;
					case 164:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -161:
						break;
					case 165:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -162:
						break;
					case 166:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -163:
						break;
					case 167:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -164:
						break;
					case 168:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -165:
						break;
					case 169:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -166:
						break;
					case 170:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -167:
						break;
					case 171:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -168:
						break;
					case 172:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -169:
						break;
					case 173:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -170:
						break;
					case 174:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -171:
						break;
					case 175:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -172:
						break;
					case 176:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -173:
						break;
					case 177:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -174:
						break;
					case 178:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -175:
						break;
					case 179:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -176:
						break;
					case 180:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -177:
						break;
					case 181:
						{ myTokenType = JavaTokenType.DOUBLE_LITERAL; return; }
					case -178:
						break;
					case 182:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -179:
						break;
					case 183:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -180:
						break;
					case 184:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -181:
						break;
					case 185:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -182:
						break;
					case 186:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -183:
						break;
					case 187:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -184:
						break;
					case 188:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -185:
						break;
					case 189:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -186:
						break;
					case 190:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -187:
						break;
					case 191:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -188:
						break;
					case 192:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -189:
						break;
					case 193:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -190:
						break;
					case 194:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -191:
						break;
					case 195:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -192:
						break;
					case 196:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -193:
						break;
					case 197:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -194:
						break;
					case 198:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -195:
						break;
					case 199:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -196:
						break;
					case 200:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -197:
						break;
					case 201:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -198:
						break;
					case 202:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -199:
						break;
					case 203:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -200:
						break;
					case 204:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -201:
						break;
					case 205:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -202:
						break;
					case 206:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -203:
						break;
					case 207:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -204:
						break;
					case 208:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -205:
						break;
					case 209:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -206:
						break;
					case 210:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -207:
						break;
					case 211:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -208:
						break;
					case 212:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -209:
						break;
					case 213:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -210:
						break;
					case 214:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -211:
						break;
					case 215:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -212:
						break;
					case 216:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -213:
						break;
					case 217:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -214:
						break;
					case 218:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -215:
						break;
					case 219:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -216:
						break;
					case 220:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -217:
						break;
					case 221:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -218:
						break;
					case 222:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -219:
						break;
					case 223:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -220:
						break;
					case 224:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -221:
						break;
					case 225:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -222:
						break;
					case 226:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -223:
						break;
					case 227:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -224:
						break;
					case 228:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -225:
						break;
					case 229:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -226:
						break;
					case 230:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -227:
						break;
					case 231:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -228:
						break;
					case 232:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -229:
						break;
					case 233:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -230:
						break;
					case 234:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -231:
						break;
					case 235:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -232:
						break;
					case 236:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -233:
						break;
					case 237:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -234:
						break;
					case 238:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -235:
						break;
					case 239:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -236:
						break;
					case 240:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -237:
						break;
					case 241:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -238:
						break;
					case 242:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -239:
						break;
					case 243:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -240:
						break;
					case 244:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -241:
						break;
					case 245:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -242:
						break;
					case 246:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -243:
						break;
					case 247:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -244:
						break;
					case 248:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -245:
						break;
					case 249:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -246:
						break;
					case 250:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -247:
						break;
					case 251:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -248:
						break;
					case 252:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -249:
						break;
					case 253:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -250:
						break;
					case 254:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -251:
						break;
					case 255:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -252:
						break;
					case 256:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -253:
						break;
					case 257:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -254:
						break;
					case 258:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -255:
						break;
					case 259:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -256:
						break;
					case 260:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -257:
						break;
					case 261:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -258:
						break;
					case 262:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -259:
						break;
					case 263:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -260:
						break;
					case 264:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -261:
						break;
					case 265:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -262:
						break;
					case 266:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -263:
						break;
					case 267:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -264:
						break;
					case 268:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -265:
						break;
					case 269:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -266:
						break;
					case 270:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -267:
						break;
					case 271:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -268:
						break;
					case 272:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -269:
						break;
					case 273:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -270:
						break;
					case 274:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -271:
						break;
					case 275:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -272:
						break;
					case 276:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -273:
						break;
					case 277:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -274:
						break;
					case 278:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -275:
						break;
					case 279:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -276:
						break;
					case 280:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -277:
						break;
					case 281:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -278:
						break;
					case 282:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -279:
						break;
					case 283:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -280:
						break;
					case 284:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -281:
						break;
					case 285:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -282:
						break;
					case 286:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -283:
						break;
					case 287:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -284:
						break;
					case 288:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -285:
						break;
					case 289:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -286:
						break;
					case 290:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -287:
						break;
					case 291:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -288:
						break;
					case 292:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -289:
						break;
					case 293:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -290:
						break;
					case 294:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -291:
						break;
					case 295:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -292:
						break;
					case 296:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -293:
						break;
					case 297:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -294:
						break;
					case 298:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -295:
						break;
					case 299:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -296:
						break;
					case 300:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -297:
						break;
					case 301:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -298:
						break;
					case 302:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -299:
						break;
					case 303:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -300:
						break;
					case 304:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -301:
						break;
					case 305:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -302:
						break;
					case 306:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -303:
						break;
					case 307:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -304:
						break;
					case 308:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -305:
						break;
					case 309:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -306:
						break;
					case 310:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -307:
						break;
					case 311:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -308:
						break;
					case 312:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -309:
						break;
					case 313:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -310:
						break;
					case 314:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -311:
						break;
					case 315:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -312:
						break;
					case 316:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -313:
						break;
					case 317:
						{ myTokenType = JavaTokenType.IDENTIFIER; return; }
					case -314:
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
