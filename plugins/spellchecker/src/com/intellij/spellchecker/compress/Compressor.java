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

import com.intellij.util.Consumer;

import java.util.ArrayList;
import java.util.List;

public final class Compressor {

  public static int WORD_LENGTH = 8;
  public static byte TRANSITION_WORD = new Double(-Math.pow(2, WORD_LENGTH - 1)).byteValue();
  public final int skipFirst;

  
  public Compressor(int skipFirst) {
    this.skipFirst = skipFirst;
  }


  public UnitBitSet decompress(byte[] value) {
    UnitBitSet bs = new UnitBitSet(WORD_LENGTH - 1, true);
    int index = 0;
    int bitsPerUnitInOrigin = value[skipFirst];

    for (int i = skipFirst + 1; i < value.length; i++) {
      byte b = value[i];
      if (b > 0) {
        //decompress verbose word
        bs.setUnitValue(index++, b);
      }
      else {
        int count = (-TRANSITION_WORD + b);
        index = index + count + 1;
      }

    }
    UnitBitSet result = UnitBitSet.create(bs, bitsPerUnitInOrigin);
    result.moveRight(skipFirst);
    for (int i = 0; i < skipFirst; i++) {
      result.setUnitValue(i, value[i]);
    }

    return result;
  }

  public byte[] compress(UnitBitSet origin) {
    if (origin == null) {
      return new byte[0];
    }
    final List<Byte> words = new ArrayList<Byte>();
    final List<Byte> compressed = new ArrayList<Byte>();

    final UnitBitSet copyOfOrigin = UnitBitSet.create(origin, origin.bitsPerUnit);

    for (int i = 0; i < skipFirst; i++) {
      compressed.add(Integer.valueOf(copyOfOrigin.getUnitValue(i)).byteValue());
    }
    copyOfOrigin.moveLeft(skipFirst);

    compressed.add(Integer.valueOf(copyOfOrigin.bitsPerUnit).byteValue());

    UnitBitSet bs = UnitBitSet.create(copyOfOrigin, WORD_LENGTH - 1);
    bs.iterateParUnits(new Consumer<Integer>() {

      public void consume(Integer integer) {
        if (integer != 0) {
          //create verbatim word
          words.add(integer.byteValue());
        }
        else {
          //create transition word
          words.add(TRANSITION_WORD);
        }

      }
    }, 0, true);


    int count = -1;
    for (Byte word : words) {
      if (word == TRANSITION_WORD && count == -1) {
        count++;
      }
      else if (word == TRANSITION_WORD && count > -1) {
        count++;
      }
      else if (word != TRANSITION_WORD && count > -1) {
        //compress all transition words
        while (count > 120) {
          compressed.add((byte)(TRANSITION_WORD + 120));
          count -= 121;
        }
        if (count > -1) {
          compressed.add((byte)(TRANSITION_WORD + count));
        }
        compressed.add(word);
        count = -1;
      }
      else if (word != TRANSITION_WORD && count == -1) {
        compressed.add(word);
        count = -1;
      }
    }
    byte[] result = new byte[compressed.size()];
    int i = 0;
    for (Byte word : compressed) {
      result[i++] = word;
    }
    return result;
  }


}
