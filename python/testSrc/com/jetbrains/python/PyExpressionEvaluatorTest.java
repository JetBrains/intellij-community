package com.jetbrains.python;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyExpressionEvaluator;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;

/**
 * Tests {@link com.jetbrains.python.psi.impl.PyExpressionEvaluator}
 *
 * @author Ilya.Kazakevich
 */
public class PyExpressionEvaluatorTest extends PyTestCase {

  public void testLiteralReferences() {
    final PyTargetExpression myValue = createFileAndExpression("my_value",
                                                               "x = 2",
                                                               "y = x",
                                                               "my_value = y"
    );
    final List<PyNumericLiteralExpression> result = PyExpressionEvaluator.evaluateRaw(myValue, PyNumericLiteralExpression.class);
    Assert.assertThat("Expected list of 1 long element", result.size(), Matchers.is(1));
    Assert.assertNotNull("Failed to evaluate chain of literals (got null)", result);
    final PyNumericLiteralExpression resultElement = result.get(0);
    Assert.assertThat("Evaluated incorrectly", resultElement.getLongValue(), Matchers.equalTo(2L));
  }


  public void testCall() {
    final PyTargetExpression myValue = createFileAndExpression("my_value",
                                                               "x = abs(42)",
                                                               "y = x",
                                                               "my_value = y"
    );
    final List<PyCallExpression> result = PyExpressionEvaluator.evaluateRaw(myValue, PyCallExpression.class);
    Assert.assertNotNull("Failed to evaluate chain of functions (got null)", result);
    Assert.assertThat("Expected list of 1 call element", result.size(), Matchers.is(1));
    final PyExpression callee = result.get(0).getCallee();
    Assert.assertNotNull("Found empty callee", callee);
    Assert.assertThat("Evaluated incorrectly", callee.getName(), Matchers.equalTo("abs"));
  }

  public void testStringConcat() {
    final PyTargetExpression myValue = createFileAndExpression("my_value",
                                                               "x = 'go'",
                                                               "y = x + ' away'",
                                                               "my_value = y"
    );

    final List<PyStringLiteralExpression> expressions = PyExpressionEvaluator.evaluateRaw(myValue, PyStringLiteralExpression.class);
    Assert.assertThat("Expected list of 2 string elements", expressions.size(), Matchers.is(2));
    Assert.assertThat("Evaluated incorrectly", expressions.get(0).getStringValue(), Matchers.equalTo("go"));
    Assert.assertThat("Evaluated incorrectly", expressions.get(1).getStringValue(), Matchers.equalTo(" away"));
  }

  public void testNumberConcat() {
    final PyTargetExpression myValue = createFileAndExpression("my_value",
                                                               "x = 2",
                                                               "y = x + 1",
                                                               "my_value = y"
    );

    final List<PyNumericLiteralExpression> expressions = PyExpressionEvaluator.evaluateRaw(myValue, PyNumericLiteralExpression.class);
    Assert.assertThat("Expected list of 2 num elements", expressions.size(), Matchers.is(2));
    Assert.assertThat("Evaluated incorrectly", expressions.get(0).getLongValue(), Matchers.equalTo(2L));
    Assert.assertThat("Evaluated incorrectly", expressions.get(1).getLongValue(), Matchers.equalTo(1L));
  }

  public void testNumber() {
    final long result = PyExpressionEvaluator.evaluateLong(createFileAndExpression("my_value", "x = 2", " y = 3", "my_value = x + y + 1"));
    Assert.assertThat("Error summarizing numbers", result, Matchers.is(6L));
  }

  public void testString() {
    final String result = PyExpressionEvaluator.evaluateString(createFileAndExpression("my_value",
                                                                                       "x = 'hello'",
                                                                                       " y = \" there\"",
                                                                                       "my_value = x + y + '!'"));
    Assert.assertThat("Error in string concat", result, Matchers.is("hello there!"));
  }

  public void testIterableTuples() {
    final List<PyStringLiteralExpression> value = PyExpressionEvaluator.evaluateIterable(createFileAndExpression("my_value",
                                                                                                                 "x = ('hello', 'from')",
                                                                                                                 " y = ('here', '!')",
                                                                                                                 "my_value = x + y"),
                                                                                         PyStringLiteralExpression.class
    );
    Assert.assertThat("Expected list of elements", value.size(), Matchers.is(4));
    Assert.assertThat("Evaluated incorrectly", value.get(0).getStringValue(), Matchers.equalTo("hello"));
    Assert.assertThat("Evaluated incorrectly", value.get(1).getStringValue(), Matchers.equalTo("from"));
    Assert.assertThat("Evaluated incorrectly", value.get(2).getStringValue(), Matchers.equalTo("here"));
    Assert.assertThat("Evaluated incorrectly", value.get(3).getStringValue(), Matchers.equalTo("!"));
  }

  public void testIterableLists() {
    final List<PyStringLiteralExpression> value = PyExpressionEvaluator.evaluateIterable(createFileAndExpression("my_value",
                                                                                                                 "x = ['hello', 'from']",
                                                                                                                 " y = ['here'] + ['!']",
                                                                                                                 "my_value = x + y"),
                                                                                         PyStringLiteralExpression.class
    );
    Assert.assertThat("Expected list of elements", value.size(), Matchers.is(4));
    Assert.assertThat("Evaluated incorrectly", value.get(0).getStringValue(), Matchers.equalTo("hello"));
    Assert.assertThat("Evaluated incorrectly", value.get(1).getStringValue(), Matchers.equalTo("from"));
    Assert.assertThat("Evaluated incorrectly", value.get(2).getStringValue(), Matchers.equalTo("here"));
    Assert.assertThat("Evaluated incorrectly", value.get(3).getStringValue(), Matchers.equalTo("!"));
  }

  /**
   * Create file, fill it with data and return some expression from it
   *
   * @param varName name of expression to return
   * @param lines   lines of code
   * @return expression
   */
  @NotNull
  private PyTargetExpression createFileAndExpression(@NotNull final String varName, @NotNull final String... lines) {
    final PsiFile file =
      PyElementGenerator.getInstance(myFixture.getProject()).createDummyFile(LanguageLevel.PYTHON27, StringUtil.join(lines, "\n"));
    assert file instanceof PyFile : "PyElementGenerator created not py file?";
    final PyFile pyFile = (PyFile)file;
    final PyTargetExpression result = pyFile.findTopLevelAttribute(varName);
    assert result != null : "No var with name " + varName + " found";
    return result;
  }
}
