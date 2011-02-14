package com.intellij.spellchecker.compress;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;

public final class Encoder {

  private final Alphabet alphabet;
  private static final int offset = 2;
  static final UnitBitSet WORD_OF_ENTIRELY_UNKNOWN_LETTERS = new UnitBitSet();

  public Encoder() {
    alphabet = new Alphabet();
  }

  public Encoder(Alphabet alphabet) {
    this.alphabet = alphabet;
  }

  public Alphabet getAlphabet() {
    return alphabet;
  }

  @Nullable
  public UnitBitSet encode(@NotNull CharSequence letters, boolean force) throws EncodingException {
    if (UnitBitSet.MAX_CHARS_IN_WORD <= letters.length() + offset) return null;
    int unknownLetters = 0;
    UnitBitSet bs = new UnitBitSet();
    for (int i = 0; i < letters.length(); i++) {
      char letter = letters.charAt(i);
      int index = alphabet.getIndex(letter, force);
      if (index < 0) unknownLetters++;
      bs.setUnitValue(i + offset, index);
    }
    bs.setUnitValue(0, letters.length());
    bs.setUnitValue(1, bs.getUnitValue(2));
    if (unknownLetters == letters.length()) return WORD_OF_ENTIRELY_UNKNOWN_LETTERS;
    if (unknownLetters>0) return null;
    return bs;
  }

  public String decode(@NotNull UnitBitSet bitSet) throws EncodingException {
    int wordLength = bitSet.getUnitValue(0);
    char firstLetter = alphabet.getLetter(bitSet.getUnitValue(1));
    final StringBuffer result = new StringBuffer();
    for (int i = 2; i < bitSet.b.length; i++) {
      int value = bitSet.getUnitValue(i);
      if (value > 0 && value <= alphabet.getLastIndexUsed()) {
        result.append(alphabet.getLetter(value));
      }
    }

    final String word = result.toString();
    final int actualLength = word.length();
    if (actualLength != wordLength || !word.startsWith(String.valueOf(firstLetter))) {
      throw new EncodingException(
        new MessageFormat("Error during encoding: required length - {0}, starts with {1}, but decoded: {2} ({3})")
          .format(new Object[]{wordLength, firstLetter, word, actualLength}));
    }
    return word;
  }
}