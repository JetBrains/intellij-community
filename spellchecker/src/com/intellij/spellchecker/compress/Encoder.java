/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.spellchecker.compress;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Encoder {
  private final Alphabet alphabet;
  private static final int offset = 0;
  static final UnitBitSet WORD_OF_ENTIRELY_UNKNOWN_LETTERS = new UnitBitSet(new byte[1],new Alphabet());
  private static final Logger LOG = Logger.getInstance("#com.intellij.spellchecker.compress");

  public Encoder() {
    this(new Alphabet());
  }

  public Encoder(@NotNull Alphabet alphabet) {
    this.alphabet = alphabet;
  }

  public Alphabet getAlphabet() {
    return alphabet;
  }

  @Nullable
  public UnitBitSet encode(@NotNull CharSequence letters, boolean force) {
    if (UnitBitSet.MAX_CHARS_IN_WORD <= letters.length() + offset) return null;
    int unknownLetters = 0;
    byte[] indices = new byte[letters.length()];
    for (int i = 0; i < letters.length(); i++) {
      char letter = letters.charAt(i);
      int index = alphabet.getIndex(letter, force);
      if (index < 0) {
        unknownLetters++;
      }
      else {
        indices[i] = (byte)index;
      }
    }
    if (unknownLetters == letters.length()) return WORD_OF_ENTIRELY_UNKNOWN_LETTERS;
    if (unknownLetters>0) return null;
    return new UnitBitSet(indices, alphabet);
  }

  @NotNull
  public String decode(@NotNull byte[] compressed) {
    return UnitBitSet.decode(compressed, alphabet);
  }

  @NotNull
  public String decode(@NotNull byte[] data, int from, int to) {
    return UnitBitSet.decode(data, from, to, alphabet);
  }

  public int getFirstLetterIndex(byte firstPackedByte) {
    return UnitBitSet.getFirstLetterIndex(firstPackedByte, alphabet);
  }
}