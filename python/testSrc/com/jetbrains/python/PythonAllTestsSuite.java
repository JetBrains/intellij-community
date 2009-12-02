package com.jetbrains.python;

import junit.framework.TestSuite;

/**
 * A suite to explicitly include all actual tests, in fail-fast order.
 * User: dcheryasov
 * Date: Nov 19, 2009 1:23:22 AM
 */
public class PythonAllTestsSuite {

  public static final Class[] tests = {
    PythonLexerTest.class,
    PythonParsingTest.class,
    PyIndentTest.class,
    PyStatementPartsTest.class,
    PythonHighlightingTest.class,
    PyStubsTest.class,
    PyResolveTest.class,
    PyMultiFileResolveTest.class,
    PyResolveCalleeTest.class,
    PyToJavaResolveTest.class,
    PythonCompletionTest.class,
    PyInheritorsSearchTest.class,
    PyParameterInfoTest.class,
    PyDecoratorTest.class,
    PyQuickDocTest.class,
    PythonInspectionsTest.class,
    PythonDemorganLawIntentionTest.class,
    PyQuickFixTest.class,
    PySelectWordTest.class,
  };

  public static TestSuite suite() {
    return new TestSuite(tests);
  }

  
}
