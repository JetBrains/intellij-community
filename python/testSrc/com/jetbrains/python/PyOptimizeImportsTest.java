// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.actions.OptimizeImportsAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class PyOptimizeImportsTest extends PyTestCase {
  @NotNull
  private PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }

  public void testSimple() {
    doTest();
  }

  public void testOneOfMultiple() {
    doTest();
  }

  public void testImportStar() {
    doTest();
  }

  public void testImportStarOneOfMultiple() {
    doTest();
  }

  public void testTryExcept() {
    doTest();
  }

  public void testFromFuture() {
    doTest();
  }

  public void testUnresolved() {  // PY-2201
    doTest();
  }
  
  public void testSuppressed() {  // PY-5228
    doTest();
  }

  // PY-4330
  public void testSuppressedWithLegacyUnresolvedReferencesId() {
    doTest();
  }

  public void testSplit() {
    doTest();
  }

  public void testOrderByType() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "datetime.py",
          "",
          (___) -> doTest()
        )
    );
  }

  // PY-12018
  public void testAlphabeticalOrder() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "datetime.py",
          "",
          (___) -> doTest()
        )
    );
  }

  public void testInsertBlankLines() {  // PY-8355
    runWithAdditionalFileInLibDir(
      "sys.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "datetime.py",
          "",
          (___) -> doTest()
        )
    );
  }

  // PY-16351
  public void testNoExtraBlankLineAfterImportBlock() {
    doMultiFileTest();
  }

  // PY-18521
  public void testImportsFromTypingUnusedInTypeComments() {
    doTest();
  }

  // PY-18970
  public void testLibraryRootInsideProject() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    final VirtualFile libDir = myFixture.findFileInTempDir("lib");
    assertNotNull(libDir);
    
    runWithAdditionalClassEntryInSdkRoots(libDir, () -> {
      myFixture.configureByFile("main.py");
      OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
      myFixture.checkResultByFile(testName + "/main.after.py");
    });
  }
  
  // PY-22656
  public void testPyiStubInInterpreterPaths() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");

    runWithAdditionalClassEntryInSdkRoots(testName + "/stubs", () -> {
      myFixture.configureByFile("main.py");
      OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
      myFixture.checkResultByFile(testName + "/main.after.py");
    });
  }

  // PY-18792
  public void testDisableAlphabeticalOrder() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_IMPORTS = false;
    runWithAdditionalFileInLibDir(
      "sys.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "datetime.py",
          "",
          (___) -> doTest()
        )
    );
  }

  // PY-18792, PY-19292
  public void testOrderNamesInsideFromImport() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doTest();
  }

  // PY-18792, PY-19292
  public void testOrderNamesInsightFromImportDoesntAffectAlreadyOrderedImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doTest();
  }

  // PY-18792, PY-14176
  public void testJoinFromImportsForSameSource() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }
  
  // PY-18792, PY-14176
  public void testJoinFromImportsForSameSourceAndSortNames() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doTest();
  }

  // PY-18792, PY-14176
  public void testJoinFromImportsDoesntAffectSingleImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-18792, PY-14176
  public void testJoinFromImportsIgnoresStarImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-18792, PY-14176
  public void testJoinFromImportsAndRelativeImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-18792
  public void testSortImportsByNameFirstWithinGroup() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST = false;
    doTest();
  }

  // PY-20159
  public void testCaseInsensitiveOrderOfImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER = true;
    doTest();
  }

  // PY-20159
  public void testCaseInsensitiveOrderOfNamesInsideFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER = true;
    doTest();
  }

  // PY-19674
  public void testUnresolvedRelativeImportsShouldBeInProjectGroup() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("pkg/main.py");
    OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
    myFixture.checkResultByFile(testName + "/pkg/main.after.py");
  }

  public void testExtractImportBlockWithIntermediateComments() {
    myFixture.configureByFile(getTestName(true) + ".py");
    final PyFileImpl file = assertInstanceOf(myFixture.getFile(), PyFileImpl.class);
    final List<PyImportStatementBase> block = file.getImportBlock();
    assertSize(2, block);
  }

  public void testExtractImportBlockNoWhitespaceAtEnd() {
    myFixture.configureByFile(getTestName(true) + ".py");
    final PyFileImpl file = assertInstanceOf(myFixture.getFile(), PyFileImpl.class);
    final List<PyImportStatementBase> block = file.getImportBlock();
    assertSize(2, block);
  }

  // PY-19836
  public void testSameNameImportedWithDifferentAliasesInline() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doTest();
  }
  
  // PY-19836
  public void testSameNameImportedWithDifferentAliases() {
    doTest();
  }

  // PY-19836
  public void testSameModuleImportedWithDifferentAliases() {
    doTest();
  }

  // PY-18972
  public void testReferencesInFStringLiterals() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON36,
      () ->
        runWithAdditionalFileInLibDir(
          "sys.py",
          "",
          (__) ->
            runWithAdditionalFileInLibDir(
              "datetime.py",
              "",
              (___) -> doTest()
            )
        )
    );
  }

  // PY-22355
  public void testParenthesesAndTrailingCommaInFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doTest();
  }

  // PY-19837
  public void testCommentsHandling() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doTest();
  }

  // PY-19837
  public void testKeepLicenseComment() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-23035
  public void testCommentsInsideParenthesesInCombinedFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-23086
  public void testTrailingCommentsInCombinedFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-23104
  public void testMultilineImportElementsInCombinedFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }
  
  // PY-23125
  public void testStackDanglingCommentsAtEnd() {
    doTest();
  }

  // PY-23578
  public void testBlankLineBetweenDocstringAndFirstImportPreserved() {
    doTest();
  }

  // PY-23636
  public void testBlankLineBetweenEncodingDeclarationAndFirstImportPreserved() {
    doTest();
  }

  // PY-25567
  public void testExistingParenthesesInReorderedFromImport() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doTest();
  }

  // PY-25567
  public void testExistingParenthesesInCombinedFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-20100
  public void testSplittingOfFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS = true;
    doTest();
  }

  // PY-23475
  public void testModuleLevelDunder() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-23475
  public void testModuleLevelDunderWithImportFromFutureAbove() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    doTest();
  }

  // PY-23475
  public void testModuleLevelDunderWithImportFromFutureBelow() {
    doTest();
  }

  // PY-23475
  public void testImportFromFutureWithRegularImports() {
    doTest();
  }

  private void doMultiFileTest() {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
    myFixture.checkResultByFile(testName + "/main.after.py");
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(true) + ".py");
    OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/optimizeImports";
  }
}
