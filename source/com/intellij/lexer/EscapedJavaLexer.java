
package com.intellij.lexer;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Used to process scriptlet code in JSP attribute values like this:
 *   attribute="<%=texts.get(\"Blabla\")%>"
 */
public class EscapedJavaLexer implements Lexer, Cloneable{
  private char mySurroundingQuote;
  private JavaLexer myJavaLexer;

  private char[] myBuffer;
  private int myBufferEnd;
  private int myCurOffset;
  private IElementType myTokenType = null;
  private int myTokenEnd;
  private static final int END_STATE = 10;

  public EscapedJavaLexer(char surroundingQuote, LanguageLevel languageLevel) {
    mySurroundingQuote = surroundingQuote;
    myJavaLexer = new JavaLexer(languageLevel);
  }

  public char getSurroundingQuote() {
    return mySurroundingQuote;
  }

  public void setSurroundingQuote(char surroundingQuote) {
    mySurroundingQuote = surroundingQuote;
  }

  public Object clone() {
    try{
      EscapedJavaLexer clone = (EscapedJavaLexer)super.clone();
      clone.myJavaLexer = (JavaLexer)clone.myJavaLexer.clone();
      return clone;
    }
    catch(CloneNotSupportedException e){
      return null;
    }
  }

  public void start(char[] buffer) {
    start(buffer, 0, buffer.length);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myBuffer = buffer;
    myCurOffset = startOffset;
    myTokenEnd = startOffset;
    myBufferEnd = endOffset;
    myTokenType = null;
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    start(buffer, 0, buffer.length);
  }

  public int getState() {
    return 0;
  }

  public int getLastState() {
    return 0;
  }

  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  public final int getTokenStart(){
    locateToken();
    return myCurOffset;
  }

  public final int getTokenEnd(){
    locateToken();
    return myTokenEnd;
  }

  public final void advance(){
    locateToken();
    myTokenType = null;
    myCurOffset = myTokenEnd;
  }

  public final char[] getBuffer(){
    return myBuffer;
  }

  public final int getBufferEnd(){
    return myBufferEnd;
  }

  public int getSmartUpdateShift() {
    return myJavaLexer.getSmartUpdateShift();
  }

  private void locateToken(){
    if (myTokenType != null) return;
    if (myCurOffset >= myBufferEnd) return;

    int state = 0; // 0 -- start/end
                   // 1 -- start of escape in the string start
                   // 2 -- escape start inside string
                   // 3 -- inside string
                   // 4 -- error
    int offset = myCurOffset;
    do{
      char c = myBuffer[offset];
      switch(c){
        case '\\':
          state += state % 2 == 0 ? 1 : -1;
          if(state % 5 == 0) state = 4;
        case '"':
        case '\'':
          if((state == 1 || state == 2) && c == mySurroundingQuote){
            state = 6 - state * 3 ;
            offset++;
            break;
          }
        default:
          offset+=state > 0 ? 1 : 0;
          break;
        case '\n':
        case '\r':
          state = 0; // break string at the end of line
          //offset++;
          break;
      }
      if(offset == myBufferEnd - 1) state = 0;
      switch (state){
        case 0:
          if(offset == myCurOffset){
            myJavaLexer.start(myBuffer, myCurOffset, myBufferEnd);
            myTokenType = myJavaLexer.getTokenType();
            myTokenEnd = myJavaLexer.getTokenEnd();
          }
          else {
            myTokenType = myTokenType = mySurroundingQuote == '"' ? JavaTokenType.STRING_LITERAL : JavaTokenType.CHARACTER_LITERAL;
            myTokenEnd = offset;
          }
          break;
        case 4:
          myTokenType = JavaTokenType.BAD_CHARACTER;
          myTokenEnd = offset;
          state = 0;
      }
    }
    while (state > 0);
  }
}