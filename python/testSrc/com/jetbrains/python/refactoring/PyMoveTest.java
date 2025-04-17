// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersHelper;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersHelper.isMovableModuleMember;

public class PyMoveTest extends PyTestCase {
  public void testFunction() {
    doMoveSymbolTest("f", "b.py");
  }

  public void testClass() {
    doMoveSymbolTest("C", "b.py");
  }

  // PY-11923
  public void testTopLevelVariable() {
    doMoveSymbolTest("Y", "b.py");
  }

  // PY-11923
  public void testMovableTopLevelAssignmentDetection() {
    myFixture.configureByFile("/refactoring/move/" + getTestName(true) + ".py");
    assertFalse(isMovableModuleMember(findFirstNamedElement("X1")));
    assertFalse(isMovableModuleMember(findFirstNamedElement("X3")));
    assertFalse(isMovableModuleMember(findFirstNamedElement("X2")));
    assertFalse(isMovableModuleMember(findFirstNamedElement("X4")));
    assertFalse(isMovableModuleMember(findFirstNamedElement("X5")));
    assertFalse(isMovableModuleMember(findFirstNamedElement("X6")));
    assertFalse(isMovableModuleMember(findFirstNamedElement("X7")));
    assertTrue(isMovableModuleMember(findFirstNamedElement("X8")));
  }

  // PY-15348
  public void testCollectMovableModuleMembers() {
    myFixture.configureByFile("/refactoring/move/" + getTestName(true) + ".py");
    final List<PyElement> members = PyMoveModuleMembersHelper.getTopLevelModuleMembers((PyFile)myFixture.getFile());
    final List<String> names = ContainerUtil.map(members, element -> element.getName());
    assertSameElements(names, "CONST", "C", "outer_func");
  }

  // PY-3929
  // PY-4095
  public void testImportAs() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-3929
  public void testQualifiedImport() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-4074
  public void testNewModule() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-4098
  public void testPackageImport() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-4130
  // PY-4131
  public void testDocstringTypes() {
    doMoveSymbolTest("C", "b.py");
  }

  // PY-4182
  public void testInnerImports() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-5489
  public void testImportSlash() {
    doMoveSymbolTest("function_2", "file2.py");
  }

  // PY-5489
  public void testImportFirstWithSlash() {
    doMoveSymbolTest("function_1", "file2.py");
  }

  // PY-4545
  public void testBaseClass() {
    doMoveSymbolTest("B", "b.py");
  }

  // PY-4379
  public void testModule() {
    doMoveFileTest("p1/p2/m1.py", "p1");
  }

  // PY-5168
  public void testModuleToNonPackage() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doMoveFileTest("p1/p2/m1.py", "nonp3"));
  }

  // PY-6432, PY-15347
  public void testStarImportWithUsages() {
    doMoveSymbolTest("f", "c.py");
  }

  // PY-6447
  public void testFunctionToUsage() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-5850
  public void testSubModuleUsage() {
    doMoveSymbolTest("f", "b.py");
  }

  // PY-6465
  public void testUsageFromFunction() {
    doMoveSymbolTest("use_f", "b.py");
  }

  // PY-6571
  public void testStarImportUsage() {
    doMoveSymbolTest("g", "c.py");
  }

  // PY-13870
  public void testConditionalImport() {
    doMoveFileTest("mod2.py", "pkg1");
  }

  // PY-13870
  public void testConditionalImportFromPackage() {
    doMoveFileTest("pkg1/mod2.py", "");
  }

  // PY-14439
  public void testConditionalImportFromPackageToPackage() {
    doMoveFileTest("pkg1", "pkg2");
  }

  // PY-14979
  public void testTemplateAttributesExpansionInCreatedDestinationModule() {
    final FileTemplateManager instance = FileTemplateManager.getInstance(myFixture.getProject());
    final FileTemplate template = instance.getInternalTemplate("Python Script");
    assertNotNull(template);
    final String oldTemplateContent = template.getText();
    try {
      template.setText("NAME = '${NAME}'");
      doMoveSymbolTest("C", "b.py");
    }
    finally {
      template.setText(oldTemplateContent);
    }
  }

  // PY-7378
  public void testMoveNamespacePackage1() {
    doMoveFileTest("nspkg/nssubpkg", "");
  }

  // PY-7378
  public void testMoveNamespacePackage2() {
    doMoveFileTest("nspkg/nssubpkg/a.py", "");
  }

  // PY-7378
  public void testMoveNamespacePackage3() {
    doMoveFileTest("nspkg/nssubpkg/a.py", "nspkg");
  }

  // PY-14384
  public void testRelativeImportInsideNamespacePackage() {
    String fileName = "nspkg/nssubpkg";
    String toDirName = "";
    doComparingDirectories(testDir -> {
      PyNamespacePackagesService.getInstance(myFixture.getModule())
        .toggleMarkingAsNamespacePackage(testDir.findFileByRelativePath("nspkg"));

      final Project project = myFixture.getProject();
      final PsiManager manager = PsiManager.getInstance(project);
      final VirtualFile virtualFile = testDir.findFileByRelativePath(fileName);
      assertNotNull(virtualFile);
      PsiElement file = manager.findFile(virtualFile);
      if (file == null) {
        file = manager.findDirectory(virtualFile);
      }
      assertNotNull(file);
      final VirtualFile toVirtualDir = testDir.findFileByRelativePath(toDirName);
      assertNotNull(toVirtualDir);
      final PsiDirectory toDir = manager.findDirectory(toVirtualDir);
      new MoveFilesOrDirectoriesProcessor(project, new PsiElement[]{file}, toDir, false, false, null, null).run();

      PyNamespacePackagesService.getInstance(myFixture.getModule()).setNamespacePackageFolders(new ArrayList<>());
    });
  }

  // PY-14384
  public void testRelativeImportInsideNormalPackage() {
    doMoveFileTest("nspkg/nssubpkg", "");
  }

  // PY-14432
  public void testRelativeImportsInsideMovedModule() {
    doMoveFileTest("pkg1/subpkg1", "");
  }


  // PY-14432
  public void testRelativeImportSourceWithSpacesInsideMovedModule() {
    doMoveFileTest("pkg/subpkg1/a.py", "");
  }

  // PY-14595
  public void testNamespacePackageUsedInMovedFunction() {
    doMoveSymbolTest("func", "b.py");
  }

  // PY-14599
  public void testMoveFunctionFromUnimportableModule() {
    doMoveSymbolTest("func", "dst.py");
  }

  // PY-14599
  public void testMoveUnreferencedFunctionToUnimportableModule() {
    doMoveSymbolTest("func", "dst-unimportable.py");
  }

  // PY-14599
  public void testMoveReferencedFunctionToUnimportableModule() {
    try {
      doMoveSymbolTest("func", "dst-unimportable.py");
      fail();
    }
    catch (IncorrectOperationException e) {
      assertEquals("Cannot use a module name 'dst-unimportable.py' in imports", e.getMessage());
    }
  }

  public void testRelativeImportOfNameFromInitPy() {
    doMoveFileTest("pkg/subpkg2", "");
  }

  // PY-15218
  public void testImportForMovedElementWithPreferredQualifiedImportStyle() {
    final boolean defaultImportStyle = PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
    try {
      PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT = false;
      doMoveSymbolTest("bar", "b.py");
    }
    finally {
      PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT = defaultImportStyle;
    }
  }

  // PY-10553
  public void testMoveModuleWithSameNameAsSymbolInside() {
    doMoveFileTest("Animals/Carnivore.py", "Animals/test");
  }

  // PY-14617
  public void testOldStyleRelativeImport() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doMoveFileTest("pkg/a.py", ""));
  }

  // PY-14617
  public void testRelativeImportsToModulesInSameMovedPackageNotUpdated() {
    doMoveFileTest("pkg/subpkg", "");
  }

  // PY-14617
  public void testUsagesOfUnqualifiedOldStyleRelativeImportsInsideMovedModule() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> doMoveFileTest("pkg/m1.py", ""));
  }

  // PY-15324
  public void testInterdependentSymbols() {
    doMoveSymbolsTest("b.py", "f", "A");
  }

  // PY-15343
  public void testDunderAll() {
    doMoveSymbolTest("func", "b.py");
  }

  // PY-54168
  public void testMoveFromInitPyPreserveDunderAll() {
    doMoveSymbolTest("MyClass", "my_class.py");
  }

  // PY-54168
  public void testMoveInHierarchyUpdatesDunderAllInInitPy() {
    doMoveSymbolTest("MyClass", "new.py");
  }

  // PY-15343
  public void testDunderAllSingleElementTuple() {
    doMoveSymbolTest("func", "b.py");
  }

  // PY-15343
  public void testDunderAllTwoElementsTuple() {
    doMoveSymbolTest("func", "b.py");
  }

  // PY-15342
  public void testGlobalStatementWithSingleName() {
    doMoveSymbolTest("VAR", "b.py");
  }

  // PY-15342
  public void testGlobalStatementWithTwoNames() {
    doMoveSymbolTest("VAR", "b.py");
  }

  // PY-15342
  public void testGlobalStatementOnly() {
    doMoveSymbolTest("VAR", "b.py");
  }

  // PY-15350
  public void testMoveSymbolFromStatementList() {
    doMoveSymbolsTest("b.py", "func", "C");
  }

  // PY-14811
  public void testUsageFromFunctionResolvesToDunderAll() {
    doMoveSymbolTest("use_foo", "c.py");
  }

  // PY-14811
  public void testUsageFromFunctionResolvesToDunderAllWithAlias() {
    doMoveSymbolTest("use_foo", "c.py");
  }

  // PY-21366
  public void testFromImportAliases() {
    doMoveSymbolTest("func", "b.py");
  }

  // PY-21292
  public void testStaleFromImportsRemovedWhenSeveralMovedSymbolsUsedInSameModule() {
    doMoveSymbolsTest("b.py", "A", "B");
  }

  // PY-21292
  public void testStaleFromImportRemovedWhenNewImportCombinedWithExistingImport() {
    doMoveSymbolTest("A", "b.py");
  }

  // PY-20427
  public void testQualifiedReferenceInDestinationModule() {
    doMoveSymbolTest("FOO", "b.py");
  }

  // PY-21220
  public void testReferenceToClassWithNewInMovedSymbol() {
    doMoveSymbolTest("fnToMove", "toFile.py");
  }

  // PY-22422
  public void testReformatFromImports() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_JOIN_FROM_IMPORTS_WITH_SAME_SOURCE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;
    getPythonCodeStyleSettings().FROM_IMPORT_PARENTHESES_FORCE_IF_MULTILINE = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_AFTER_LEFT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_NEW_LINE_BEFORE_RIGHT_PARENTHESIS = true;
    getPythonCodeStyleSettings().FROM_IMPORT_TRAILING_COMMA_IF_MULTILINE = true;
    doMoveSymbolTest("func", "b.py");
  }

  // PY-24365
  public void testOptimizeImportsAfterMoveInvalidatesMembersToBeMoved() {
    doMoveSymbolsTest("dst.py", "Class1", "Class2");
  }

  // PY-24365
  public void testCleanupImportsAfterMove() {
    doMoveSymbolsTest("other.py", "C1", "C2");
  }

  // PY-18216
  public void testMoveSymbolDoesntReorderImportsInOriginFile() {
    doMoveSymbolTest("func", "other.py");
  }

  // PY-18216
  public void testMoveSymbolDoesntReorderImportsInUsageFile() {
    doMoveSymbolTest("func", "other.py");
  }

  // PY-18216
  public void testMoveFileDoesntReorderImports() {
    doMoveFileTest("b.py", "pkg");
  }

  // PY-20100
  public void testMoveDoesntMergeFromImportsAccordingToCodeStyle() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS = true;
    doMoveSymbolsTest("dst.py", "func");
  }

  // PY-8415
  public void testMoveSymbolDoesntCreateInitPyInSourceRoot() {
    doComparingDirectories(testDir -> {
      final VirtualFile sourceRoot = testDir.findFileByRelativePath("src");
      runWithSourceRoots(Collections.singletonList(sourceRoot), () -> moveSymbols(testDir, "src/pkg/subpkg/b.py", "MyClass"));
    });
  }

  public void testMoveSymbolFromTopLevelModuleToNewPackageCreatesInitPy() {
    doComparingDirectories(testDir -> {
      moveSymbols(testDir, "pkg/subpkg/b.py", "MyClass");
    });
  }

  //PY-44858
  public void testMoveNotCreateInitPyForNamespacePackagesToAnotherDirectory() {
    doMoveSymbolsTest("pkg/subpkg/B/module_b.py", "myfunc");
  }

  //PY-44858
  public void testMoveNotCreateInitPyForNamespacePackagesInSameDirectory() {
    doMoveSymbolsTest("pkg/subpkg/module_b.py", "myfunc");
  }

  //PY-44858
  public void testMoveNotCreateInitPyForNamespacePackagesToParentDirectory() {
    doMoveSymbolsTest("pkg/subpkg/B/module_b.py", "myfunc");
  }

  //PY-44858
  public void testMoveNotCreateInitPyForNamespacePackagesToChildDirectory() {
    doMoveSymbolsTest("pkg/subpkg/A/B/module_b.py", "myfunc");
  }

  // PY-23968
  public void testUpdatingNamesInFromImportsRespectsOrder() {
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_IMPORTS = true;
    getPythonCodeStyleSettings().OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS = true;
    doMoveSymbolTest("func", "dst.py");
  }

  // PY-16221
  public void testFromFutureImports() {
    doMoveSymbolTest("C", "b.py");
  }

  // PY-16221
  public void testExistingFromFutureImportsNotDuplicated() {
    doMoveSymbolTest("C", "b.py");
  }

  // PY-23831
  public void testWithImportedForwardReferencesInTypeHints() {
    doMoveSymbolTest("test", "dst.py");
  }

  // PY-23831
  public void testWithImportedFunctionTypeComments() {
    doMoveSymbolTest("test", "dst.py");
  }

  // PY-23831
  public void testWithImportedTypeComments() {
    doMoveSymbolTest("test", "dst.py");
  }

  private void doComparingDirectories(@NotNull Consumer<VirtualFile> testDirConsumer) {
    final String root = "/refactoring/move/" + getTestName(true);
    final String rootBefore = root + "/before/src";
    final String rootAfter = root + "/after/src";

    final VirtualFile testDir = myFixture.copyDirectoryToProject(rootBefore, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();

    testDirConsumer.consume(testDir);

    final VirtualFile expectedDir = getVirtualFileByName(PythonTestUtil.getTestDataPath() + rootAfter);
    try {
      PlatformTestUtil.assertDirectoriesEqual(expectedDir, testDir);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doMoveFileTest(String fileName, String toDirName) {
    doComparingDirectories(testDir -> {
      final Project project = myFixture.getProject();
      final PsiManager manager = PsiManager.getInstance(project);
      final VirtualFile virtualFile = testDir.findFileByRelativePath(fileName);
      assertNotNull(virtualFile);
      PsiElement file = manager.findFile(virtualFile);
      if (file == null) {
        file = manager.findDirectory(virtualFile);
      }
      assertNotNull(file);
      final VirtualFile toVirtualDir = testDir.findFileByRelativePath(toDirName);
      assertNotNull(toVirtualDir);
      final PsiDirectory toDir = manager.findDirectory(toVirtualDir);
      new MoveFilesOrDirectoriesProcessor(project, new PsiElement[]{file}, toDir, false, false, null, null).run();
    });
  }

  private void doMoveSymbolsTest(@NotNull String toFileName, String... symbolNames) {
    doComparingDirectories(testDir -> moveSymbols(testDir, toFileName, symbolNames));
  }

  private void moveSymbols(@NotNull VirtualFile testDir, @NotNull String toFileName, String @NotNull ... symbolNames) {
    final PsiNamedElement[] symbols = ContainerUtil.map2Array(symbolNames, PsiNamedElement.class, name -> {
      final PsiNamedElement found = findFirstNamedElement(name);
      assertNotNull("Symbol '" + name + "' does not exist", found);
      return found;
    });

    final VirtualFile toVirtualFile = testDir.findFileByRelativePath(toFileName);
    final String path = toVirtualFile != null ? toVirtualFile.getPath() : (testDir.getPath() + "/" + toFileName);
    new PyMoveModuleMembersProcessor(symbols, path).run();
  }


  private void doMoveSymbolTest(String symbolName, String toFileName) {
    doMoveSymbolsTest(toFileName, symbolName);
  }

  @Nullable
  private PsiNamedElement findFirstNamedElement(String name) {
    final Project project = myFixture.getProject();
    final GlobalSearchScope scope = ProjectScope.getProjectScope(project);

    final Collection<PyClass> classes = PyClassNameIndex.find(name, project, scope);
    if (classes.size() > 0) {
      return classes.iterator().next();
    }
    final Collection<PyFunction> functions = PyFunctionNameIndex.find(name, project, scope);
    if (functions.size() > 0) {
      return functions.iterator().next();
    }
    final Collection<PyTargetExpression> targets = PyVariableNameIndex.find(name, project, scope);
    if (targets.size() > 0) {
      return targets.iterator().next();
    }
    return null;
  }

  @NotNull
  private PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }
}
