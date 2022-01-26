/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.quickFixes;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.ImportCandidateHolder;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyModuleNameIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
public class PyAddImportQuickFixTest extends PyQuickFixTestCase {
  @NotNull
  private PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }

  // PY-19773
  public void testReexportedName() {
    doMultiFileAutoImportTest("Import 'flask.request'");
  }

  public void testOsPathFunctions() {
    Consumer<VirtualFile> fileConsumer = file -> {
      doMultiFileAutoImportTest("Import", fix -> {
        final List<ImportCandidateHolder> candidates = fix.getCandidates();
        final List<String> names = ContainerUtil.map(candidates, c -> c.getPresentableText());
        assertSameElements(names, "os.path.commonpath()");
        return true;
      });
    };
    runWithAdditionalFileInLibDir(
      "ntpath.py",
      "def commonpath(paths):\n" +
      "    pass",
      f -> runWithAdditionalFileInLibDir(
        "os.py",
        "if windows():\n" +
        "    import ntpath as path\n" +
        "else:\n" +
        "    import posixpath as path",
        f1 -> runWithAdditionalFileInLibDir(
          "posixpath.py",
          "def commonpath(paths):\n" +
          "    pass",
          fileConsumer
        )
      )
    );
  }

  // PY-19975
  public void testCanonicalNamesFromHigherLevelPackage() {
    doMultiFileAutoImportTest("Import", fix -> {
      final List<ImportCandidateHolder> candidates = fix.getCandidates();
      final List<String> names = ContainerUtil.map(candidates, c -> c.getPresentableText());
      assertOrderedEquals(names, "bar.MyClass", "foo.MyClass");
      return true;
    });
  }

  // PY-22422
  public void testAddParenthesesAndTrailingCommaToUpdatedFromImport() {
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doMultiFileAutoImportTest("Import 'bar from module'");
  }

  // PY-21563
  public void testCombineFromImportsForReferencesInTypeComment() {
    doMultiFileAutoImportTest("Import 'typing.FrozenSet'");
  }

  // PY-25234
  public void testBinarySkeletonStdlibModule() {
    runWithAdditionalFileInLibDir(
      "re.py",
      "",
      (__) ->
        runWithAdditionalFileInSkeletonDir(
          "sys.py",
          "# encoding: utf-8\n" +
          "# module sys\n" +
          "# from (built-in)\n" +
          "# by generator 1.138\n" +
          "path = 10",
          (___) -> doMultiFileAutoImportTest("Import 'sys'")
        )
    );
  }

  // PY-25234
  public void testUserSkeletonStdlibModule() {
    runWithAdditionalClassEntryInSdkRoots(getTestName(true) + "/site-packages", () -> {
      doMultiFileAutoImportTest("Import 'alembic'");
    });
  }

  // PY-16176
  public void testAllVariantsSuggestedWhenExistingNonProjectImportFits() {
    doMultiFileAutoImportTest("Import", quickfix -> {
      final List<String> candidates = ContainerUtil.map(quickfix.getCandidates(), c -> c.getPresentableText());
      assertOrderedEquals(candidates, "time from datetime", "time");
      return false;
    });
  }

  // PY-16176
  public void testExistingImportsAlwaysSuggestedFirstEvenIfLonger() {
    doMultiFileAutoImportTest("Import", quickfix -> {
      final List<String> candidates = ContainerUtil.map(quickfix.getCandidates(), c -> c.getPresentableText());
      assertOrderedEquals(candidates, "ClassB from long.pkg.path", "short.ClassB");
      return false;
    });
  }

  // PY-16176
  public void testExistingImportsAlwaysSuggestedFirstEvenIfNonProject() {
    doMultiFileAutoImportTest("Import", quickfix -> {
      final List<String> candidates = ContainerUtil.map(quickfix.getCandidates(), c -> c.getPresentableText());
      assertOrderedEquals(candidates, "datetime(date) from datetime", "mod.datetime");
      return false;
    });
  }

  // PY-28752
  public void testFullFromImportSourceNameInSuggestion() {
    doMultiFileAutoImportTest("Import 'ClassB from foo.bar.baz'");
  }

  // PY-24450
  public void testAvailableForUnqualifiedDecoratorWithoutArguments() {
    doMultiFileAutoImportTest("Import 'pytest'");
  }

  // PY-24450
  public void testUnavailableForUnqualifiedDecoratorWithArguments() {
    doMultiFileNegativeTest("Import 'pytest'");
  }

  // PY-20100
  public void testAlwaysSplitFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS = true;
    doMultiFileAutoImportTest("Import 'mod.bar()'");
  }

  // PY-20976
  public void testCombinedElementOrdering() {
    runWithAdditionalFileInLibDir(
      "os/__init__.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "os/path.py",
          "",
          (___) -> doTestProposedImportsOrdering(
            "path from sys", "first.path", "first.second.path()", "os.path", "first._third.path")
        )
    );
  }

  // PY-20976
  public void testOrderingLocalBeforeStdlib() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "path = 10",
      (__) -> doTestProposedImportsOrdering("pkg.path", "sys.path")
    );
  }

  // PY-20976
  public void testOrderingUnderscoreInPath() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "path = 10",
      (__) -> doTestProposedImportsOrdering("first.second.path", "sys.path", "_private.path")
    );
  }

  // PY-20976
  public void testOrderingSymbolBeforeModule() {
    doTestProposedImportsOrdering("first.module.foo()", "first.a.foo");
  }

  // PY-20976
  public void testOrderingModuleBeforePackage() {
    doTestProposedImportsOrdering("b.foo", "a.foo");
  }

  // PY-20976
  public void testOrderingPathComponentsNumber() {
    doTestProposedImportsOrdering("c.foo", "b.c.foo", "a.b.c.foo");
  }

  // PY-20976
  public void testOrderingWithExistingImport() {
    runWithAdditionalFileInLibDir(
      "os/__init__.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "os/path.py",
          "",
          (___) -> doTestProposedImportsOrdering("path from sys", "src.path", "os.path")
        )
    );
  }

  // PY-34818
  public void testReferenceInsideFString() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> {
      doMultiFileAutoImportTest("Import");
    });
  }

  // PY-23968
  public void testOrderingOfNamesInFromImportBeginning() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doMultiFileAutoImportTest("Import");
  }

  // PY-23968
  public void testOrderingOfNamesInFromImportInTheMiddle() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doMultiFileAutoImportTest("Import");
  }

  // PY-23968
  public void testOrderingOfNamesInFromImportEnd() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doMultiFileAutoImportTest("Import");
  }

  // PY-36374
  public void testGlobalDefinitionDoesNotShadowCommonPackageAliasVariant() {
    doMultiFileAutoImportTest("Import", fix -> {
      List<ImportCandidateHolder> candidates = fix.getCandidates();
      ImportCandidateHolder importNumpyAsNpVariant = ContainerUtil.find(candidates, c -> {
        PsiDirectory dir = as(c.getImportable(), PsiDirectory.class);
        return dir != null && dir.getName().equals("numpy") && "np".equals(c.getAsName());
      });
      assertNotNull(importNumpyAsNpVariant);
      List<String> candidateText = ContainerUtil.map(fix.getCandidates(), c -> c.getPresentableText());
      assertContainsInRelativeOrder(candidateText, "numpy as np", "pandas.np");
      return true;
    });
  }

  // PY-36374
  public void testUnrelatedSubpackagesNotSuggestedForCommonPackageAliases() {
    doMultiFileAutoImportTest("Import", fix -> {
      ImportCandidateHolder onlyCandidate = assertOneElement(fix.getCandidates());
      assertEquals("numpy as np", onlyCandidate.getPresentableText());
      return false;
    });
  }

  public void testCommonPackageAlias() {
    doMultiFileAutoImportTest("Import");
  }

  // PY-46356
  public void testCommonSubModuleAliasPlainImport() {
    PyCodeInsightSettings codeInsightSettings = PyCodeInsightSettings.getInstance();
    boolean oldPreferFromImport = codeInsightSettings.PREFER_FROM_IMPORT;
    codeInsightSettings.PREFER_FROM_IMPORT = false;
    try {
      doMultiFileAutoImportTest("Qualify with an imported module");
    }
    finally {
      codeInsightSettings.PREFER_FROM_IMPORT = oldPreferFromImport;
    }
  }

  // PY-46356
  public void testCommonSubModuleAliasFromImport() {
    doMultiFileAutoImportTest("Import");
  }

  // PY-46358
  public void testLocalPlainImportForCommonPackageAlias() {
    doMultiFileAutoImportTest("Import 'numpy as np' locally");
  }

  // PY-46358
  public void testLocalFromImportForCommonPackageAlias() {
    doMultiFileAutoImportTest("Import 'matplotlib.pyplot as plt' locally");
  }

  // PY-46361
  public void testPackagesFromPythonSkeletonsNotSuggested() {
    GlobalSearchScope scope = GlobalSearchScope.allScope(myFixture.getProject());
    List<PyFile> djangoPackages = PyModuleNameIndex.findByQualifiedName(QualifiedName.fromComponents("django"),
                                                                       myFixture.getProject(), scope);
    if (djangoPackages.size() != 1) {
      dumpSdkRoots();
      dumpDjangoModulesFromModuleIndex(scope);
    }
    PyFile djangoPackage = assertOneElement(djangoPackages);
    assertTrue(PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(djangoPackage));

    doMultiFileNegativeTest("Import");
  }

  // PY-46361
  public void testClassesFromPythonSkeletonsNotSuggested() {
    PyClass djangoViewClass = PyClassNameIndex.findClass("django.views.generic.base.View", myFixture.getProject());
    if (djangoViewClass == null) {
      dumpSdkRoots();
      dumpDjangoModulesFromModuleIndex(GlobalSearchScope.allScope(myFixture.getProject()));
    }
    assertNotNull(djangoViewClass);
    assertTrue(PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(djangoViewClass.getContainingFile()));

    doMultiFileNegativeTest("Import");
  }

  private static void dumpDjangoModulesFromModuleIndex(@NotNull GlobalSearchScope scope) {
    final List<PyFile> djangoModules = PyModuleNameIndex.findByShortName("django", scope.getProject(), scope);
    System.out.println(StringUtil.join(djangoModules, m -> m.getVirtualFile().getPath(), "\n"));
  }

  // PY-46344
  public void testImportAbstractContainersFromCollectionsABC() {
    Consumer<VirtualFile> fileConsumer = file -> {
      doMultiFileAutoImportTest("Import", fix -> {
        final List<String> candidates = ContainerUtil.map(fix.getCandidates(), c -> c.getPresentableText());
        assertNotNull(candidates);
        assertContainsElements(candidates, "collections.abc.Sized");
        assertDoesntContain(candidates, "collections.Sized");
        return false;
      });
    };
    runWithAdditionalFileInLibDir(
      "_collections_abc.py",
      "__all__ = [\"Sized\"]\n" +
      "__name__ = \"collections.abc\"\n" +
      "class Sized:\n" +
      "    pass\n",
      fileConsumer
    );
  }

  private void doTestProposedImportsOrdering(String @NotNull ... expected) {
    doMultiFileAutoImportTest("Import", fix -> {
      final List<String> candidates = ContainerUtil.map(fix.getCandidates(), c -> c.getPresentableText());
      assertNotNull(candidates);
      assertContainsInRelativeOrder(candidates, expected);
      return false;
    });
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix) {
    doMultiFileAutoImportTest(hintPrefix, null);
  }

  private void doMultiFileAutoImportTest(@NotNull String hintPrefix, @Nullable Processor<? super AutoImportQuickFix> checkQuickfix) {
    configureMultiFileProject();

    final PsiElement hostUnderCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    final PyReferenceExpression hostRefExpr = PsiTreeUtil.getParentOfType(hostUnderCaret, PyReferenceExpression.class);

    final InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(myFixture.getProject());
    final PsiElement injectedUnderCaret = injectionManager.findInjectedElementAt(myFixture.getFile(), myFixture.getCaretOffset());
    final PyReferenceExpression injectionRefExpr = PsiTreeUtil.getParentOfType(injectedUnderCaret, PyReferenceExpression.class);

    final PyReferenceExpression unresolvedRefExpr = ObjectUtils.chooseNotNull(injectionRefExpr, hostRefExpr);
    assertNotNull(unresolvedRefExpr);
    final AutoImportQuickFix quickfix = PythonImportUtils.proposeImportFix(unresolvedRefExpr, unresolvedRefExpr.getReference());
    assertNotNull(quickfix);

    final boolean applyFix = checkQuickfix == null || checkQuickfix.process(quickfix);
    if (applyFix) {
      myFixture.launchAction(myFixture.findSingleIntention(hintPrefix));
      myFixture.checkResultByFile(getTestName(true) + "/main_after.py", true);
    }
  }

  private void doMultiFileNegativeTest(@NotNull String hintPrefix) {
    configureMultiFileProject();
    assertEmpty(myFixture.filterAvailableIntentions(hintPrefix));
  }

  private void configureMultiFileProject() {
    myFixture.copyDirectoryToProject(getTestName(true), "");
    myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
    myFixture.configureByFile("main.py");
    myFixture.checkHighlighting(true, false, false);
  }
}
