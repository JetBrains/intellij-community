package com.intellij.util.text;

import java.text.CharacterIterator;

/**
 * @author max
 */
public class CharSequenceCharacterIterator implements CharacterIterator {
  private CharSequence myChars;
  private int myCurPosition;

  public CharSequenceCharacterIterator(final CharSequence chars) {
    myChars = chars;
    myCurPosition = 0;
  }

  public char current() {
    if (myCurPosition < 0) {
      myCurPosition = 0;
      return DONE;
    }

    if (myCurPosition >= myChars.length()) {
      myCurPosition = myChars.length();
      return DONE;
    }

    return myChars.charAt(myCurPosition);
  }

  public char first() {
    myCurPosition = 0;
    return current();
  }

  public char last() {
    myCurPosition = myChars.length() - 1;
    return current();
  }

  public char next() {
    myCurPosition++;
    return current();
  }

  public char previous() {
    myCurPosition--;
    return current();
  }

  public int getBeginIndex() {
    return 0;
  }

  public int getEndIndex() {
    return myChars.length();
  }

  public int getIndex() {
    return myCurPosition;
  }

  public char setIndex(int position) {
    if (position < 0 || position > myChars.length()) throw new IllegalArgumentException("Wrong index: " + position);
    myCurPosition = position;
    return current();
  }

  public Object clone() {
    final CharSequenceCharacterIterator it = new CharSequenceCharacterIterator(myChars);
    it.myCurPosition = myCurPosition;
    return it;
  }
}
