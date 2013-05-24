/*
 * User: anna
 * Date: 06-Mar-2008
 */
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;

/**
 * User : ktisha
 */
public class PyConvertMethodToPropertyIntentionTest extends PyIntentionTestCase {

  public void testParamList() throws Exception {
    doNegateIntentionTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testSimple() throws Exception {
    doIntentionTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testProperty() throws Exception {
    doNegateIntentionTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testEmptyReturn() throws Exception {
    doNegateIntentionTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testYield() throws Exception {
    doIntentionTest(PyBundle.message("INTN.convert.method.to.property"));
  }

  public void testNoReturn() throws Exception {
    doNegateIntentionTest(PyBundle.message("INTN.convert.method.to.property"));
  }

}