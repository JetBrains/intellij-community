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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EncodeAndCompressTest {
  @Test
  public void testEncodeAndCompress() {
    Encoder encoder = new Encoder();
    String word = "example";
    UnitBitSet bs = encoder.encode(word, true);
    assertNotNull(bs);
    byte[] compressed = bs.pack();
    String decompressed = UnitBitSet.decode(compressed, encoder.getAlphabet());
    assertEquals(word, decompressed);
    String restored = encoder.decode(compressed);
    assertEquals(word, restored);
  }
}
