package com.intellij.util.text;

import java.io.Reader;

/**
 * @author max
 */
public class CharSequenceReader extends Reader {
  private CharSequence myText;
  private int myCurPos;

  public CharSequenceReader(final CharSequence text) {
    myText = text;
    myCurPos = 0;
  }

  public void close() {}

  public int read(char[] cbuf, int off, int len) {
    if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
        throw new IndexOutOfBoundsException();
    } else if (len == 0) {
        return 0;
    }

    if (myText instanceof CharArrayCharSequence) { // Optimization
      final int readChars = ((CharArrayCharSequence)myText).readCharsTo(myCurPos, cbuf, off, len);
      if (readChars < 0) return -1;
      myCurPos += readChars;
      return readChars;
    }

    int charsToCopy = Math.min(len, myText.length() - myCurPos);
    if (charsToCopy <= 0) return -1;

    for (int n = 0; n < charsToCopy; n++) {
      cbuf[n + off] = myText.charAt(n + myCurPos);
    }

    myCurPos += charsToCopy;
    return charsToCopy;
  }

  public int read() {
    if (myCurPos >= myText.length()) return -1;
    return myText.charAt(myCurPos++);
  }
}
