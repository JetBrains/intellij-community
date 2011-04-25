/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.util.Consumer;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class CompressedDictionary implements Dictionary {

  private final Alphabet alphabet;
  private int wordsCount;
  private byte[][] words;
  private int[] lengths;

  private final Encoder encoder;
  private final String name;

  private TIntObjectHashMap<SortedSet<byte[]>> rawData = new TIntObjectHashMap<SortedSet<byte[]>>();
  private static final Comparator<byte[]> COMPARATOR = new Comparator<byte[]>() {
    public int compare(byte[] o1, byte[] o2) {
      return compareArrays(o1, o2);
    }
  };

  CompressedDictionary(@NotNull Alphabet alphabet, @NotNull Encoder encoder, @NotNull String name) {
    this.alphabet = alphabet;
    this.encoder = encoder;
    this.name = name;
  }

  void addToDictionary(byte[] word) {
    SortedSet<byte[]> set = rawData.get(word.length);
    if (set == null) {
      set = createSet();
      rawData.put(word.length, set);
    }
    set.add(word);
    wordsCount++;
  }

  void pack() {
    lengths = new int[rawData.size()];
    words = new byte[rawData.size()][];
    rawData.forEachEntry(new TIntObjectProcedure<SortedSet<byte[]>>() {
      int row = 0;
      @Override
      public boolean execute(int l, SortedSet<byte[]> value) {
        lengths[row] = l;
        words[row] = new byte[value.size() * l];
        int k = 0;
        for (byte[] bytes : value) {
          for (byte aByte : bytes) {
            words[row][k++] = aByte;
          }
        }
        row++;
        return true;
      }
    });
    rawData = null;
  }

  private static SortedSet<byte[]> createSet() {
    return new TreeSet<byte[]>(COMPARATOR);

  }

  public List<String> getWords(char first, int minLength, int maxLength) {
    int index = alphabet.getIndex(first, false);
    List<String> result = new ArrayList<String>();
    if (index == -1) {
      return result;
    }
    int i = 0;
    for (byte[] data : words) {
      int length = lengths[i];
      for (int x = 0; x < data.length; x += length) {
        byte[] toTest = new byte[length];
        System.arraycopy(data, x, toTest, 0, length);
        if (toTest[1] != index || toTest[0] > maxLength || toTest[0] < minLength) {
          continue;
        }
        UnitBitSet set = UnitBitSet.create(toTest);
        result.add(encoder.decode(set));
      }
      i++;
    }
    return result;
  }

  public List<String> getWords(char first) {
    return getWords(first, 0, Integer.MAX_VALUE);
  }

  public String getName() {
    return name;
  }

  public boolean contains(String word) {
    if (word == null) {
      return false;
    }
    try {
      UnitBitSet bs = encoder.encode(word, false);
      if (bs == Encoder.WORD_OF_ENTIRELY_UNKNOWN_LETTERS) return false;  // TODO: polyglot.dic contains word typppo , wtf ?! also make tests for your changes!!!
      if (bs == null) return false; // fail faster w/o search
      byte[] compressed = UnitBitSet.getBytes(bs);
      int index = -1;
      for (int i = 0; i < lengths.length; i++) {
        if (lengths[i] == compressed.length) {
          index = i;
          break;
        }
      }
      return index != -1 && contains(compressed, words[index]);
    }
    catch (EncodingException ignored) {
      return false;
    }

  }

  public boolean isEmpty() {
    return wordsCount <= 0;
  }

  public void traverse(Consumer<String> action) {
    throw new UnsupportedOperationException();
  }

  public Set<String> getWords() {
    throw new UnsupportedOperationException();
  }

  public int getAlphabetLength() {
    return alphabet.getLastIndexUsed();
  }

  public int size() {
    return wordsCount;
  }


  public String toString() {
    @NonNls StringBuilder sb = new StringBuilder();
    sb.append("CompressedDictionary");
    sb.append("{wordsCount=").append(wordsCount);
    sb.append(", name='").append(name).append('\'');
    sb.append('}');
    return sb.toString();
  }

  public static CompressedDictionary create(@NotNull Loader loader, @NotNull final Transformation transform) {
    Alphabet alphabet = new Alphabet();
    final Encoder encoder = new Encoder(alphabet);
    final CompressedDictionary dictionary = new CompressedDictionary(alphabet, encoder, loader.getName());
    loader.load(new Consumer<String>() {
      public void consume(String s) {
        String transformed = transform.transform(s);
        if (transformed != null) {
          UnitBitSet bs = encoder.encode(transformed, true);
          if (bs == null) return;
          byte[] compressed = UnitBitSet.getBytes(bs);
          dictionary.addToDictionary(compressed);
        }
      }
    });
    dictionary.pack();
    return dictionary;
  }

  public static int compareArrays(byte[] array1, byte[] array2) {
    if (array1.length != array2.length) {
      return array1.length < array2.length ? -1 : 1;
    }
    //compare elements values
    for (int i = 0; i < array1.length; i++) {
      if (array1[i] < array2[i]) {
        return -1;
      }
      else if (array1[i] > array2[i]) {
        return 1;
      }
    }
    return 0;
  }


  public static boolean contains(byte[] goal, byte[] data) {
    return binarySearchNew(goal, 0, data.length / goal.length, data) >= 0;
  }

  public static int binarySearchNew(byte[] goal, int fromIndex, int toIndex, byte[] data) {
    int unitLength = goal.length;
    int low = fromIndex;
    int high = toIndex - 1;
    while (low <= high) {
      int mid = low + high >>> 1;
      byte[] toTest = new byte[unitLength];
      System.arraycopy(data, mid * unitLength, toTest, 0, unitLength);
      int check = compareArrays(toTest, goal);
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
