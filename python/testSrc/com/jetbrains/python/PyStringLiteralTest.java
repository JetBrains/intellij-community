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
    final PsiFile file = PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("test.py", "a = '\\nfoo");
    PyStringLiteralExpression expr = PsiTreeUtil.getParentOfType(file.findElementAt(5), PyStringLiteralExpression.class);
    assertNotNull(expr);
    final LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper = expr.createLiteralTextEscaper();
    StringBuilder builder = new StringBuilder();
    escaper.decode(new TextRange(3, 5), builder);
    assertEquals("fo", builder.toString());
    
    builder.setLength(0);
    escaper.decode(new TextRange(1, 3), builder);
    assertEquals("\n", builder.toString());

    assertEquals(1, escaper.getOffsetInHost(0, new TextRange(0, 5)));
    assertEquals(3, escaper.getOffsetInHost(1, new TextRange(0, 5)));
    assertEquals(4, escaper.getOffsetInHost(2, new TextRange(0, 5)));

  }
}
