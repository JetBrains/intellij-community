// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.replaceService
import com.jetbrains.python.codeInsight.PyCodeInsightSettings
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.*
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.packaging.management.TestPypiPackageCache
import com.jetbrains.python.packaging.pip.PypiPackageCache
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.quickFixes.PyRenameElementQuickFixTest
import org.intellij.lang.regexp.inspection.RegExpRedundantEscapeInspection
import org.jetbrains.annotations.NonNls

@TestDataPath("\$CONTENT_ROOT/../testData/inspections/")
class PyQuickFixTest : PyTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor? = ourPy2Descriptor

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    InspectionProfileImpl.INIT_INSPECTIONS = true
    myFixture.setCaresAboutInjection(false)
    PyRenameElementQuickFixTest.registerTestNameSuggestionProvider(testRootDisposable)
  }

  @Throws(Exception::class)
  override fun tearDown() {
    InspectionProfileImpl.INIT_INSPECTIONS = false
    super.tearDown()
  }

  fun testAddImport() {
    doInspectionTest(
      arrayOf("AddImport.py", "ImportTarget.py"),
      PyUnresolvedReferencesInspection::class.java,
      "Import 'ImportTarget'",
      true,
      true
    )
  }

  fun testAddImportDoc() {
    doInspectionTest(
      arrayOf("AddImportDoc.py", "ImportTarget.py"),
      PyUnresolvedReferencesInspection::class.java,
      "Import 'ImportTarget'",
      true,
      true
    )
  }

  // PY-728
  fun testAddImportDocComment() {
    doInspectionTest(
      arrayOf("AddImportDocComment.py", "ImportTarget.py"),
      PyUnresolvedReferencesInspection::class.java,
      "Import 'ImportTarget'",
      true,
      true
    )
  }

  // PY-42307
  fun testInstallAndImportPackageByNameAlias() {
    val packageCache = TestPypiPackageCache()
    packageCache.testPackages = setOf("pandas")
    ApplicationManager.getApplication().replaceService(PypiPackageCache::class.java,
                                                       packageCache,
                                                       testRootDisposable)
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.configureByText(PythonFileType.INSTANCE, "pd<caret>.array()")
    myFixture.findSingleIntention("Import 'turtle.pd'") // standard library
    myFixture.findSingleIntention("Install and import package 'pandas'") // 'pd' is a common import alias for 'pandas' from PyPI
  }

  fun testImportFromModule() {
    doInspectionTest(
      arrayOf("importFromModule/foo/bar.py", "importFromModule/foo/baz.py", "importFromModule/foo/__init__.py"),
      PyUnresolvedReferencesInspection::class.java,
      "Import 'importFromModule.foo.baz'",
      true,
      true
    )
  }

  // PY-14365
  fun testObjectBaseIsNotShownInAutoImportQuickfix() {
    myFixture.copyDirectoryToProject("objectBaseIsNotShownInAutoImportQuickfix", "")
    myFixture.configureByFile("main.py")
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    val intention = myFixture.findSingleIntention("Import")
    assertNotNull(intention)
    assertEquals("Import 'module.MyOldStyleClass'", intention.text)
  }

  // PY-6302
  fun testImportFromModuleStar() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.copyDirectoryToProject("importFromModuleStar", "")
    myFixture.configureFromTempProjectFile("source.py")
    myFixture.checkHighlighting(true, false, false)
    val intentionAction = myFixture.findSingleIntention("Import 'target.xyzzy'")
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile("importFromModuleStar/source_after.py")
  }

  fun testQualifyByImport() {
    val settings = PyCodeInsightSettings.getInstance()
    val oldPreferFrom = settings.PREFER_FROM_IMPORT
    settings.PREFER_FROM_IMPORT = false
    try {
      doInspectionTest(
        arrayOf("QualifyByImport.py", "QualifyByImportFoo.py"),
        PyUnresolvedReferencesInspection::class.java,
        PyPsiBundle.message("ACT.qualify.with.module"),
        true,
        true
      )
    }
    finally {
      settings.PREFER_FROM_IMPORT = oldPreferFrom
    }
  }

  fun testAddToImportFromList() {
    doInspectionTest(
      arrayOf("AddToImportFromList.py", "AddToImportFromFoo.py"),
      PyUnresolvedReferencesInspection::class.java,
      "Import 'add_to_import_test_unique_name from AddToImportFromFoo'",
      true,
      true
    )
  }

  fun testAddSelf() {
    doInspectionTest(
      PyMethodParametersInspection::class.java,
      PyPsiBundle.message("QFIX.add.parameter.self", "self"),
      true,
      true
    )
  }

  fun testReplacePrint() {
    doInspectionTest(
      PyCompatibilityInspection::class.java,
      PyPsiBundle.message("QFIX.statement.effect"),
      true,
      true
    )
  }

  // PY-22045
  fun testBatchReplacePrintInsertsFutureImportOnlyOnce() {
    doInspectionTest(
      PyCompatibilityInspection::class.java,
      "Fix all 'Code is incompatible with specific Python versions' problems in file",
      true,
      true
    )
  }

  // PY-4556
  fun testAddSelfFunction() {
    doInspectionTest(
      "AddSelfFunction.py",
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.unresolved.reference", "get_a", "self"),
      true,
      true
    )
  }

  // PY-9721
  fun testAddSelfToClassmethod() {
    doInspectionTest(
      "AddSelfToClassmethod.py",
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.unresolved.reference", "foo", "cls"),
      true,
      true
    )
  }

  fun testAddCls() {
    doInspectionTest(
      PyMethodParametersInspection::class.java,
      PyPsiBundle.message("QFIX.add.parameter.self", "cls"),
      true,
      true
    )
  }

  fun testRenameToSelf() {
    doInspectionTest(
      PyMethodParametersInspection::class.java,
      PyPsiBundle.message("QFIX.rename.parameter", "self"),
      true,
      true
    )
  }

  fun testRemoveTrailingSemicolon() {
    doInspectionTest(
      PyTrailingSemicolonInspection::class.java,
      PyPsiBundle.message("QFIX.remove.trailing.semicolon"),
      true,
      true
    )
  }

  fun testDictCreation() {
    doInspectionTest(
      PyDictCreationInspection::class.java,
      PyPsiBundle.message("QFIX.dict.creation"),
      true,
      true
    )
  }

  // PY-6283
  fun testDictCreationTuple() {
    doInspectionTest(
      PyDictCreationInspection::class.java,
      PyPsiBundle.message("QFIX.dict.creation"),
      true,
      true
    )
  }

  // PY-7318
  fun testDictCreationDuplicate() {
    doInspectionTest(
      PyDictCreationInspection::class.java,
      PyPsiBundle.message("QFIX.dict.creation"),
      true,
      true
    )
  }

  // PY-40177
  fun testDictCreationWithDoubleStars() {
    runWithLanguageLevel(LanguageLevel.getLatest()) {
      doInspectionTest(
        PyDictCreationInspection::class.java,
        PyPsiBundle.message("QFIX.dict.creation"),
        true,
        true
      )
    }
  }

  fun testTransformClassicClass() {
    doInspectionTest(
      PyClassicStyleClassInspection::class.java,
      PyPsiBundle.message("QFIX.classic.class.transform"),
      true,
      true
    )
  }

  fun testAddGlobalStatement() {
    doInspectionTest(
      PyUnboundLocalVariableInspection::class.java,
      PyPsiBundle.message("QFIX.add.global"),
      true,
      true
    )
  }

  fun testAddGlobalExistingStatement() {
    doInspectionTest(
      PyUnboundLocalVariableInspection::class.java,
      PyPsiBundle.message("QFIX.add.global"),
      true,
      true
    )
  }

  fun testSimplifyBooleanCheck() {
    doInspectionTest(
      PySimplifyBooleanCheckInspection::class.java,
      PyPsiBundle.message("QFIX.simplify.boolean.expression", "b"),
      true,
      true
    )
  }

  fun testMoveFromFutureImport() {
    doInspectionTest(
      PyFromFutureImportInspection::class.java,
      PyPsiBundle.message("QFIX.move.from.future.import"),
      true,
      true
    )
  }

  // PY-10080
  fun testMoveFromFutureImportDocString() {
    doInspectionTest(
      PyFromFutureImportInspection::class.java,
      PyPsiBundle.message("QFIX.move.from.future.import"),
      true,
      true
    )
  }

  // PY-23475
  fun testMoveFromFutureImportAboveModuleLevelDunder() {
    doInspectionTest(
      PyFromFutureImportInspection::class.java,
      PyPsiBundle.message("QFIX.move.from.future.import"),
      true,
      true
    )
  }

  fun testComparisonWithNone() {
    doInspectionTest(
      PyComparisonWithNoneInspection::class.java,
      PyPsiBundle.message("QFIX.replace.equality"),
      true,
      true
    )
  }

  fun testAddClassFix() {
    doInspectionTest(
      "AddClass.py",
      PyUnresolvedReferencesInspection::class.java,
      "Create class 'Xyzzy'",
      true,
      true
    )
  }

  // PY-42389
  fun testAddClassFixPython3() {
    runWithLanguageLevel(LanguageLevel.getLatest()) {
      doInspectionTest(
        PyUnresolvedReferencesInspection::class.java,
        "Create class 'Xyzzy'",
        true,
        true
      )
    }
  }

  // PY-21204
  fun testAddClassFromTypeComment() {
    doInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      "Create class 'MyClass'",
      true,
      true
    )
  }

  // PY-21204
  fun testAddClassFromFString() {
    runWithLanguageLevel(LanguageLevel.PYTHON36) {
      doInspectionTest(
        PyUnresolvedReferencesInspection::class.java,
        "Create class 'MyClass'",
        true,
        true
      )
    }
  }

  // PY-33802
  fun testAddClassToImportedModule() {
    doMultiFilesInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.create.class.in.module", "Clzz", "mod.py"),
      "mod.py"
    )
  }

  // PY-33802
  fun testAddClassToImportedPackage() {
    doMultiFilesInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.create.class.in.module", "Clzz", "__init__.py"),
      "pkg/__init__.py"
    )
  }

  // PY-33802
  fun testAddClassToModuleInFromImport() {
    doMultiFilesInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.create.class.in.module", "Clzz", "mod.py"),
      "mod.py"
    )
  }

  // PY-33802
  fun testAddClassToPackageInFromImport() {
    doMultiFilesInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.create.class.in.module", "Clzz", "__init__.py"),
      "mypack/__init__.py"
    )
  }

  // PY-21204
  fun testAddFunctionFromFString() {
    runWithLanguageLevel(LanguageLevel.PYTHON36) {
      doInspectionTest(
        PyUnresolvedReferencesInspection::class.java,
        PyPsiBundle.message("QFIX.NAME.unresolved.reference.create.function", "my_function"),
        true,
        true
      )
    }
  }

  // PY-1465
  fun testAddFunctionToModuleInImport() {
    doMultiFilesInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.create.function.in.module", "func", "mod.py"),
      "mod.py"
    )
  }

  // PY-34710
  fun testAddFunctionToModuleInFromImport() {
    doMultiFilesInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.create.function.in.module", "foo", "mod.py"),
      "mod.py"
    )
  }

  // PY-34710
  fun testAddFunctionToPackageInFromImport() {
    doMultiFilesInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.create.function.in.module", "foo", "__init__.py"),
      "mypack/__init__.py"
    )
  }

  // PY-1470
  fun testRedundantParentheses() {
    val testFiles = arrayOf("RedundantParentheses.py")
    myFixture.enableInspections(PyRedundantParenthesesInspection::class.java)
    myFixture.configureByFiles(*testFiles)
    myFixture.checkHighlighting(true, false, true)
    val intentionAction = myFixture.findSingleIntention(PyPsiBundle.message("QFIX.redundant.parentheses"))
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"))
  }

  // PY-3095
  fun testRedundantParenthesesBoolean() {
    doInspectionTest(
      PyRedundantParenthesesInspection::class.java,
      PyPsiBundle.message("QFIX.redundant.parentheses"),
      true,
      true
    )
  }

  // PY-3239
  fun testRedundantParenthesesMore() {
    doInspectionTest(
      PyRedundantParenthesesInspection::class.java,
      PyPsiBundle.message("QFIX.redundant.parentheses"),
      true,
      true
    )
  }

  // PY-12679
  fun testRedundantParenthesesParenthesizedExpression() {
    doInspectionTest(
      PyRedundantParenthesesInspection::class.java,
      PyPsiBundle.message("QFIX.redundant.parentheses"),
      true,
      true
    )
  }

  fun testRedundantParenthesesMultipleParentheses() {
    doInspectionTest(
      PyRedundantParenthesesInspection::class.java,
      PyPsiBundle.message("QFIX.redundant.parentheses"),
      true,
      true
    )
  }

  // PY-15506
  fun testEmptyListOfBaseClasses() {
    doInspectionTest(
      PyRedundantParenthesesInspection::class.java,
      PyPsiBundle.message("QFIX.redundant.parentheses"),
      true,
      true
    )
  }

  // PY-18203
  fun testRedundantParenthesesInTuples() {
    doInspectionTest(
      PyRedundantParenthesesInspection::class.java,
      PyPsiBundle.message("QFIX.redundant.parentheses"),
      true,
      true
    )
  }

  // PY-1020
  fun testChainedComparisons() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      PyPsiBundle.message("QFIX.chained.comparison"),
      true,
      true
    )
  }

  // PY-3126
  fun testChainedComparison1() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      PyPsiBundle.message("QFIX.chained.comparison"),
      true,
      true
    )
  }

  // PY-3126
  fun testChainedComparison2() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      PyPsiBundle.message("QFIX.chained.comparison"),
      true,
      true
    )
  }

  // PY-3126
  fun testChainedComparison3() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      PyPsiBundle.message("QFIX.chained.comparison"),
      true,
      true
    )
  }

  // PY-5623
  fun testChainedComparison4() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      PyPsiBundle.message("QFIX.chained.comparison"),
      true,
      true
    )
  }

  // PY-6467
  fun testChainedComparison5() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      PyPsiBundle.message("QFIX.chained.comparison"),
      true,
      true
    )
  }

  // PY-20004
  fun testChainedComparison7() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      PyPsiBundle.message("QFIX.chained.comparison"),
      true,
      true
    )
  }

  // PY-14002
  fun testChainedComparisonWithCommonBinaryExpression() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      PyPsiBundle.message("QFIX.chained.comparison"),
      true,
      true
    )
  }

  // PY-19583
  fun testChainedComparison6() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      "Simplify chained comparison",
      true,
      true
    )
  }

  // PY-24942
  fun testChainedComparison8() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      "Simplify chained comparison",
      true,
      true
    )
  }

  // PY-29121
  fun testChainedComparison9() {
    doInspectionTest(
      PyChainedComparisonsInspection::class.java,
      "Simplify chained comparison",
      true,
      true
    )
  }

  // PY-1362, PY-2585
  fun testStatementEffect() {
    doInspectionTest(
      PyStatementEffectInspection::class.java,
      PyPsiBundle.message("QFIX.statement.effect"),
      true,
      true
    )
  }

  // PY-1265
  fun testStatementEffectIntroduceVariable() {
    doInspectionTest(
      PyStatementEffectInspection::class.java,
      PyPsiBundle.message("QFIX.introduce.variable"),
      true,
      true
    )
  }

  // PY-2092
  fun testUnresolvedRefCreateFunction() {
    doInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.NAME.unresolved.reference.create.function", "ref"),
      true,
      true
    )
  }

  fun testUnresolvedRefCreateAsyncFunction() {
    runWithLanguageLevel(LanguageLevel.getLatest()) {
      doInspectionTest(
        PyUnresolvedReferencesInspection::class.java,
        PyPsiBundle.message("QFIX.NAME.unresolved.reference.create.function", "ref"),
        true,
        true
      )
    }
  }

  fun testUnresolvedRefNoCreateFunction() {
    myFixture.enableInspections(PyUnresolvedReferencesInspection::class.java)
    myFixture.configureByFile("UnresolvedRefNoCreateFunction.py")
    myFixture.checkHighlighting(true, false, false)
    val intentionAction = myFixture.getAvailableIntention(
      PyPsiBundle.message("QFIX.NAME.unresolved.reference.create.function", "ref")
    )
    assertNull(intentionAction)
  }

  fun testReplaceNotEqOperator() {
    doInspectionTest(
      PyCompatibilityInspection::class.java,
      PyPsiBundle.message("INTN.replace.noteq.operator"),
      true,
      true
    )
  }

  fun testListCreation() {
    doInspectionTest(
      PyListCreationInspection::class.java,
      PyPsiBundle.message("QFIX.list.creation"),
      true,
      true
    )
  }

  // PY-16194
  fun testListCreationOnlyConsecutiveAppends() {
    doInspectionTest(
      PyListCreationInspection::class.java,
      PyPsiBundle.message("QFIX.list.creation"),
      true,
      true
    )
  }

  // PY-1445
  fun testConvertSingleQuotedDocstring() {
    indentOptions.INDENT_SIZE = 2
    doInspectionTest(
      PySingleQuotedDocstringInspection::class.java,
      PyPsiBundle.message("QFIX.convert.single.quoted.docstring"),
      true,
      true
    )
  }

  // PY-8926
  fun testConvertSingleQuotedDocstringEscape() {
    indentOptions.INDENT_SIZE = 2
    doInspectionTest(
      PySingleQuotedDocstringInspection::class.java,
      PyPsiBundle.message("QFIX.convert.single.quoted.docstring"),
      true,
      true
    )
  }

  // PY-3127
  fun testDefaultArgument() {
    doInspectionTest(
      PyDefaultArgumentInspection::class.java,
      PyPsiBundle.message("QFIX.default.argument"),
      true,
      true
    )
  }

  fun testDefaultArgumentEmptyList() {
    doInspectionTest(
      PyDefaultArgumentInspection::class.java,
      PyPsiBundle.message("QFIX.default.argument"),
      true,
      true
    )
  }

  // PY-17392
  fun testDefaultArgumentCommentsInsideParameters() {
    doInspectionTest(
      PyDefaultArgumentInspection::class.java,
      PyPsiBundle.message("QFIX.default.argument"),
      true,
      true
    )
  }

  // PY-3125
  fun testArgumentEqualDefault() {
    doInspectionTest(
      PyArgumentEqualDefaultInspection::class.java,
      PyPsiBundle.message("QFIX.remove.argument.equal.default"),
      true,
      true
    )
  }

  // PY-3315
  fun testAddCallSuper() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-4017
  fun testAddCallSuper1() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-8654
  fun testAddCallSuperPass() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-15867
  fun testAddCallSuperOptionalAndRequiredParamsNameCollision() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-15927
  fun testAddCallSuperConflictingTupleParam() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-16036
  fun testAddCallSuperSelfNamePreserved() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-16420
  fun testAddCallSuperRepeatedOptionalParamsPassedToSuperConstructor() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-16420
  fun testAddCallSuperRepeatedOptionalTupleParamsPassedToSuperConstructor() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-16289
  fun testAddCallSuperCommentAfterColonPreserved() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-16348
  fun testAddCallSuperCommentsInFunctionBodyPreserved() {
    doInspectionTest(
      PyMissingConstructorInspection::class.java,
      PyPsiBundle.message("QFIX.add.super"),
      true,
      true
    )
  }

  // PY-491, PY-13297
  fun testAddEncoding() {
    doInspectionTest(
      PyMandatoryEncodingInspection::class.java,
      PyPsiBundle.message("QFIX.add.encoding"),
      true,
      true
    )
  }

  // PY-13297
  fun testAddEncodingAtLastLine() {
    doInspectionTest(
      PyMandatoryEncodingInspection::class.java,
      PyPsiBundle.message("QFIX.add.encoding"),
      true,
      true
    )
  }

  // PY-3348
  fun testRemoveDecorator() {
    doInspectionTest(
      PyDecoratorInspection::class.java,
      PyPsiBundle.message("QFIX.remove.decorator"),
      true,
      true
    )
  }

  fun testAddParameter() {
    doInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.unresolved.reference.add.param", "test"),
      true,
      true
    )
  }

  // PY-6595
  fun testRenameUnresolvedReference() {
    doInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.rename.unresolved.reference"),
      true,
      true
    )
  }

  // PY-3120
  fun testSetFunctionToLiteral() {
    runWithLanguageLevel(LanguageLevel.PYTHON27) {
      doInspectionTest(
        PySetFunctionToLiteralInspection::class.java,
        PyPsiBundle.message("QFIX.replace.function.set.with.literal"),
        true,
        true
      )
    }
  }

  // PY-3394
  fun testDocstringParams() {
    indentOptions.INDENT_SIZE = 2
    runWithDocStringFormat(DocStringFormat.REST) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.add.parameter", "b"),
        true,
        true
      )
    }
  }

  fun testDocstringParams1() {
    indentOptions.INDENT_SIZE = 2
    runWithDocStringFormat(DocStringFormat.REST) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "c"),
        true,
        true
      )
    }
  }

  // PY-4964
  fun testDocstringParams2() {
    runWithDocStringFormat(DocStringFormat.REST) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.add.parameter", "ham"),
        true,
        true
      )
    }
  }

  // PY-9795
  fun testGoogleDocStringAddParam() {
    runWithDocStringFormat(DocStringFormat.GOOGLE) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.add.parameter", "b"),
        true,
        true
      )
    }
  }

  // PY-9795
  fun testGoogleDocStringRemoveParam() {
    runWithDocStringFormat(DocStringFormat.GOOGLE) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "c"),
        true,
        true
      )
    }
  }

  // PY-9795
  fun testGoogleDocStringRemoveParamWithSection() {
    runWithDocStringFormat(DocStringFormat.GOOGLE) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "c"),
        true,
        true
      )
    }
  }

  // PY-16761
  fun testGoogleDocStringRemovePositionalVararg() {
    runWithDocStringFormat(DocStringFormat.GOOGLE) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "args"),
        true,
        true
      )
    }
  }

  // PY-16761
  fun testGoogleDocStringRemoveKeywordVararg() {
    runWithDocStringFormat(DocStringFormat.GOOGLE) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "kwargs"),
        true,
        true
      )
    }
  }

  // PY-16908
  fun testNumpyDocStringRemoveFirstOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "x"),
        true,
        true
      )
    }
  }

  // PY-16908
  fun testNumpyDocStringRemoveMidOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "y"),
        true,
        true
      )
    }
  }

  // PY-16908
  fun testNumpyDocStringRemoveLastOfCombinedParams() {
    runWithDocStringFormat(DocStringFormat.NUMPY) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "z"),
        true,
        true
      )
    }
  }

  // PY-16908
  fun testNumpyDocStringRemoveCombinedVarargParam() {
    runWithDocStringFormat(DocStringFormat.NUMPY) {
      doInspectionTest(
        PyIncorrectDocstringInspection::class.java,
        PyPsiBundle.message("QFIX.docstring.remove.parameter", "args"),
        true,
        true
      )
    }
  }

  fun testUnnecessaryBackslash() {
    val testFiles = arrayOf("UnnecessaryBackslash.py")
    myFixture.enableInspections(PyUnnecessaryBackslashInspection::class.java)
    myFixture.configureByFiles(*testFiles)
    myFixture.checkHighlighting(true, false, true)
    val intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.remove.unnecessary.backslash"))!!
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"))
  }

  // PY-3051
  fun testUnresolvedRefTrueFalse() {
    doInspectionTest(
      PyUnresolvedReferencesInspection::class.java,
      PyPsiBundle.message("QFIX.replace.with.true.or.false", "True"),
      true,
      true
    )
  }

  fun testUnnecessaryBackslashInArgumentList() {
    val testFiles = arrayOf("UnnecessaryBackslashInArguments.py")
    myFixture.enableInspections(PyUnnecessaryBackslashInspection::class.java)
    myFixture.configureByFiles(*testFiles)
    myFixture.checkHighlighting(true, false, true)
    val intentionAction = myFixture.getAvailableIntention(PyPsiBundle.message("QFIX.remove.unnecessary.backslash"))!!
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"))
  }

  // PY-8788
  fun testRenameShadowingBuiltins() {
    val fileName = "RenameShadowingBuiltins.py"
    myFixture.configureByFile(fileName)
    myFixture.enableInspections(PyShadowingBuiltinsInspection::class.java)
    myFixture.checkHighlighting(true, false, true)
    val intentionAction = myFixture.getAvailableIntention(PyBundle.message("QFIX.NAME.rename.element"))!!
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile(graftBeforeExt(fileName, "_after"))
  }

  // PY-8788
  fun testRenameFunctionShadowingBuiltins() {
    val fileName = "RenameFunctionShadowingBuiltins.py"
    myFixture.configureByFile(fileName)
    myFixture.enableInspections(PyShadowingBuiltinsInspection::class.java)
    myFixture.checkHighlighting(true, false, true)
    val intentionAction = myFixture.getAvailableIntention(PyBundle.message("QFIX.NAME.rename.element"))!!
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkResultByFile(graftBeforeExt(fileName, "_after"))
  }

  fun testIgnoreShadowingBuiltins() {
    myFixture.configureByFile("IgnoreShadowingBuiltins.py")
    myFixture.enableInspections(PyShadowingBuiltinsInspection::class.java)
    val intentionAction = myFixture.getAvailableIntention("Ignore shadowed built-in name \"open\"")!!
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    myFixture.checkHighlighting(true, false, true)
  }

  fun testImplementAbstractProperty() {
    doInspectionTest(
      "ImplementAbstractProperty.py",
      PyAbstractClassInspection::class.java,
      PyBundle.message("QFIX.NAME.implement.methods"),
      true,
      true
    )
  }

  fun testImplementAbstractProperty1() {
    doInspectionTest(
      "ImplementAbstractProperty.py",
      PyAbstractClassInspection::class.java,
      PyBundle.message("QFIX.NAME.implement.methods"),
      true,
      true
    )
  }

  fun testImplementAbstractOrder() {
    doInspectionTest(
      "ImplementAbstractOrder.py",
      PyAbstractClassInspection::class.java,
      PyBundle.message("QFIX.NAME.implement.methods"),
      true,
      true
    )
  }

  fun testRemovingUnderscoresInNumericLiterals() {
    myFixture.configureByText(PythonFileType.INSTANCE, "1_0_0")

    val action = myFixture.findSingleIntention(PyPsiBundle.message("QFIX.NAME.remove.underscores.in.numeric"))
    myFixture.launchAction(action)

    myFixture.checkResult("100")
  }

  // PY-20452
  fun testRemoveRedundantEscapeInOnePartRegExp() {
    myFixture.enableInspections(RegExpRedundantEscapeInspection())
    myFixture.configureByText(PythonFileType.INSTANCE, "import re\nre.compile(\"(?P<foo>((<caret>\\/(?P<bar>.+))?))\")\")")

    val quickFixes = myFixture.getAllQuickFixes()
    assertEquals(1, quickFixes.size)

    val removeRedundantEscapeFix = quickFixes[0]
    assertEquals("Remove redundant escape", removeRedundantEscapeFix.text)

    myFixture.launchAction(removeRedundantEscapeFix)
    myFixture.checkResult("import re\nre.compile(\"(?P<foo>((/(?P<bar>.+))?))\")\")")
  }

  // PY-20452
  fun testRemoveRedundantEscapeInMultiPartRegExp() {
    myFixture.enableInspections(RegExpRedundantEscapeInspection())
    myFixture.configureByText(
      PythonFileType.INSTANCE, """
            import re
            re.compile("(?P<foo>"
                       "((<caret>\/(?P<bar>.+))?))")
        """.trimIndent()
    )

    val quickFixes = myFixture.getAllQuickFixes()
    assertEquals(1, quickFixes.size)

    val removeRedundantEscapeFix = quickFixes[0]
    assertEquals("Remove redundant escape", removeRedundantEscapeFix.text)

    myFixture.launchAction(removeRedundantEscapeFix)
    myFixture.checkResult(
      """
            import re
            re.compile("(?P<foo>"
                       "((/(?P<bar>.+))?))")
        """.trimIndent()
    )
  }

  // PY-8174
  fun testChangeSignatureKeywordAndPositionalParameters() {
    doInspectionTest(
      PyArgumentListInspection::class.java,
      "<html>Change the signature of f(x, foo)</html>",
      true,
      true
    )
  }

  // PY-8174
  fun testChangeSignatureAddKeywordOnlyParameter() {
    runWithLanguageLevel(LanguageLevel.PYTHON34) {
      doInspectionTest(
        PyArgumentListInspection::class.java,
        "<html>Change the signature of func(x, *args, foo)</html>",
        true,
        true
      )
    }
  }

  // PY-8174
  fun testChangeSignatureNewParametersNames() {
    doInspectionTest(
      PyArgumentListInspection::class.java,
      "<html>Change the signature of func(i1)</html>",
      true,
      true
    )
  }

  // PY-53671
  fun testChangeSignatureOfExportedBoundMethod() {
    runWithLanguageLevel(LanguageLevel.getLatest()) {
      doMultiFilesInspectionTest(
        PyArgumentListInspection::class.java,
        "<html>Change the signature of method(self, a, b)</html>",
        "mod.py"
      )
    }
  }

  // PY-8174
  fun testChangeSignatureParametersDefaultValues() {
    doInspectionTest(
      PyArgumentListInspection::class.java,
      "<html>Change the signature of func()</html>",
      true,
      true
    )
  }

  fun testAddKwargsToNewMethodIncompatibleWithInit() {
    doInspectionTest(
      PyInitNewSignatureInspection::class.java,
      "<html>Change the signature of __new__(cls)</html>",
      true,
      true
    )
  }

  fun testAddKwargsToIncompatibleOverridingMethod() {
    doInspectionTest(
      PyMethodOverridingInspection::class.java,
      "<html>Change the signature of m(self)</html>",
      true,
      true
    )
  }

  // PY-30789
  fun testSetImportedABCMetaAsMetaclassPy2() {
    doInspectionTest(
      "PyAbstractClassInspection/quickFix/SetImportedABCMetaAsMetaclassPy2/main.py",
      PyAbstractClassInspection::class.java,
      "Set '${PyNames.ABC_META}' as metaclass",
      true,
      true
    )
  }

  @NonNls
  override fun getTestDataPath(): String = PythonTestUtil.getTestDataPath() + "/inspections/"

  private fun doInspectionTest(
    inspectionClass: Class<out LocalInspectionTool>,
    quickFixName: String,
    applyFix: Boolean,
    available: Boolean,
  ) {
    doInspectionTest(getTestName(false) + ".py", inspectionClass, quickFixName, applyFix, available)
  }

  protected fun doInspectionTest(
    @TestDataFile @NonNls testFileName: String,
    inspectionClass: Class<out LocalInspectionTool>,
    @NonNls quickFixName: String,
    applyFix: Boolean,
    available: Boolean,
  ) {
    doInspectionTest(arrayOf(testFileName), inspectionClass, quickFixName, applyFix, available)
  }

  /**
   * Runs daemon passes and looks for given fix within infos.
   *
   * @param testFiles       names of files to participate; first is used for inspection and then for check by "_after".
   * @param inspectionClass what inspection to run
   * @param quickFixName    how the resulting fix should be named (the human-readable name users see)
   * @param applyFix        true if the fix needs to be applied
   * @param available       true if the fix should be available, false if it should be explicitly not available.
   */
  protected fun doInspectionTest(
    @NonNls testFiles: Array<String>,
    inspectionClass: Class<out LocalInspectionTool>,
    @NonNls quickFixName: String,
    applyFix: Boolean,
    available: Boolean,
  ) {
    myFixture.enableInspections(inspectionClass)
    myFixture.configureByFiles(*testFiles)
    myFixture.checkHighlighting(true, false, false)
    val intentionActions = myFixture.filterAvailableIntentions(quickFixName)
    if (available) {
      if (intentionActions.isEmpty()) {
        val intentionNames = myFixture.availableIntentions.map({ it.text })
        throw AssertionError(
          "Quickfix starting with \"$quickFixName\" is not available. " +
          "Available intentions:\n${StringUtil.join(intentionNames, "\n")}"
        )
      }
      if (intentionActions.size > 1) {
        throw AssertionError("There are more than one quickfix with the name \"$quickFixName\"")
      }
      if (applyFix) {
        myFixture.launchAction(intentionActions[0])
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        myFixture.checkResultByFile(graftBeforeExt(testFiles[0], "_after"), true)
      }
    }
    else {
      assertEmpty("Quick fix \"$quickFixName\" should not be available", intentionActions)
    }
  }

  private fun doMultiFilesInspectionTest(
    inspectionClass: Class<out LocalInspectionTool>,
    intentionStr: String,
    modifiedFile: String,
  ) {
    myFixture.enableInspections(inspectionClass)
    myFixture.copyDirectoryToProject(getTestName(true), "")
    myFixture.configureFromTempProjectFile(getTestName(true) + ".py")
    myFixture.checkHighlighting(true, false, false)
    val intentionAction = myFixture.findSingleIntention(intentionStr)
    assertNotNull(intentionAction)
    myFixture.launchAction(intentionAction)
    val expectedFile = getTestName(true) + "/" + graftBeforeExt(modifiedFile, "_after")
    myFixture.checkResultByFile(modifiedFile, expectedFile, true)
  }

  companion object {
    // Turns "name.ext" to "name_insertion.ext"
    @NonNls
    private fun graftBeforeExt(name: String, insertion: String): String {
      var dotpos = name.indexOf('.')
      if (dotpos < 0) dotpos = name.length
      return name.substring(0, dotpos) + insertion + name.substring(dotpos)
    }
  }
}
