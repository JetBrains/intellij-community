/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.codeInsight.PyFStringsInjector;
import com.jetbrains.python.fixtures.PyTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class PyFStringTest extends PyTestCase {

  private static void doTestRanges(@NotNull @Language("Python") String fstringLiteral) {
    final StringBuilder originalString = new StringBuilder();
    int lastBarOffset = -1;
    final List<Integer> barOffsets = new ArrayList<>();
    while (true) {
      final int nextOffset = fstringLiteral.indexOf("|", lastBarOffset + 1);
      if (nextOffset < 0) {
        originalString.append(fstringLiteral.substring(lastBarOffset + 1));
        break;
      }
      originalString.append(fstringLiteral.substring(lastBarOffset + 1, nextOffset));
      lastBarOffset = nextOffset;
      barOffsets.add(lastBarOffset - barOffsets.size());
    }
    assertTrue("Odd number of markers", barOffsets.size() % 2 == 0);
    final List<TextRange> actualRanges = PyFStringsInjector.getInjectionRanges(originalString.toString());
    final List<TextRange> expectedRanges = new ArrayList<>();
    for (int i = 0; i < barOffsets.size(); i += 2) {
      expectedRanges.add(TextRange.create(barOffsets.get(i), barOffsets.get(i + 1)));
    }
    assertSameElements(actualRanges, expectedRanges);
  }

  public void testSimple() {
    doTestRanges("f'{|x|} {|y|} {|42|'");
  }

  public void testEscapedBraces() {
    doTestRanges("f'{{{|x|}}}{{}}{{{|x|}'");
  }

  public void testBracesInside() {
    doTestRanges("f'{| {1, 2, 3} |} {| {x for x in range(10)} |}'");
  }

  public void testBraceInsideNestedLiteral() {
    doTestRanges("f'{|\"{x}}}\"|}'");
    doTestRanges("f'{|d[\"}\"]|}'");
    doTestRanges("f'''{|\"'}\"|}'''");
    doTestRanges("f'''{|\"\\\"}\"|}'''");
  }

  public void testChunkTypeConversionsAndFormatSpecifiers() {
    doTestRanges("f'{|x|!s} {|y|!r}}'");
    doTestRanges("f'{|x|:^42}}'");
    doTestRanges("f'{|x|!s:{|y|}.{|z|}}'");
    doTestRanges("f'{|x|:{|sum({x for x in range(10)})|}}'");
  }

  public void testNestedLambda() {
    doTestRanges("f'{|(lambda: x)|}'");
  }

  public void testNotEquals() {
    doTestRanges("f'{|x != 42|}'");
  }
}
