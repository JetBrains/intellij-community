/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.toolbox;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Tests basic FP stuff.
 * User: dcheryasov
 * Date: Nov 20, 2009 6:46:25 AM
 */
public class FPTest extends TestCase {

  public void testMap() {
    List<String> sequence = Arrays.asList("a", "b", "c");
    FP.Lambda1<String, String> func = new FP.Lambda1<String, String>() {
      @Override
      public String apply(String arg) {
        return arg.toUpperCase();
      }
    };

    int count = 0;
    for (String what : FP.map(func, sequence)) {
      assertEquals(sequence.get(count).toUpperCase(), what);
      count += 1;
    }
    assertEquals(sequence.size(), count);
  }

  public void testMapEmpty() {
    List<String> sequence = Arrays.asList();
    FP.Lambda1<String, String> func = new FP.Lambda1<String, String>() {
      @Override
      public String apply(String arg) {
        return arg.toUpperCase();
      }
    };

    int count = 0;
    for (String what : FP.map(func, sequence)) {
      count += 1; // this never happens
    }
    assertEquals(sequence.size(), count);
  }

  
  public void testMapLazy() {
    List<Float> sequence = Arrays.asList(1.0f, 2.0f, 3.0f, 0.0f);
    FP.Lambda1<Float, Float> func = new FP.Lambda1<Float, Float>() {
      @Override
      public Float apply(Float arg) {
        return 1.0f/arg;
      }
    };

    int count = 0;
    Iterator<Float> iterator = FP.map(func, sequence).iterator();
    while (iterator.hasNext() && count < 3) { // func is applied on the fly and will not be calculated for 4th arg
      iterator.next();
      count += 1;
    }
    assertEquals(3, count);
  }
  

  public void testFold() {
    List<String> sequence = Arrays.asList("a", "b", "c");
    FP.Lambda2<StringBuilder, String, StringBuilder> adder = new FP.Lambda2<StringBuilder, String, StringBuilder>() {
      @Override
      public StringBuilder apply(StringBuilder builder, String arg2) {
        return builder.append(arg2);
      }
    };
    StringBuilder result = FP.fold(adder, sequence, new StringBuilder());
    assertEquals("abc", result.toString());
  }

  public void testFoldr() {
    List<String> sequence = Arrays.asList("a", "b", "c");
    FP.Lambda2<String, String, String> adder = new FP.Lambda2<String, String, String>() {
      @Override
      public String apply(String left, String right) {
        return left + right;
      }
    };
    String result = FP.foldr(adder, sequence, "");
    assertEquals("cba", result);
  }

}
