/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.spellchecker.dictionary.Dictionary;
import com.intellij.spellchecker.dictionary.Loader;
import com.intellij.spellchecker.engine.Transformation;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class CompressedDictionary implements Dictionary {
  private final Alphabet alphabet;
  private int wordsCount;
  private byte[][] words;
  private int[] lengths;

  private final Encoder encoder;
  private final String name;

  private TIntObjectHashMap<SortedSet<byte[]>> rawData = new TIntObjectHashMap<>();
  private static final Comparator<byte[]> COMPARATOR = (o1, o2) -> compareArrays(o1, o2);

  private CompressedDictionary(@NotNull Alphabet alphabet, @NotNull Encoder encoder, @NotNull String name) {
    this.alphabet = alphabet;
    this.encoder = encoder;
    this.name = name;
  }

  private void addToDictionary(@NotNull byte[] word) {
    SortedSet<byte[]> set = rawData.get(word.length);
    if (set == null) {
      set = createSet();
      rawData.put(word.length, set);
    }
    set.add(word);
    wordsCount++;
  }

  private void pack() {
    lengths = new int[rawData.size()];
    words = new byte[rawData.size()][];
    rawData.forEachEntry(new TIntObjectProcedure<SortedSet<byte[]>>() {
      int row = 0;
      @Override
      public boolean execute(int length, SortedSet<byte[]> value) {
        lengths[row] = length;
        words[row] = new byte[value.size() * length];
        int k = 0;
        byte[] wordBytes = words[row];
        for (byte[] bytes : value) {
          assert bytes.length == length;
          System.arraycopy(bytes, 0, wordBytes, k, bytes.length);
          k += bytes.length;
        }
        row++;
        return true;
      }
    });
    rawData = null;
  }

  @NotNull
  private static SortedSet<byte[]> createSet() {
    return new TreeSet<>(COMPARATOR);
  }

  /** @deprecated use {@link #getWords(char, int, int, Collection)} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public List<String> getWords(char first, int minLength, int maxLength) {
    List<String> result = new ArrayList<>();
    getWords(first, minLength, maxLength, result);
    return result;
  }

  /** @deprecated use {@link #getWords(char, int, int, Collection)} (to be removed in IDEA 17) */
  @SuppressWarnings("unused")
  public List<String> getWords(char first) {
    List<String> result = new ArrayList<>();
    getWords(first, 0, Integer.MAX_VALUE, result);
    return result;
  }

  public void getWords(char first, int minLength, int maxLength, @NotNull Collection<String> result) {
    int index = alphabet.getIndex(first, false);
    if (index == -1) return;

    int i = 0;
    for (byte[] data : words) {
      int length = lengths[i];
      if (length < minLength || length > maxLength) continue;
      for (int x = 0; x < data.length; x += length) {
        if (encoder.getFirstLetterIndex(data[x]) == index) {
          byte[] toTest = new byte[length];
          System.arraycopy(data, x, toTest, 0, length);
          String decoded = encoder.decode(toTest);
          result.add(decoded);
        }
      }
      i++;
    }
  }

  @NotNull
  @Override
  public String getName() {
    return name;
  }

  @Override
  @Nullable
  public Boolean contains(@NotNull String word) {
    UnitBitSet bs = encoder.encode(word, false);
    if (bs == Encoder.WORD_OF_ENTIRELY_UNKNOWN_LETTERS) return null;
    if (bs == null) return false;
      //TODO throw new EncodingException("WORD_WITH_SOME_UNKNOWN_LETTERS");
    byte[] compressed = bs.pack();
    int index = ArrayUtil.indexOf(lengths, compressed.length);
    return index != -1 && contains(compressed, words[index]);
  }

  @Override
  public boolean isEmpty() {
    return wordsCount <= 0;
  }

  @Override
  public void traverse(@NotNull Consumer<String> action) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> getWords() {
    Set<String> words = new THashSet<>();
    for (int i = 0; i <= alphabet.getLastIndexUsed(); i++) {
      char letter = alphabet.getLetter(i);
      getWords(letter, 0, Integer.MAX_VALUE, words);
    }
    return words;
  }

  @Override
  public int size() {
    return wordsCount;
  }


  @Override
  public String toString() {
    return "CompressedDictionary" + "{wordsCount=" + wordsCount + ", name='" + name + '\'' + '}';
  }

  @NotNull
  public static CompressedDictionary create(@NotNull Loader loader, @NotNull final Transformation transform) {
    Alphabet alphabet = new Alphabet();
    final Encoder encoder = new Encoder(alphabet);
    final CompressedDictionary dictionary = new CompressedDictionary(alphabet, encoder, loader.getName());
    final List<UnitBitSet> bss = new ArrayList<>();
    loader.load(s -> {
      String transformed = transform.transform(s);
      if (transformed != null) {
        UnitBitSet bs = encoder.encode(transformed, true);
        if (bs == null) return;
        bss.add(bs);
      }
    });
    for (UnitBitSet bs : bss) {
      byte[] compressed = bs.pack();
      dictionary.addToDictionary(compressed);
    }
    dictionary.pack();
    return dictionary;
  }

  public static int compareArrays(@NotNull byte[] array1, @NotNull byte[] array2) {
    return compareArrays(array1, 0, array1.length, array2);
  }
  private static int compareArrays(@NotNull byte[] array1, int start1, int length1, @NotNull byte[] array2) {
    if (length1 != array2.length) {
      return length1 < array2.length ? -1 : 1;
    }
    //compare elements values
    for (int i = 0; i < length1; i++) {
      int d = array1[i+start1] - array2[i];
      if (d < 0) {
        return -1;
      }
      else if (d > 0) {
        return 1;
      }
    }
    return 0;
  }


  public static boolean contains(@NotNull byte[] goal, @NotNull byte[] data) {
    return binarySearchNew(goal, 0, data.length / goal.length, data) >= 0;
  }

  public static int binarySearchNew(@NotNull byte[] goal, int fromIndex, int toIndex, @NotNull byte[] data) {
    int unitLength = goal.length;
    int low = fromIndex;
    int high = toIndex - 1;
    while (low <= high) {
      int mid = low + high >>> 1;
      int check = compareArrays(data, mid * unitLength, unitLength, goal);
      if (check == -1) {
        low = mid + 1;
      }
      else if (check == 1) {
        high = mid - 1;
      }
      else {
        return mid;
      }
    }
    return -(low + 1);  // key not found.
  }
}
