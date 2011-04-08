package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;
import com.jetbrains.python.psi.PyStringLiteralExpression;

/**
 * @author yole
 */
public class PyStringLiteralTest extends PyLightFixtureTestCase {
  public void testLiteralEscaper() {
    PyStringLiteralExpression expr = createLiteralFromText("'\\nfoo'");
    assertNotNull(expr);
    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
    StringBuilder builder = new StringBuilder();
    escaper.decode(new TextRange(3, 5), builder);
    assertEquals("fo", builder.toString());
    
    builder.setLength(0);
    escaper.decode(new TextRange(1, 3), builder);
    assertEquals("\n", builder.toString());

    assertEquals(1, escaper.getOffsetInHost(0, new TextRange(1, 5)));
    assertEquals(3, escaper.getOffsetInHost(1, new TextRange(1, 5)));
    assertEquals(6, escaper.getOffsetInHost(4, new TextRange(1, 5)));
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
