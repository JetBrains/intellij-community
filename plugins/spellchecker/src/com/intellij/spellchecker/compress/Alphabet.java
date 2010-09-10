package com.intellij.spellchecker.compress;

import org.jetbrains.annotations.NotNull;

public final class Alphabet {

  private final char[] letters;
  private int lastIndexUsed = 0;
  private static final int MAX_INDEX = UnitBitSet.MAX_UNIT_VALUE;

  public char getLetter(int position) {
    return letters[position];
  }

  /*
    @param forceAdd - if set to true - letter will be added to the alphabet if not present yet
    @return index of the letter or -1 if letter was not found and could not be added (due to forceAdd property value)
  */

  public int getIndex(char letter, boolean forceAdd) {
    final int r = getNextIndex(0, letter, forceAdd);
    return r;
  }

  /*
   @param forceAdd - if set to true - letter will be added to the alphabet if not present yet
   @return index of the letter or -1 if letter was not found and could not be added (due to forceAdd property value)
  */
  public int getNextIndex(int startFrom, char letter, boolean forceAdd) {
    for (int i = startFrom; i <= lastIndexUsed; i++) {
      if (letters[i] != 0 && letters[i] == letter) {
        return i;
      }
    }
    if (!forceAdd) {
      return -1;
    }
    return add(letter);
  }

  public int getLastIndexUsed() {
    return lastIndexUsed;
  }

  public int add(char c) {
    lastIndexUsed++;
    letters[lastIndexUsed] = c;
    return lastIndexUsed;
  }


  Alphabet() {
    this(MAX_INDEX);
  }

  Alphabet(int maxIndex) {
    assert maxIndex <= MAX_INDEX : "alphabet is too long";
    letters = new char[maxIndex];
  }

  // TODO: this should be ONLY way to create it to sped up getIndex
  Alphabet(@NotNull CharSequence alphabet) {
    this(alphabet.length() + 1);
    for (int i = 0; i < alphabet.length(); i++) {
      add(alphabet.charAt(i));
    }
  }
}
