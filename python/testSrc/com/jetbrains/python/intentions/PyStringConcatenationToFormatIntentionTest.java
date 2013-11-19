package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * User : ktisha
 */
public class PyStringConcatenationToFormatIntentionTest extends PyIntentionTestCase {

  public void testSimple() {
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON25);
  }

  public void testAugmentAssignment() {   //PY-5226
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON25);
  }

  public void testNegative() {   //PY-6505
    runWithLanguageLevel(LanguageLevel.PYTHON25, new Runnable() {
      @Override
      public void run() {
        doNegativeTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
      }
    });
  }

  public void testTwoStrings() {   //PY-6505
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON25);
  }

  public void testUnknownType() {   //PY-7969
    doNegativeTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testEmptyStrings() {   //PY-7968
    doNegativeTest(PyBundle.message("INTN.replace.plus.with.format.operator"));
  }

  public void testUnicodeString() { //PY-7463
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON25);
  }

  public void testUnicodeSecondString() { //PY-7463
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON25);
  }

  // PY-8366
  public void testEscapingPy3() {
    doTest(PyBundle.message("INTN.replace.plus.with.str.format"), LanguageLevel.PYTHON33);
  }

  // PY-8588
  public void testEscaping() {
    doTest(PyBundle.message("INTN.replace.plus.with.format.operator"), LanguageLevel.PYTHON25);
  }

  public void testPy3() {   //PY-4706
    doTest(PyBundle.message("INTN.replace.plus.with.str.format"), LanguageLevel.PYTHON33);
  }
}