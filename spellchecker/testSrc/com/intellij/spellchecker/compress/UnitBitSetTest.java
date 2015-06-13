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

public class UnitBitSetTest {
  @Test
  public void testUnitValue() {
    int bitsPerUnit = 256;
    for (int i = 0; i < bitsPerUnit - 1; i++) {
      UnitBitSet bs = new UnitBitSet(new byte[2], new Alphabet());
      bs.setUnitValue(0, i);
      assertEquals(i, bs.getUnitValue(0));
      assertEquals(0, bs.getUnitValue(1));
    }
  }
}
