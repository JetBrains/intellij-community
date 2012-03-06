package com.jetbrains.python;

import com.jetbrains.cython.CythonInspectionsTest;
import com.jetbrains.cython.CythonRenameTest;
import com.jetbrains.cython.CythonResolveTest;
import com.jetbrains.django.lang.template.DjangoTemplateParserTest;
import com.jetbrains.jinja2.Jinja2ParserTest;
import com.jetbrains.python.codeInsight.PyCompletionPatternsTest;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.refactoring.*;
import com.jetbrains.python.refactoring.classes.PyExtractSuperclassTest;
import com.jetbrains.python.refactoring.classes.PyPullUpTest;
import com.jetbrains.python.refactoring.classes.PyPushDownTest;
import junit.framework.TestSuite;

/**
 * A suite to explicitly include all actual tests, in fail-fast order.
 * User: dcheryasov
 * Date: Nov 19, 2009 1:23:22 AM
 */
public class PythonAllTestsSuite {

  public static final Class[] tests = {
    PythonLexerTest.class,
    PyStringLiteralLexerTest.class,
    PyStringFormatParserTest.class,
    PyEncodingTest.class,
    PythonParsingTest.class,
    PyStringLiteralTest.class,
    PyIndentTest.class,
    PyWrapTest.class,
    PyStatementPartsTest.class,
    PythonHighlightingTest.class,
    PyStubsTest.class,
    PyResolveTest.class,
    Py3ResolveTest.class,
    PyMultiFileResolveTest.class,
    PyResolveCalleeTest.class,
    CythonResolveTest.class,
    PyAssignmentMappingTest.class,
    PythonCompletionTest.class,
    Py3CompletionTest.class,
    PyInheritorsSearchTest.class,
    PyParameterInfoTest.class,
    PyDecoratorTest.class,
    PyQuickDocTest.class,
    PythonInspectionsTest.class,
    PyArgumentListInspectionTest.class,
    CythonInspectionsTest.class,
    PythonDemorganLawIntentionTest.class,
    PyQuickFixTest.class,
    PyIntentionTest.class,
    PySelectWordTest.class,
    PyEditingTest.class,
    PySurroundWithTest.class,
    PyFormatterTest.class,
    PyRenameTest.class,
    CythonRenameTest.class,
    PyExtractMethodTest.class,
    PyPullUpTest.class,
    PyPushDownTest.class,
    PyExtractSuperclassTest.class,
    PyInlineLocalTest.class,
    PyAutoUnindentTest.class,
    PyFindUsagesTest.class,
    PyTypeTest.class,
    PyControlFlowBuilderTest.class,
    PyCodeFragmentTest.class,
    PyOptimizeImportsTest.class,
    PySmartEnterTest.class,
    PyStatementMoverTest.class,
    PyIntroduceVariableTest.class,
    PyIntroduceFieldTest.class,
    PyIntroduceConstantTest.class,
    PyClassNameCompletionTest.class,
    PySuppressInspectionsTest.class,
    PyClassicPropertyTest.class,
    PyDecoratedPropertyTest.class,
    PythonRunConfigurationTest.class,
    PyFoldingTest.class,
    EpydocStringTest.class,
    PyEmacsTabTest.class,
    PyTypeParserTest.class,
    PyOverrideTest.class,
    PyBinaryModuleCompletionTest.class,
    PyCompletionPatternsTest.class,
    PyCompatibilityInspectionTest.class,
    PyUnresolvedReferencesInspectionTest.class,
    PyCallingNonCallableTest.class,
    PyUnusedImportTest.class,
    PyDeprecationTest.class,
    PythonHighlightingLexerTest.class,
    PyOldStyleClassInspectionTest.class,
    PyMissingConstructorTest.class,
    PyPropertyAccessInspectionTest.class,
    Jinja2ParserTest.class,
    DjangoTemplateParserTest.class
  };

  public static TestSuite suite() {
    return new TestSuite(tests);
  }


}
