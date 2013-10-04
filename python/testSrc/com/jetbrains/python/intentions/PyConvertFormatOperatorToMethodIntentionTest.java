package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : ktisha
 */
public class PyConvertFormatOperatorToMethodIntentionTest extends PyIntentionTestCase {

  public void testSimple() {
    doIntentionTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testMulti() {
    doIntentionTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testEscaped() {
    doIntentionTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }

  public void testUnicode() {
    doIntentionTest(PyBundle.message("INTN.replace.with.method"), LanguageLevel.PYTHON26);
  }
}