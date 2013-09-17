package com.jetbrains.python;

import com.jetbrains.cython.*;
import com.jetbrains.django.lang.template.DjangoTemplateParserTest;
import com.jetbrains.jinja2.Jinja2ParserTest;
import com.jetbrains.python.codeInsight.PyClassMROTest;
import com.jetbrains.python.codeInsight.PyCompletionPatternsTest;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.intentions.PyIntentionTest;
import com.jetbrains.python.intentions.PythonDemorganLawIntentionTest;
import com.jetbrains.python.refactoring.*;
import com.jetbrains.python.refactoring.changeSignature.PyChangeSignatureTest;
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
    CythonLexerTest.class,
    PyStringFormatParserTest.class,
    PyEncodingTest.class,
    PythonParsingTest.class,
    CythonParsingTest.class,
    CythonReparseTest.class,
    PyStringLiteralTest.class,
    PyIndentTest.class,
    PyWrapTest.class,
    PyStatementPartsTest.class,
    PythonHighlightingTest.class,
    PyStubsTest.class,
    PyResolveTest.class,
    Py3ResolveTest.class,
    PyMultiFileResolveTest.class,
    PyClassMROTest.class,
    PyResolveCalleeTest.class,
    CythonResolveTest.class,
    PyAssignmentMappingTest.class,
    PythonCompletionTest.class,
    Py3CompletionTest.class,
    PyInheritorsSearchTest.class,
    PyParameterInfoTest.class,
    PyDecoratorTest.class,
    PyQuickDocTest.class,
    PyTypeParserTest.class,
    PyTypeTest.class,
    PythonInspectionsTest.class,
    PyTypeCheckerInspectionTest.class,
    Py3TypeCheckerInspectionTest.class,
    PyUnreachableCodeInspectionTest.class,
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
    PyMoveTest.class,
    PyExtractMethodTest.class,
    PyPullUpTest.class,
    PyPushDownTest.class,
    PyExtractSuperclassTest.class,
    PyInlineLocalTest.class,
    PyAutoUnindentTest.class,
    PyFindUsagesTest.class,
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
    PyClassicPropertyTest.StubBasedTest.class,
    PyDecoratedPropertyTest.class,
    PythonRunConfigurationTest.class,
    PyFoldingTest.class,
    EpydocStringTest.class,
    PyEmacsTabTest.class,
    PyRequirementTest.class,
    PyOverrideTest.class,
    PyBinaryModuleCompletionTest.class,
    PyCompletionPatternsTest.class,
    PyCompatibilityInspectionTest.class,
    PyUnresolvedReferencesInspectionTest.class,
    Py3UnresolvedReferencesInspectionTest.class,
    PyCallingNonCallableInspectionTest.class,
    PyUnboundLocalVariableInspectionTest.class,
    PyMethodOverridingInspectionTest.class,
    PyAttributeOutsideInitInspectionTest.class,
    PyClassHasNoInitInspectionTest.class,
    PyUnusedImportTest.class,
    PyDeprecationTest.class,
    PythonHighlightingLexerTest.class,
    PyOldStyleClassInspectionTest.class,
    PyMissingConstructorTest.class,
    PyPropertyAccessInspectionTest.class,
    Jinja2ParserTest.class,
    DjangoTemplateParserTest.class,
    PyJoinLinesTest.class,
    PyStatementListTest.class,
    PyChangeSignatureTest.class,
    PyCommenterTest.class,
    PyRegexpTest.class,
    PyAddImportTest.class,
    PyPathEvaluatorTest.class,
    PyBlockEvaluatorTest.class,
    PyDocstringTest.class
  };

  public static TestSuite suite() {
    return new TestSuite(tests);
  }


}
