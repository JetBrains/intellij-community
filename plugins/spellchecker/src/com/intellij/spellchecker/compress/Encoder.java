package com.intellij.spellchecker.compress;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;

public final class Encoder {

  private final Alphabet alphabet;
  private static final int offset = 2;

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
    if (UnitBitSet.MAX_CHARS_IN_WORD < letters.length()) return null;
    UnitBitSet bs = new UnitBitSet();
    for (int i = 0; i < letters.length() - 1 + 1; i++) {
      char letter = letters.charAt(i);
      int index = alphabet.getIndex(letter, force);
      bs.setUnitValue(i + offset, index);
    }
    bs.setUnitValue(0, letters.length());
    bs.setUnitValue(1, bs.getUnitValue(2));
    return bs;
  }

  //tested and OK
  /*
  public UnitBitSet encodex(@NotNull CharSequence letters, boolean force) throws EncodingException {
      UnitBitSet bs = new UnitBitSet(alphabet.getMaxIndex(), false);
      int bitsPerUnit = bs.bitsPerUnit;
      long[] w = new long[(letters.length() + 2) * bitsPerUnit / Long.SIZE + 1];
      w[0] |= letters.length();
      w[0] |= alphabet.getIndex(letters.charAt(0), force) << bitsPerUnit;
      for (int i = 0; i < letters.length(); i++) {
        Character letter = letters.charAt(i);
        int index = alphabet.getIndex(letter, force);
        int startIndex = (i + offset) * bitsPerUnit;
        w[startIndex / Long.SIZE] |= ((long)index) << (startIndex % Long.SIZE);
        if (startIndex % Long.SIZE + bitsPerUnit > Long.SIZE) {
          w[startIndex / Long.SIZE + 1] |= ((long)index) >>> (Long.SIZE - startIndex % Long.SIZE);
        }
      }
      bs.setWords(w);
      return bs;
    }
  */

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