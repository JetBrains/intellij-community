package com.intellij.execution.junit2;

import gnu.trove.TIntArrayList;

import java.io.IOException;
import java.io.Reader;

/**
 * @author dyoma
 */
public class PushReader {
  private final Reader mySource;
  private final TIntArrayList myReadAhead = new TIntArrayList();

  public PushReader(final Reader source) {
    mySource = source;
  }

  public int next() throws IOException {
    return myReadAhead.isEmpty() ? mySource.read() : myReadAhead.remove(myReadAhead.size() - 1);
  }

  public void pushBack(final char[] chars) {
    for (int i = chars.length - 1; i >= 0; i--) {
      final char aChar = chars[i];
      myReadAhead.add(aChar);
    }
  }

  public void close() throws IOException {
    mySource.close();
  }

  public boolean ready() throws IOException {
    return !myReadAhead.isEmpty() || mySource.ready();
  }

  public void pushBack(final int aChar) {
    myReadAhead.add(aChar);
  }

  public char[] next(final int charCount) throws IOException {
    final char[] chars = new char[charCount];
    int offset = 0;
    for (; offset < chars.length && offset < myReadAhead.size(); offset++)
      chars[offset] = (char)myReadAhead.remove(myReadAhead.size() - 1);

    while (offset < chars.length) {
      int bytesRead = mySource.read(chars, offset, chars.length - offset);
      if (bytesRead == -1)
        throw new IOException ("Unexpected end of pipe");
      offset += bytesRead;
    }

    return chars;
  }
}
