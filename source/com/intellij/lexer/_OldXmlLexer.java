/* It's an automatically generated code. Do not modify it. */
package com.intellij.lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.*;


public class _OldXmlLexer implements Lexer, Cloneable {
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
    myState = (short)yy_lexical_state;
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
    return 10;
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

  public _OldXmlLexer () {
    yy_lexical_state = YYINITIAL;

    myTokenType = null;
  }

  private boolean yy_eof_done = false;
  public static final short PROCESSING_INSTRUCTION = 11;
  public static final short DECL_ATTR_VALUE_DQ = 3;
  public static final short DECL_ATTR = 2;
  public static final short ATTRIBUTE_VALUE_START = 8;
  public static final short DECL_ATTR_VALUE_SQ = 4;
  public static final short ATTRIBUTE_VALUE_DQ = 9;
  public static final short DECL = 1;
  public static final short ATTRIBUTE_VALUE_SQ = 10;
  public static final short DOCTYPE_EXTERNAL_ID = 13;
  public static final short CDATA = 17;
  public static final short COMMENT = 7;
  public static final short TAG_NAME = 5;
  public static final short DOCTYPE_MARKUP = 14;
  public static final short DOCTYPE_MARKUP_DQ = 15;
  public static final short YYINITIAL = 0;
  public static final short TAG_ATTRIBUTES = 6;
  public static final short DOCTYPE = 12;
  public static final short DOCTYPE_MARKUP_SQ = 16;
  public static final short LAST_STATE = 18;
  private static final int yy_state_dtrans[] = {
    0,
    204,
    206,
    25,
    27,
    207,
    208,
    34,
    214,
    215,
    216,
    217,
    218,
    219,
    222,
    243,
    244,
    245
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
    YY_NO_ANCHOR,
    YY_NO_ANCHOR,
    YY_NO_ANCHOR,
    YY_NO_ANCHOR,
    YY_NO_ANCHOR,
    YY_NO_ANCHOR,
    YY_NO_ANCHOR,
    YY_NO_ANCHOR,
    YY_NOT_ACCEPT,
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
    YY_NOT_ACCEPT,
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
    YY_NOT_ACCEPT,
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
    YY_NO_ANCHOR
  };
  private static final int yy_cmap[] = {
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 1, 1, 0, 1, 1, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0,
    2, 3, 4, 5, 0, 6, 7, 8,
    9, 10, 11, 12, 13, 14, 15, 16,
    17, 17, 17, 17, 17, 17, 17, 17,
    17, 17, 18, 19, 20, 21, 22, 23,
    0, 24, 25, 26, 27, 28, 29, 18,
    18, 30, 18, 18, 31, 32, 33, 34,
    35, 36, 37, 38, 39, 40, 18, 18,
    41, 42, 18, 43, 0, 44, 0, 18,
    0, 45, 45, 45, 45, 45, 45, 18,
    18, 18, 18, 18, 46, 47, 18, 18,
    18, 18, 18, 18, 18, 18, 18, 18,
    48, 18, 18, 0, 49, 0, 0, 0

  };
  private static final int yy_rmap[] = {
    0, 1, 2, 3, 4, 1, 5, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 6, 1, 1, 1, 1,
    1, 7, 1, 8, 1, 9, 10, 1,
    1, 1, 11, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 12, 1,
    1, 12, 12, 1, 1, 1, 13, 3,
    1, 1, 1, 1, 1, 1, 14, 1,
    1, 1, 1, 1, 14, 14, 1, 14,
    14, 1, 1, 1, 15, 1, 16, 1,
    1, 1, 1, 17, 13, 1, 18, 19,
    19, 19, 19, 19, 19, 20, 21, 13,
    13, 13, 22, 23, 24, 25, 13, 26,
    13, 20, 20, 20, 20, 20, 27, 28,
    29, 3, 3, 3, 30, 31, 32, 33,
    3, 34, 35, 21, 21, 21, 21, 21,
    36, 37, 38, 35, 39, 40, 41, 42,
    43, 44, 15, 15, 15, 15, 15, 45,
    46, 47, 48, 49, 50, 51, 52, 53,
    54, 16, 16, 16, 16, 16, 55, 56,
    57, 58, 59, 60, 61, 62, 63, 64,
    65, 66, 67, 68, 69, 70, 71, 72,
    73, 74, 75, 76, 77, 78, 79, 80,
    81, 82, 83, 84, 85, 86, 87, 88,
    89, 90, 91, 92, 93, 94, 95, 96,
    97, 98, 99, 100, 101, 102, 103, 104,
    105, 106, 107, 108, 109, 110, 111, 112,
    113, 114, 115, 116, 68, 70, 117, 118,
    119, 120, 121, 122, 123, 124, 125, 126,
    127, 128, 129, 130, 131, 132, 133, 134,
    135, 136, 137, 138, 139, 140, 141, 142,
    143, 144, 145, 146, 147, 148, 149, 150,
    151, 152, 153, 154, 155, 156, 157, 158,
    159, 160, 161, 162, 163, 164, 165, 166,
    167, 168, 169, 170, 171, 172, 173, 174,
    175, 176, 177, 178, 179, 180, 181, 182,
    183, 184, 185, 186, 187, 188, 189, 190,
    191, 192, 193, 194, 195, 196, 197, 198,
    199, 200, 201, 202, 203, 204, 205, 206,
    207, 208, 209, 210, 211, 212, 213, 214,
    215, 216, 217, 218, 219, 220, 221, 222,
    223, 224, 225, 226, 227, 228, 229, 230

  };
  private static final int yy_nxt[][] = unpackFromString(231,50,
                                                         "1,2:2,1:3,84,3,1:12,4,1:29,-1:51,2:2,-1:52,103,-1:12,121,-1:5,121:19,-1:2,121:4,-1:4,136,-1:12,5,-1:6,6,-1:74,178,-1:15,19:2,-1,19:2,-1:5,19:19,-1:2,19:4,-1,92:4,26,92,253,263,92:12,334,92:29,93:6,254,264,28,93:11,335,93:29,-1:14,29:2,-1,29:2,-1:5,29:19,-1:2,29:4,-1:15,30:2,-1,30:2,-1:5,30:19,-1:2,30:4,-1,94:6,255,265,94:6,164,94:5,297,94:29,-1:14,46:2,-1,46:2,-1:5,46:19,-1:2,46:4,-1:19,83,-1:5,83:19,-1:2,83:4,-1:15,62:2,-1,62:2,-1:5,62:19,-1:2,62:4,-1,76:4,-1,76:45,78:8,-1,78:41,-1:14,83:2,-1,83:2,7,-1:4,83:19,-1:2,83:4,-1:4,205,-1:12,5,-1:33,92:4,-1,92:45,93:8,-1,93:41,94:14,209,94:35,-1:14,46:2,-1,46:2,-1:5,46:2,49,46:16,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:18,68,-1:2,62:4,-1,76:4,-1,76:9,100:2,76,100:2,138,76:4,100:19,76:2,100:4,76,78:8,-1,78:5,101:2,78,101:2,153,78:4,101:19,78:2,101:4,78,-1:17,151,-1:30,163,-1,92:4,-1,92:9,110:2,92,110:2,87,92:4,110:19,92:2,110:4,92,93:8,-1,93:5,111:2,93,111:2,105,93:4,111:19,93:2,111:4,93,94:14,211,112,94,112:2,123,94:4,112:19,94:2,112:4,94,-1:14,46:2,-1,46:2,-1:5,46:8,50,46:10,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:18,69,-1:2,62:4,-1,76:4,-1,76:9,118:2,76,118:2,139,76:4,118:19,76:2,118:4,76,78:8,-1,78:5,119:2,78,119:2,154,78:4,119:19,78:2,119:4,78,-1:14,121:2,-1,121:2,8,-1:4,121:19,-1:2,121:4,-1:4,205,-1:46,92:4,-1,92:9,128:2,92,128:2,88,92:4,128:19,92:2,128:4,92,93:8,-1,93:5,129:2,93,129:2,106,93:4,129:19,93:2,129:4,93,94:14,212,130,94,130:2,124,94:4,130:19,94:2,130:4,94,-1:14,62:2,-1,62:2,-1:5,62:2,71,62:16,-1:2,62:4,-1,76:4,-1,76:12,133,76,140,76:30,78:8,-1,78:8,134,78,155,78:30,-1:3,246,-1:60,168,-1:9,170,-1:2,172,174,-1:4,176,-1:9,177,-1:28,21,-1:27,92:4,-1,92:12,143,92,89,92:30,93:8,-1,93:8,144,93,107,93:30,94:14,209,94:2,145,94,125,94:30,-1:22,45,-1:41,62:2,-1,62:2,-1:5,62:8,72,62:10,-1:2,62:4,-1,76:4,-1,76:12,148,76,141,76:4,148:6,76:15,148,76:4,78:8,-1,78:8,149,78,156,78:4,149:6,78:15,149,78:4,-1:44,247,-1:22,151,-1,9,-1:52,33,-1:27,92:4,-1,92:12,158,92,90,92:4,158:6,92:15,158,92:4,93:8,-1,93:8,159,93,108,93:4,159:6,93:15,159,93:4,94:14,209,94:2,160,94,126,94:4,160:6,94:15,160,94:4,76:4,-1,76:38,142,76:6,78:8,-1,78:34,157,78:6,-1:17,179,-1:6,179:6,-1:15,179,-1:4,94:14,210,94:35,92:4,-1,92:38,91,92:6,93:8,-1,93:34,109,93:6,94:14,209,94:28,127,94:6,-1:14,10,-1:57,39,-1:66,257,-1:10,220:4,51,220:45,-1:34,180,-1:15,221:8,52,221:41,-1:31,181,-1,266,-1:45,223,224,-1:4,225,-1,226,-1:46,273,-1:41,182,-1:70,183,-1:19,179,-1,11,-1:4,179:6,-1:15,179,-1:30,280,-1:51,185,-1:48,298,-1:68,188,-1:34,189,-1:50,191,-1:47,286,-1:43,288,-1:27,12,-1:77,192,-1:61,193,-1:35,194,-1:59,198,-1:46,199,-1:47,200,-1:58,13,-1:37,201,-1:43,202,-1:64,14,-1:38,15,-1:60,16,-1:44,203,-1:58,17,-1:39,18,-1:16,85,2:2,85:3,104,3,85:10,19,85,122,85,20,137,19:19,85:2,19:4,85,-1:14,168,-1:28,177,-1:6,85,2:2,85,22,85,104,3,23,85:11,122,24,85:29,2:2,85:3,104,3,85:10,29,85,86,85:3,29:19,85:2,29:4,85:2,2:2,85:3,104,3,85:8,152,85,30,85,86,31,32,85,30:19,85:2,30:4,85,94:14,-1,94:35,-1:22,35,-1:27,94:14,83,112,94,112:2,123,94:4,112:19,94:2,112:4,94:15,121,130,94,130:2,124,94:4,130:19,94:2,130:4,94:15,10,94:35,85,2:2,85,36,85,104,3,37,85:7,169,85:3,86,85,38,85:27,40:4,41,40,95,113,40:12,86,40:29,42:6,96,114,43,42:11,86,42:29,44:6,97,115,44:12,131,44:2,146,44:26,85,2:2,85:3,104,3,85:10,46,85,122,85,47,85,46:11,314,46:2,321,46:4,48,85,46:4,85:2,2:2,85,171,85,104,3,173,85:11,122,85,47,85:20,48,85:7,2:2,85,53,175,54,55,56,57,58,59,60,61,62:2,85,62:2,63,256,85,64,65,250,62:3,305,62:6,315,62:2,322,62:4,85,66,62:4,67,-1:30,228,-1:51,229,-1:43,230,-1:51,231,-1:35,168,-1:9,170,-1:3,174,-1:4,176,-1:9,177,-1:47,232,-1:43,233,-1:41,302,-1:58,234,-1:41,235,-1:52,236,-1:58,258,-1:36,70,-1:52,237,-1:47,240,-1:45,73,-1:62,241,-1:39,74,-1:50,242,-1:48,75,-1:22,76:4,77,76,251,261,76:12,332,76:29,78:6,252,262,79,78:11,333,78:29,80:6,102,120,80:12,135,80:23,150,80:5,-1:14,248,-1:28,177,-1:28,81,-1:41,82,-1:49,46:2,-1,46:2,-1:5,46:6,98,46:12,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:9,99,62:9,-1:2,62:4,-1,76:4,-1,76:13,100,76:5,100:19,76:2,100:4,76,78:8,-1,78:9,101,78:5,101:19,78:2,101:4,78,92:4,-1,92:13,110,92:5,110:19,92:2,110:4,92,93:8,-1,93:9,111,93:5,111:19,93:2,111:4,93,94:14,209,94:3,112,94:5,112:19,94:2,112:4,94,-1:3,227,-1:85,184,-1:40,239,-1:33,46:2,-1,46:2,-1:5,46:4,116,46:14,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:15,117,62:3,-1:2,62:4,-1,76:4,-1,268,76:12,118,76:5,118:19,76:2,118:4,76,78:5,269,78:2,-1,78:9,119,78:5,119:19,78:2,119:4,78,92:4,-1,270,92:12,128,92:5,128:19,92:2,128:4,92,93:5,271,93:2,-1,93:9,129,93:5,129:19,93:2,129:4,93,94:5,272,94:8,209,94:3,130,94:5,130:19,94:2,130:4,94,-1:39,186,-1:24,62:2,-1,62:2,-1:5,62:6,132,62:12,-1:2,62:4,-1,76:4,-1,76:12,133,76:30,275,76,78:8,-1,78:8,134,78:30,276,78,92:4,-1,92:12,143,92:30,277,92,93:8,-1,93:8,144,93:30,278,93,94:14,209,94:2,145,94:30,285,94,-1:39,187,-1:24,62:2,-1,62:2,-1:5,62:4,147,62:14,-1:2,62:4,-1,76:4,-1,76:12,148,76:6,148:6,76:15,148,76:4,78:8,-1,78:8,149,78:6,149:6,78:15,149,78:4,92:4,-1,92:12,158,92:6,158:6,92:15,158,92:4,93:8,-1,93:8,159,93:6,159:6,93:15,159,93:4,94:14,213,94:28,327,94:6,-1:39,190,-1:10,76:4,-1,76:19,161,76:25,78:8,-1,78:15,162,78:25,92:4,-1,92:19,165,92:25,93:8,-1,93:15,166,93:25,94:14,209,94:2,160,94:6,160:6,94:15,160,94:4,-1:39,195,-1:10,94:14,209,94:9,167,94:25,-1:39,196,-1:49,197,-1:49,238,-1:24,46:2,-1,46:2,-1:5,46:7,249,46:11,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:11,260,62:7,-1:2,62:4,-1,76:4,-1,76:34,281,76:10,78:8,-1,78:30,282,78:10,92:4,-1,92:34,283,92:10,93:8,-1,93:30,284,93:10,94:3,279,94:10,209,94:35,-1:24,289,-1:39,46:2,-1,46:2,-1:5,46:15,259,46:3,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:7,267,62:11,-1:2,62:4,-1,94:14,209,94:24,287,94:10,-1:24,290,-1:39,62:2,-1,62:2,-1:5,62:15,274,62:3,-1:2,62:4,-1:15,46:2,-1,46:2,-1:5,46,291,46:17,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:8,292,62:10,-1:2,62:4,-1,76:4,-1,76:19,293,76:25,78:8,-1,78:15,294,78:25,92:4,-1,92:19,295,92:25,93:8,-1,93:15,296,93:25,94:14,209,94:9,301,94:25,-1:14,46:2,-1,46:2,-1:5,46:14,299,46:4,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62,300,62:17,-1:2,62:4,-1:15,62:2,-1,62:2,-1:5,62:14,303,62:4,-1:2,62:4,-1:15,46:2,-1,46:2,-1:5,46:16,304,46:2,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:16,312,62:2,-1:2,62:4,-1,76:4,-1,76:22,306,76:22,78:8,-1,78:18,307,78:22,92:4,-1,92:22,308,92:22,93:8,-1,93:18,309,93:22,94:14,209,94:12,310,94:22,-1:14,46:2,-1,46:2,-1:5,46:18,311,-1:2,46:4,-1:15,62:2,-1,62:2,-1:5,62:18,313,-1:2,62:4,-1,76:4,-1,76:21,316,76:23,78:8,-1,78:17,317,78:23,92:4,-1,92:21,318,92:23,93:8,-1,93:17,319,93:23,94:14,209,94:11,320,94:23,76:4,-1,76:38,323,76:6,78:8,-1,78:34,324,78:6,92:4,-1,92:38,325,92:6,93:8,-1,93:34,326,93:6,76:3,328,-1,76:45,78:3,329,78:4,-1,78:41,92:3,330,-1,92:45,93:3,331,93:4,-1,93:41");
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
              { myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
            case -2:
              break;
            case 2:
              { myTokenType = XmlTokenType.XML_WHITE_SPACE; return; }
            case -3:
              break;
            case 3:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -4:
              break;
            case 4:
              { myTokenType = XmlTokenType.XML_START_TAG_START; yy_lexical_state = TAG_NAME; return; }
            case -5:
              break;
            case 5:
              { myTokenType = XmlTokenType.XML_END_TAG_START; yy_lexical_state = TAG_NAME; return; }
            case -6:
              break;
            case 6:
              { myTokenType = XmlTokenType.XML_PI_START; yy_lexical_state = PROCESSING_INSTRUCTION; return; }
            case -7:
              break;
            case 7:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -8:
              break;
            case 8:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -9:
              break;
            case 9:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -10:
              break;
            case 10:
              { myTokenType = XmlTokenType.XML_COMMENT_START; yy_lexical_state = COMMENT; return; }
            case -11:
              break;
            case 11:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -12:
              break;
            case 12:
              { myTokenType = XmlTokenType.XML_DECL_START; yy_lexical_state = DECL; return; }
            case -13:
              break;
            case 13:
              { myTokenType = XmlTokenType.XML_ENTITY_DECL_START; yy_lexical_state = DOCTYPE_MARKUP; return;}
            case -14:
              break;
            case 14:
              { myTokenType = XmlTokenType.XML_ATTLIST_DECL_START; yy_lexical_state = DOCTYPE_MARKUP; return;}
            case -15:
              break;
            case 15:
              { myTokenType = XmlTokenType.XML_DOCTYPE_START; yy_lexical_state = DOCTYPE; return; }
            case -16:
              break;
            case 16:
              { myTokenType = XmlTokenType.XML_ELEMENT_DECL_START; yy_lexical_state = DOCTYPE_MARKUP; return;}
            case -17:
              break;
            case 17:
              {myTokenType = XmlTokenType.XML_CDATA_START; yy_lexical_state = CDATA; return; }
            case -18:
              break;
            case 18:
              { myTokenType = XmlTokenType.XML_NOTATION_DECL_START; yy_lexical_state = DOCTYPE_MARKUP; return;}
            case -19:
              break;
            case 19:
              { myTokenType = XmlTokenType.XML_NAME;  yy_lexical_state = DECL_ATTR; return; }
            case -20:
              break;
            case 20:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; yy_lexical_state = YYINITIAL; return;}
            case -21:
              break;
            case 21:
              { myTokenType = XmlTokenType.XML_DECL_END; yy_lexical_state = YYINITIAL; return;}
            case -22:
              break;
            case 22:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = DECL_ATTR_VALUE_DQ; return;}
            case -23:
              break;
            case 23:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = DECL_ATTR_VALUE_SQ; return;}
            case -24:
              break;
            case 24:
              { myTokenType = XmlTokenType.XML_EQ; return;}
            case -25:
              break;
            case 25:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -26:
              break;
            case 26:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = DECL; return;}
            case -27:
              break;
            case 27:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -28:
              break;
            case 28:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = DECL; return;}
            case -29:
              break;
            case 29:
              { myTokenType = XmlTokenType.XML_TAG_NAME; yy_lexical_state = TAG_ATTRIBUTES; return; }
            case -30:
              break;
            case 30:
              { myTokenType = XmlTokenType.XML_NAME; return; }
            case -31:
              break;
            case 31:
              { myTokenType = XmlTokenType.XML_EQ; yy_lexical_state = ATTRIBUTE_VALUE_START; return; }
            case -32:
              break;
            case 32:
              { myTokenType = XmlTokenType.XML_TAG_END; yy_lexical_state = YYINITIAL; return; }
            case -33:
              break;
            case 33:
              { myTokenType = XmlTokenType.XML_EMPTY_ELEMENT_END; yy_lexical_state = YYINITIAL; return; }
            case -34:
              break;
            case 34:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -35:
              break;
            case 35:
              { myTokenType = XmlTokenType.XML_COMMENT_END; yy_lexical_state = YYINITIAL; return; }
            case -36:
              break;
            case 36:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = ATTRIBUTE_VALUE_DQ; return; }
            case -37:
              break;
            case 37:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = ATTRIBUTE_VALUE_SQ; return; }
            case -38:
              break;
            case 38:
              { myTokenType = XmlTokenType.XML_TAG_END; yy_lexical_state = YYINITIAL; return; }
            case -39:
              break;
            case 39:
              { myTokenType = XmlTokenType.XML_EMPTY_ELEMENT_END; yy_lexical_state = YYINITIAL; return; }
            case -40:
              break;
            case 40:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -41:
              break;
            case 41:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = TAG_ATTRIBUTES; return; }
            case -42:
              break;
            case 42:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -43:
              break;
            case 43:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = TAG_ATTRIBUTES; return; }
            case -44:
              break;
            case 44:
              { myTokenType = XmlTokenType.XML_PI_TARGET; return; }
            case -45:
              break;
            case 45:
              { myTokenType = XmlTokenType.XML_PI_END; yy_lexical_state = YYINITIAL; return; }
            case -46:
              break;
            case 46:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -47:
              break;
            case 47:
              { myTokenType = XmlTokenType.XML_DOCTYPE_END;  yy_lexical_state = YYINITIAL; return; }
            case -48:
              break;
            case 48:
              { myTokenType = XmlTokenType.XML_MARKUP_START; yy_lexical_state = DOCTYPE_MARKUP; return;}
            case -49:
              break;
            case 49:
              { myTokenType = XmlTokenType.XML_DOCTYPE_PUBLIC;  yy_lexical_state = DOCTYPE_EXTERNAL_ID; return; }
            case -50:
              break;
            case 50:
              { myTokenType = XmlTokenType.XML_DOCTYPE_SYSTEM;  yy_lexical_state = DOCTYPE_EXTERNAL_ID; return; }
            case -51:
              break;
            case 51:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -52:
              break;
            case 52:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -53:
              break;
            case 53:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = DOCTYPE_MARKUP_DQ; return; }
            case -54:
              break;
            case 54:
              { myTokenType = XmlTokenType.XML_PERCENT; return;}
            case -55:
              break;
            case 55:
              { myTokenType = XmlTokenType.XML_AMP; return;}
            case -56:
              break;
            case 56:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER; yy_lexical_state = DOCTYPE_MARKUP_SQ; return; }
            case -57:
              break;
            case 57:
              { myTokenType = XmlTokenType.XML_LEFT_PAREN; return;}
            case -58:
              break;
            case 58:
              { myTokenType = XmlTokenType.XML_RIGHT_PAREN; return;}
            case -59:
              break;
            case 59:
              { myTokenType = XmlTokenType.XML_STAR; return;}
            case -60:
              break;
            case 60:
              { myTokenType = XmlTokenType.XML_PLUS; return;}
            case -61:
              break;
            case 61:
              { myTokenType = XmlTokenType.XML_COMMA; return;}
            case -62:
              break;
            case 62:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -63:
              break;
            case 63:
              { myTokenType = XmlTokenType.XML_SEMI; return;}
            case -64:
              break;
            case 64:
              { myTokenType = XmlTokenType.XML_TAG_END; return;}
            case -65:
              break;
            case 65:
              { myTokenType = XmlTokenType.XML_QUESTION; return;}
            case -66:
              break;
            case 66:
              { myTokenType = XmlTokenType.XML_MARKUP_END; yy_lexical_state = DOCTYPE; return;}
            case -67:
              break;
            case 67:
              { myTokenType = XmlTokenType.XML_BAR; return;}
            case -68:
              break;
            case 68:
              { myTokenType = XmlTokenType.XML_CONTENT_ANY; return;}
            case -69:
              break;
            case 69:
              { myTokenType = XmlTokenType.XML_CONTENT_EMPTY; return;}
            case -70:
              break;
            case 70:
              { myTokenType = XmlTokenType.XML_ATT_FIXED; return;}
            case -71:
              break;
            case 71:
              { myTokenType = XmlTokenType.XML_DOCTYPE_PUBLIC; return; }
            case -72:
              break;
            case 72:
              { myTokenType = XmlTokenType.XML_DOCTYPE_SYSTEM; return; }
            case -73:
              break;
            case 73:
              { myTokenType = XmlTokenType.XML_PCDATA; return;}
            case -74:
              break;
            case 74:
              { myTokenType = XmlTokenType.XML_ATT_IMPLIED; return;}
            case -75:
              break;
            case 75:
              { myTokenType = XmlTokenType.XML_ATT_REQUIRED; return;}
            case -76:
              break;
            case 76:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -77:
              break;
            case 77:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = DOCTYPE_MARKUP; return; }
            case -78:
              break;
            case 78:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -79:
              break;
            case 79:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER; yy_lexical_state = DOCTYPE_MARKUP; return; }
            case -80:
              break;
            case 80:
              {myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
            case -81:
              break;
            case 81:
              {myTokenType = XmlTokenType.XML_CDATA_END; yy_lexical_state = YYINITIAL; return; }
            case -82:
              break;
            case 82:
              { myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
            case -83:
              break;
            case 84:
              { myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
            case -84:
              break;
            case 85:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -85:
              break;
            case 86:
              { myTokenType = XmlTokenType.XML_START_TAG_START; yy_lexical_state = TAG_NAME; return; }
            case -86:
              break;
            case 87:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -87:
              break;
            case 88:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -88:
              break;
            case 89:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -89:
              break;
            case 90:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -90:
              break;
            case 91:
              {myTokenType = XmlTokenType.XML_CDATA_START; yy_lexical_state = CDATA; return; }
            case -91:
              break;
            case 92:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -92:
              break;
            case 93:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -93:
              break;
            case 94:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -94:
              break;
            case 95:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -95:
              break;
            case 96:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -96:
              break;
            case 97:
              { myTokenType = XmlTokenType.XML_PI_TARGET; return; }
            case -97:
              break;
            case 98:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -98:
              break;
            case 99:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -99:
              break;
            case 100:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -100:
              break;
            case 101:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -101:
              break;
            case 102:
              {myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
            case -102:
              break;
            case 104:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -103:
              break;
            case 105:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -104:
              break;
            case 106:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -105:
              break;
            case 107:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -106:
              break;
            case 108:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -107:
              break;
            case 109:
              {myTokenType = XmlTokenType.XML_CDATA_START; yy_lexical_state = CDATA; return; }
            case -108:
              break;
            case 110:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -109:
              break;
            case 111:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -110:
              break;
            case 112:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -111:
              break;
            case 113:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -112:
              break;
            case 114:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -113:
              break;
            case 115:
              { myTokenType = XmlTokenType.XML_PI_TARGET; return; }
            case -114:
              break;
            case 116:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -115:
              break;
            case 117:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -116:
              break;
            case 118:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -117:
              break;
            case 119:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -118:
              break;
            case 120:
              {myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
            case -119:
              break;
            case 122:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -120:
              break;
            case 123:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -121:
              break;
            case 124:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -122:
              break;
            case 125:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -123:
              break;
            case 126:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -124:
              break;
            case 127:
              {myTokenType = XmlTokenType.XML_CDATA_START; yy_lexical_state = CDATA; return; }
            case -125:
              break;
            case 128:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -126:
              break;
            case 129:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -127:
              break;
            case 130:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -128:
              break;
            case 131:
              { myTokenType = XmlTokenType.XML_PI_TARGET; return; }
            case -129:
              break;
            case 132:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -130:
              break;
            case 133:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -131:
              break;
            case 134:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -132:
              break;
            case 135:
              {myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
            case -133:
              break;
            case 137:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -134:
              break;
            case 138:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -135:
              break;
            case 139:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -136:
              break;
            case 140:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -137:
              break;
            case 141:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -138:
              break;
            case 142:
              {myTokenType = XmlTokenType.XML_CDATA_START; yy_lexical_state = CDATA; return; }
            case -139:
              break;
            case 143:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -140:
              break;
            case 144:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -141:
              break;
            case 145:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -142:
              break;
            case 146:
              { myTokenType = XmlTokenType.XML_PI_TARGET; return; }
            case -143:
              break;
            case 147:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -144:
              break;
            case 148:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -145:
              break;
            case 149:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -146:
              break;
            case 150:
              {myTokenType = XmlTokenType.XML_DATA_CHARACTERS; return; }
            case -147:
              break;
            case 152:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -148:
              break;
            case 153:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -149:
              break;
            case 154:
              { myTokenType = XmlTokenType.XML_ENTITY_REF_TOKEN; return; }
            case -150:
              break;
            case 155:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -151:
              break;
            case 156:
              { myTokenType = XmlTokenType.XML_CHAR_ENTITY_REF; return; }
            case -152:
              break;
            case 157:
              {myTokenType = XmlTokenType.XML_CDATA_START; yy_lexical_state = CDATA; return; }
            case -153:
              break;
            case 158:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -154:
              break;
            case 159:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -155:
              break;
            case 160:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -156:
              break;
            case 161:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -157:
              break;
            case 162:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -158:
              break;
            case 164:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -159:
              break;
            case 165:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -160:
              break;
            case 166:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -161:
              break;
            case 167:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -162:
              break;
            case 169:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -163:
              break;
            case 171:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -164:
              break;
            case 173:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -165:
              break;
            case 175:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -166:
              break;
            case 249:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -167:
              break;
            case 250:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -168:
              break;
            case 251:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -169:
              break;
            case 252:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -170:
              break;
            case 253:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -171:
              break;
            case 254:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -172:
              break;
            case 255:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -173:
              break;
            case 256:
              { myTokenType = XmlTokenType.XML_BAD_CHARACTER; return; }
            case -174:
              break;
            case 259:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -175:
              break;
            case 260:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -176:
              break;
            case 261:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -177:
              break;
            case 262:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -178:
              break;
            case 263:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -179:
              break;
            case 264:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -180:
              break;
            case 265:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -181:
              break;
            case 267:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -182:
              break;
            case 268:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -183:
              break;
            case 269:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -184:
              break;
            case 270:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -185:
              break;
            case 271:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -186:
              break;
            case 272:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -187:
              break;
            case 274:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -188:
              break;
            case 275:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -189:
              break;
            case 276:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -190:
              break;
            case 277:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -191:
              break;
            case 278:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -192:
              break;
            case 279:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -193:
              break;
            case 281:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -194:
              break;
            case 282:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -195:
              break;
            case 283:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -196:
              break;
            case 284:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -197:
              break;
            case 285:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -198:
              break;
            case 287:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -199:
              break;
            case 291:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -200:
              break;
            case 292:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -201:
              break;
            case 293:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -202:
              break;
            case 294:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -203:
              break;
            case 295:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -204:
              break;
            case 296:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -205:
              break;
            case 297:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -206:
              break;
            case 299:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -207:
              break;
            case 300:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -208:
              break;
            case 301:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -209:
              break;
            case 303:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -210:
              break;
            case 304:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -211:
              break;
            case 305:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -212:
              break;
            case 306:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -213:
              break;
            case 307:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -214:
              break;
            case 308:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -215:
              break;
            case 309:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -216:
              break;
            case 310:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -217:
              break;
            case 311:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -218:
              break;
            case 312:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -219:
              break;
            case 313:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -220:
              break;
            case 314:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -221:
              break;
            case 315:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -222:
              break;
            case 316:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -223:
              break;
            case 317:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -224:
              break;
            case 318:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -225:
              break;
            case 319:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -226:
              break;
            case 320:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -227:
              break;
            case 321:
              { myTokenType = XmlTokenType.XML_NAME;  return; }
            case -228:
              break;
            case 322:
              { myTokenType = XmlTokenType.XML_NAME; return;}
            case -229:
              break;
            case 323:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -230:
              break;
            case 324:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -231:
              break;
            case 325:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -232:
              break;
            case 326:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -233:
              break;
            case 327:
              { myTokenType = XmlTokenType.XML_COMMENT_CHARACTERS; return; }
            case -234:
              break;
            case 328:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -235:
              break;
            case 329:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -236:
              break;
            case 330:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -237:
              break;
            case 331:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -238:
              break;
            case 332:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -239:
              break;
            case 333:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return; }
            case -240:
              break;
            case 334:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -241:
              break;
            case 335:
              { myTokenType = XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN; return;}
            case -242:
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
