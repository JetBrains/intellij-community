/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.text;

/**
 *
 */
public class StringSearcher {
  private String myPattern = null;
  private char[] myPatternArray = null;
  private int myPatternLength;
  private int[] mySearchTable = new int[128];
  private boolean myCaseSensitive = true;
  private boolean myForwardDirection = true;

  public StringSearcher() {
  }

  public StringSearcher(String pattern) {
    setPattern(pattern);
  }

  public String getPattern(){
    return myPattern;
  }

  public void setPattern(String pattern){
    myPattern = pattern;
    myPatternArray = myCaseSensitive ? myPattern.toCharArray() : myPattern.toLowerCase().toCharArray();
    myPatternLength = myPatternArray.length;
    java.util.Arrays.fill(mySearchTable, -1);
  }
  
  public void setCaseSensitive(boolean value){
    myCaseSensitive = value;
    myPatternArray = myCaseSensitive ? myPattern.toCharArray() : myPattern.toLowerCase().toCharArray();
    java.util.Arrays.fill(mySearchTable, -1);
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public void setForwardDirection(boolean value) {
    myForwardDirection = value;
    java.util.Arrays.fill(mySearchTable, -1);
  }

  public boolean isForwardDirection() {
    return myForwardDirection;
  }

  public int scan(CharSequence text) {
    if (myForwardDirection){
      int start = 0;
      int end = text.length() - myPatternLength;
      while(start <= end){
        int i = myPatternLength - 1;
        char lastChar = text.charAt(start + i);
        if (!myCaseSensitive){
          lastChar = Character.toLowerCase(lastChar);
        }
        if (myPatternArray[i] == lastChar){
          i--;
          while(i >= 0){
            char c = text.charAt(start + i);
            if (!myCaseSensitive){
              c = Character.toLowerCase(c);
            }
            if (myPatternArray[i] != c) break;
            i--;
          }
          if (i < 0) return start;
        }

        int step;
        if (0 <= lastChar && lastChar < 128){
          step = mySearchTable[((int)lastChar) & 0x7F];
        }
        else{
          step = 1;
        }

        if (step <= 0){
          int index;
          for(index = myPatternLength - 2; index >= 0; index--){
            if (myPatternArray[index] == lastChar) break;
          }
          step = myPatternLength - index - 1;
          mySearchTable[((int)lastChar) & 0x7F] = step;
        }

        start += step;
      }
      return -1;
    }
    else{
      int start = 1;
      int end = text.length() - myPatternLength + 1;
      while(start <= end){
        int i = myPatternLength - 1;
        char lastChar = text.charAt(text.length() - (start + i));
        if (!myCaseSensitive){
          lastChar = Character.toLowerCase(lastChar);
        }
        if (myPatternArray[myPatternLength - 1 - i] == lastChar){
          i--;
          while(i >= 0){
            char c = text.charAt(text.length() - (start + i));
            if (!myCaseSensitive){
              c = Character.toLowerCase(c);
            }
            if (myPatternArray[myPatternLength - 1 - i] != c) break;
            i--;
          }
          if (i < 0) return text.length() - start - myPatternLength + 1;
        }

        int step;
        if (0 <= lastChar && lastChar < 128){
          step = mySearchTable[((int)lastChar) & 0x7F];
        }
        else{
          step = 1;
        }

        if (step <= 0){
          int index;
          for(index = myPatternLength - 2; index >= 0; index--){
            if (myPatternArray[myPatternLength - 1 - index] == lastChar) break;
          }
          step = myPatternLength - index - 1;
          mySearchTable[((int)lastChar) & 0x7F] = step;
        }

        start += step;
      }
      return -1;
    }

  }

  /**
   * @deprecated Use {@link #scan(CharSequence)} instead
   * @param text
   * @param startOffset
   * @param endOffset
   * @return
   */
  public int scan(char[] text, int startOffset, int endOffset){
    final int res = scan(new CharArrayCharSequence(text, startOffset, endOffset));
    return res >= 0 ? res + startOffset : -1;
  }
}
