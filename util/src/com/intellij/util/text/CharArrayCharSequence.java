/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.text;

public class CharArrayCharSequence implements CharSequence {
  private final char[] myChars;
  private final int myStart;
  private final int myEnd;

  public CharArrayCharSequence(char[] chars) {
    this(chars, 0, chars.length);
  }

  public CharArrayCharSequence(char[] chars, int start, int end) {
    if (start < 0 || end > chars.length || start > end) {
      throw new IndexOutOfBoundsException("chars.length:" + chars.length +
                                          ", start:" + start +
                                          ", end:" + end);
    }
    myChars = chars;
    myStart = start;
    myEnd = end;
  }

  public int length() {
    return myEnd - myStart;
  }

  public char charAt(int index) {
    return myChars[index + myStart];
  }

  public CharSequence subSequence(int start, int end) {
    return new CharArrayCharSequence(myChars, myStart + start, myStart + end);
  }

  public String toString() {
    return new String(myChars, myStart, myEnd - myStart); //TODO StringFactory
  }

  public char[] getChars() {
    if (myStart == 0 /*&& myEnd == myChars.length*/) return myChars;
    char[] chars = new char[length()];
    System.arraycopy(myChars, myStart, chars, 0, length());
    return chars;
  }

  public int hashCode() {
    int h = 0;
    int to = myEnd;
    for (int off = myStart; off < to; off++) {
      h = 31 * h + myChars[off];
    }
    return h;
  }
  
  public void getChars(char[] dst, int dstOffset) {
    System.arraycopy(myChars, myStart, dst, dstOffset, length());
  }

  /**
   * See {@link java.io.Reader#read(char[], int, int)};
   */
  public int readCharsTo(int start, char[] cbuf, int off, int len) {
    final int readChars = Math.min(len, length() - start);
    if (readChars <= 0) return -1;

    System.arraycopy(myChars, start, cbuf, off, readChars);
    return readChars;
  }
}
