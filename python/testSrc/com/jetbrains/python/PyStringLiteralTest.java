// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class PyStringLiteralTest extends PyTestCase {
  public void testEscaperDecode() {
    final PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'");
    assertNotNull(expr);
    assertEquals("fo", decodeRange(expr, TextRange.create(3, 5)));
    assertEquals("\n", decodeRange(expr, TextRange.create(1, 3)));
  }

  public void testEscaperOffsetInHost() {
    final PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'");
    assertNotNull(expr);
    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
    final TextRange newLineFoo = TextRange.create(1, 6);
    assertEquals(1, escaper.getOffsetInHost(0, newLineFoo));
    assertEquals(3, escaper.getOffsetInHost(1, newLineFoo));
    assertEquals(4, escaper.getOffsetInHost(2, newLineFoo));
    assertEquals(5, escaper.getOffsetInHost(3, newLineFoo));
    assertEquals(6, escaper.getOffsetInHost(4, newLineFoo));
    assertEquals(-1, escaper.getOffsetInHost(5, newLineFoo));
  }

  public void testEscaperOffsetInHostSubString() {
    final PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'");
    assertNotNull(expr);
    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
    final TextRange fooOnly = TextRange.create(3, 6);
    assertEquals(3, escaper.getOffsetInHost(0, fooOnly));
    assertEquals(4, escaper.getOffsetInHost(1, fooOnly));
    assertEquals(5, escaper.getOffsetInHost(2, fooOnly));
    assertEquals(6, escaper.getOffsetInHost(3, fooOnly));
    assertEquals(-1, escaper.getOffsetInHost(4, fooOnly));
  }

  public void testEscaperOffsetInSingleCharString() {
    final PyStringLiteralExpression expr = createLiteralFromText("'c'");
    assertNotNull(expr);
    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
    final TextRange range = TextRange.create(1, 2);
    assertEquals(1, escaper.getOffsetInHost(0, range));
    assertEquals(2, escaper.getOffsetInHost(1, range));
    assertEquals(-1, escaper.getOffsetInHost(2, range));
  }

  public void testEscaperOffsetInSingleEscapedCharString() {
    final PyStringLiteralExpression expr = createLiteralFromText("'\\n'");
    assertNotNull(expr);
    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
    final TextRange range = TextRange.create(1, 3);
    assertEquals(1, escaper.getOffsetInHost(0, range));
    assertEquals(3, escaper.getOffsetInHost(1, range));
    assertEquals(-1, escaper.getOffsetInHost(2, range));
  }

  public void testIterateCharacterRanges() {
    assertSameElements(getCharacterRanges("'\\nfoo'  'bar'"),
                       Arrays.asList("\n", "foo", "bar"));
  }

  public void testIterateEscapedBackslash() {
    assertSameElements(getCharacterRanges("""
                                            '''
                                            foo.\\\\
                                            bar
                                            '''
                                            """),
                       Arrays.asList("\nfoo.", "\\", "\nbar\n"));
  }

  public void testEscaperOffsetInEscapedBackslash() {
    final PyStringLiteralExpression expr = createLiteralFromText("'XXX foo.\\\\bar YYY'");
    assertNotNull(expr);
    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
    final TextRange range = TextRange.create(5, 14);
    assertEquals(5, escaper.getOffsetInHost(0, range));
    assertEquals(6, escaper.getOffsetInHost(1, range));
    assertEquals(7, escaper.getOffsetInHost(2, range));
    assertEquals(8, escaper.getOffsetInHost(3, range));
    assertEquals(9, escaper.getOffsetInHost(4, range));
    assertEquals(11, escaper.getOffsetInHost(5, range));
    assertEquals(12, escaper.getOffsetInHost(6, range));
    assertEquals(13, escaper.getOffsetInHost(7, range));
    assertEquals(14, escaper.getOffsetInHost(8, range));
    assertEquals(-1, escaper.getOffsetInHost(9, range));
  }

  public void testEscaperOffsetInLongUnicodeEscape() {
    final PyStringLiteralExpression expr = createLiteralFromText("u'XXX a\\U0001F600\\U0001F600b YYY'");
    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
    final TextRange range = TextRange.create(6, 28);
    assertEquals(6, escaper.getOffsetInHost(0, range));
    assertEquals(7, escaper.getOffsetInHost(1, range));
    // Each \\U0001F600 is represented as a surrogate pair, hence 2 characters-wide step in decoded text
    assertEquals(17, escaper.getOffsetInHost(3, range));
    assertEquals(27, escaper.getOffsetInHost(5, range));
    assertEquals(28, escaper.getOffsetInHost(6, range));
    assertEquals(-1, escaper.getOffsetInHost(7, range));
  }

  public void testStringValue() {
    assertEquals("foo", createLiteralFromText("\"\"\"foo\"\"\"").getStringValue());
    assertEquals("foo", createLiteralFromText("u\"foo\"").getStringValue());
    assertEquals("foo", createLiteralFromText("b\"foo\"").getStringValue());
    assertEquals("\\b", createLiteralFromText("r'\\b'").getStringValue());
    assertEquals("b\\n", createLiteralFromText("ur'\\u0062\\n'").getStringValue());
    assertEquals("\\8", createLiteralFromText("'\\8'").getStringValue());
  }

  public void testEscapedUnicodeInLiterals() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      assertEquals("\\u0041", createLiteralFromText("'\\u0041'").getStringValue());
      assertEquals("A", createLiteralFromText("u'\\u0041'").getStringValue());
      assertEquals("\\u0041", createLiteralFromText("b'\\u0041'").getStringValue());
    });
  }

  public void testNonUnicodeCodePointValue() {
    assertEquals("\\U12345678", createLiteralFromText("u'\\U12345678'").getStringValue());
  }

  public void testFStringEscapes() {
    assertEquals("{", createLiteralFromText("f'{{'").getStringValue());
    assertEquals("}", createLiteralFromText("f'}}'").getStringValue());
    assertEquals("{{foo}}", createLiteralFromText("f'{{{foo}}}'").getStringValue());
    assertEquals("\n{foo}\r\"", createLiteralFromText("f'\\n{foo}\\r\"'").getStringValue());
  }

  public void testFStringDecodedRanges() {
    assertContainsOrdered(getCharacterRanges("f'foo\"bar'"), "foo\"bar");
    assertContainsOrdered(getCharacterRanges("f'foo\\'bar'"), "foo", "'", "bar");
    assertContainsOrdered(getCharacterRanges("f'foo\\{bar}'"), "foo\\", "{bar}");
    assertContainsOrdered(getCharacterRanges("f'''foo\nbar'''"), "foo\nbar");
  }

  @NotNull
  private static String decodeRange(@NotNull PyStringLiteralExpression expr, @NotNull TextRange range) {
    final StringBuilder builder = new StringBuilder();
    expr.createLiteralTextEscaper().decode(range, builder);
    return builder.toString();
  }

  @NotNull
  private PyStringLiteralExpression createLiteralFromText(@NotNull String text) {
    final PsiFile file = PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("test.py", PythonFileType.INSTANCE, "a = (" + text + ")");
    final PyStringLiteralExpression expr = PsiTreeUtil.getParentOfType(file.findElementAt(6), PyStringLiteralExpression.class);
    assert expr != null;
    return expr;
  }

  @NotNull
  private List<String> getCharacterRanges(@NotNull String text) {
    final PyStringLiteralExpression expr = createLiteralFromText(text);
    assertNotNull(expr);
    final List<String> characters = new ArrayList<>();
    for (Pair<TextRange, String> fragment : expr.getDecodedFragments()) {
      characters.add(fragment.getSecond());
    }
    return characters;
  }

  public void testRichStringNodes() {
    final PyStringLiteralExpression string = createLiteralFromText("'foo' 'bar' 'baz'");
    assertSize(3, string.getStringElements());
  }

  public void testInterpolationDetection() {
    assertFalse(createLiteralFromText("'''foo'''").isInterpolated());
    assertFalse(createLiteralFromText("f'foo'").isInterpolated());
    assertTrue(createLiteralFromText("f'foo{}'").isInterpolated());
    assertTrue(createLiteralFromText("f'foo{42}' 'bar'").isInterpolated());
    assertTrue(createLiteralFromText("f'foo{42}'").isInterpolated());
  }
}
