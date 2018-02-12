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

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.codeInsight.fstrings.FStringParser;
import com.jetbrains.python.codeInsight.fstrings.FStringParser.Fragment;
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
    final Pair<String, List<Integer>> pair = extractOffsets(fstringLiteral);
    final List<Integer> barOffsets = pair.getSecond();
    assertTrue("Odd number of markers", barOffsets.size() % 2 == 0);
    final List<Fragment> fragments = FStringParser.parse(pair.getFirst()).getFragments();
    final List<TextRange> actualRanges = ContainerUtil.map(fragments, Fragment::getContentRange);
    final List<TextRange> expectedRanges = new ArrayList<>();
    for (int i = 0; i < barOffsets.size(); i += 2) {
      expectedRanges.add(TextRange.create(barOffsets.get(i), barOffsets.get(i + 1)));
    }
    assertSameElements(actualRanges, expectedRanges);
  }

  private static void doTestSingleRightBraces(@NotNull @Language("Python") String fstringLiteral) {
    final Pair<String, List<Integer>> pair = extractOffsets(fstringLiteral);
    assertEquals(pair.getSecond(), FStringParser.parse(pair.getFirst()).getSingleRightBraces());
  }

  @NotNull
  private static Pair<String, List<Integer>> extractOffsets(@NotNull String fstringLiteral) {
    final StringBuilder builder = new StringBuilder();
    int lastBarOffset = -1;
    final List<Integer> offsets = new ArrayList<>();
    while (true) {
      final int nextOffset = fstringLiteral.indexOf("|", lastBarOffset + 1);
      if (nextOffset < 0) {
        builder.append(fstringLiteral.substring(lastBarOffset + 1));
        break;
      }
      builder.append(fstringLiteral.substring(lastBarOffset + 1, nextOffset));
      lastBarOffset = nextOffset;
      offsets.add(lastBarOffset - offsets.size());
    }
    return Pair.create(builder.toString(), offsets);
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
    doTestRanges("f'''{|\"\"\"}'\"}'\"\"\"|}'''");
    doTestRanges("f\"{|'''}\\\"'\\\"'''|}\"");
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

  // PY-20785
  public void testNamedUnicodeEscapes() {
    doTestRanges("f'\\N{foo}\\N{}\\N{{{{{}{|42 + \\N{DIGIT ONE}|}'");
    doTestRanges("f'{|x|:\\N{DIGIT_ONE}}'");
  }

  // PY-20785
  public void testUnicodeEscapeInsideExpressionFragment() {
    doTestUnicodeEscapeDetection("f'{\\N{FOO}}'", true);
    doTestUnicodeEscapeDetection("f'{\"\\N{FOO\"}'", true);
    doTestUnicodeEscapeDetection("f'{\"\\N{\"}'", true);
    doTestUnicodeEscapeDetection("f'{\"\\\\N{FOO}\"}'", false);
  }

  private static void doTestUnicodeEscapeDetection(String fStringText, boolean expected) {
    final List<Fragment> fragments = FStringParser.parse(fStringText).getFragments();
    assertSize(1, fragments);
    final Fragment offsets = fragments.get(0);
    assertEquals(expected, offsets.containsNamedUnicodeEscape());
  }

  // PY-20897
  public void testSingleRightBraces() {
    doTestSingleRightBraces("f'|}'");
    doTestSingleRightBraces("f'{x}{y}|}'");
    doTestSingleRightBraces("f'{x}}}|}'");
    doTestSingleRightBraces("f'{x:{y}}|}'");
    doTestSingleRightBraces("f'{x:{y}}}}'");
    doTestSingleRightBraces("f'{\"}\":{\"}\"}}'");
  }
}
