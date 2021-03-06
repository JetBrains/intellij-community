/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.relaxNG.compact.lexer;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.kohsuke.rngom.util.Utf16;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * A reader that deals with escape sequences in RNC files (\x{xx}) and keeps track of their positions to build correct
 * token ranges in the lexer.
 */
final class EscapePreprocessor extends FilterReader {
  private final IntList myQueuedChars;
  private final Int2IntMap myLengthMap;

  private int myOffset;

  EscapePreprocessor(Reader reader, int startOffset, Int2IntMap map) {
    super(reader);
    myOffset = startOffset;
    myQueuedChars = new IntArrayList();
    myLengthMap = map;
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    final int i = read();
    if (i == -1) {
      return -1;
    }
    cbuf[off] = (char)i; // not really efficient, but acceptable since we're usually not having to deal with megabytes of RNC files...
    return 1;
  }

  @Override
  public int read() throws IOException {
    if (myQueuedChars.size() > 0) {
      return consume();
    }
    final int i = super.read();
    if (i == -1) {
      return -1;
    }
    myOffset++;

    switch (i) {
      case '\r':
        assert false : "Unexpected newline character";  // IDEA document's are supposed to newlines normalized to \n
        if (peek() == '\n') {
          consume();
          myLengthMap.put(myOffset - 1, 2);
        }
      case '\n':
        return '\u0000';

      case '\\':
        int n = 0;
        int x;
        while ((x = peek()) == 'x') {
          n++;
        }
        if (n > 0 && x == '{') {
          n++;
        } else {
          return i;
        }
        int value = 0;
        while (isHexChar((char)(x = peek()))) {
          n++;
          value <<= 4;
          value |= Character.digit(x, 16);
        }
        if (x == '}') {
          n++;
        }
        consume(n);

        myLengthMap.put(myOffset - 1, n);
        myOffset += n;

        if (value <= 0xFFFF) {
          return value;
        }

        myQueuedChars.add(Utf16.surrogate2(value));
        return Utf16.surrogate1(value);
    }

    return i;
  }

  private static boolean isHexChar(char i) {
    if (Character.isDigit(i)) return true;
    final char c = Character.toLowerCase(i);
    return c >= 'a' && c <= 'f';
  }

  private int consume() {
    if (myQueuedChars.size() > 0) {
      myOffset++;
      return myQueuedChars.removeInt(0);
    }
    return -1;
  }

  private void consume(int n) {
    myQueuedChars.removeElements(0, n);
  }

  private int peek() throws IOException {
    final int i = super.read();
    if (i == -1) {
      return -1;
    }
    myQueuedChars.add(i);
    return i;
  }
}
