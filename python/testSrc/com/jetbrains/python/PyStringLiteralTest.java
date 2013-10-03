package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * @author yole
 */
public class PyStringLiteralTest extends PyTestCase {
  public void testLiteralEscaper() {
    final PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'");
    assertNotNull(expr);

    assertEquals("fo", decodeRange(expr, TextRange.create(3, 5)));
    assertEquals("\n", decodeRange(expr, TextRange.create(1, 3)));

    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();

    final TextRange newLineFoo = TextRange.create(1, 6);
    assertEquals(1, escaper.getOffsetInHost(0, newLineFoo));
    assertEquals(3, escaper.getOffsetInHost(1, newLineFoo));
    assertEquals(6, escaper.getOffsetInHost(4, newLineFoo));
  }

  private static String decodeRange(PyStringLiteralExpression expr, TextRange range) {
    final StringBuilder builder = new StringBuilder();
    expr.createLiteralTextEscaper().decode(range, builder);
    return builder.toString();
  }

  private PyStringLiteralExpression createLiteralFromText(final String text) {
    final PsiFile file = PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("test.py", "a = " + text);
    final PyStringLiteralExpression expr = PsiTreeUtil.getParentOfType(file.findElementAt(5), PyStringLiteralExpression.class);
    assert expr != null;
    return expr;
  }

  public void testStringValue() {
    assertEquals("foo", createLiteralFromText("\"\"\"foo\"\"\"").getStringValue());
    assertEquals("foo", createLiteralFromText("u\"foo\"").getStringValue());
    assertEquals("foo", createLiteralFromText("b\"foo\"").getStringValue());
    assertEquals("\\b", createLiteralFromText("r'\\b'").getStringValue());
    assertEquals("b\\n", createLiteralFromText("ur'\\u0062\\n'").getStringValue());
    assertEquals("\\8", createLiteralFromText("'\\8'").getStringValue());
  }
}
