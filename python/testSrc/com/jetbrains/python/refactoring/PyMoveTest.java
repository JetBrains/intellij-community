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
package com.jetbrains.python.refactoring;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.ProjectScope;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersHelper;
import com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.refactoring.move.moduleMembers.PyMoveModuleMembersHelper.isMovableModuleMember;

/**
 * @author vlan
 */
public class PyMoveTest extends PyTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SystemProperties.setTestUserName("user1");
  }

  @Override
  protected void tearDown() throws Exception {
    SystemProperties.setTestUserName(null);
    super.tearDown();
  }

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
    runWithLanguageLevel(LanguageLevel.PYTHON30, () -> {
      myFixture.configureByFile("/refactoring/move/" + getTestName(true) + ".py");
      assertFalse(isMovableModuleMember(findFirstNamedElement("X1")));
      assertFalse(isMovableModuleMember(findFirstNamedElement("X3")));
      assertFalse(isMovableModuleMember(findFirstNamedElement("X2")));
      assertFalse(isMovableModuleMember(findFirstNamedElement("X4")));
      assertFalse(isMovableModuleMember(findFirstNamedElement("X5")));
      assertFalse(isMovableModuleMember(findFirstNamedElement("X6")));
      assertFalse(isMovableModuleMember(findFirstNamedElement("X7")));
      assertTrue(isMovableModuleMember(findFirstNamedElement("X8")));
    });
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
    doMoveFileTest("p1/p2/m1.py", "nonp3");
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
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doMoveFileTest("nspkg/nssubpkg", ""));
  }

  // PY-7378
  public void testMoveNamespacePackage2() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doMoveFileTest("nspkg/nssubpkg/a.py", ""));
  }

  // PY-7378
  public void testMoveNamespacePackage3() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doMoveFileTest("nspkg/nssubpkg/a.py", "nspkg"));
  }

  // PY-14384
  public void testRelativeImportInsideNamespacePackage() {
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doMoveFileTest("nspkg/nssubpkg", ""));
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
    runWithLanguageLevel(LanguageLevel.PYTHON33, () -> doMoveSymbolTest("func", "b.py"));
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
      assertEquals("Cannot use module name 'dst-unimportable.py' in imports", e.getMessage());
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
    doMoveFileTest("pkg/a.py", "");
  }

  // PY-14617
  public void testRelativeImportsToModulesInSameMovedPackageNotUpdated() {
    doMoveFileTest("pkg/subpkg", "");
  }

  // PY-14617
  public void testUsagesOfUnqualifiedOldStyleRelativeImportsInsideMovedModule() {
    doMoveFileTest("pkg/m1.py", "");
  }

  // PY-15324
  public void testInterdependentSymbols() {
    doMoveSymbolsTest("b.py", "f", "A");
  }

  // PY-15343
  public void testDunderAll() {
    doMoveSymbolTest("func", "b.py");
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

  private void doMoveFileTest(String fileName, String toDirName) {
    Project project = myFixture.getProject();
    PsiManager manager = PsiManager.getInstance(project);

    String root = "/refactoring/move/" + getTestName(true);
    String rootBefore = root + "/before/src";
    String rootAfter = root + "/after/src";

    VirtualFile dir1 = myFixture.copyDirectoryToProject(rootBefore, "");
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    VirtualFile virtualFile = dir1.findFileByRelativePath(fileName);
    assertNotNull(virtualFile);
    PsiElement file = manager.findFile(virtualFile);
    if (file == null) {
      file = manager.findDirectory(virtualFile);
    }
    assertNotNull(file);
    VirtualFile toVirtualDir = dir1.findFileByRelativePath(toDirName);
    assertNotNull(toVirtualDir);
    PsiDirectory toDir = manager.findDirectory(toVirtualDir);
    new MoveFilesOrDirectoriesProcessor(project, new PsiElement[]{file}, toDir, false, false, null, null).run();

    VirtualFile dir2 = getVirtualFileByName(PythonTestUtil.getTestDataPath() + rootAfter);
    try {
      PlatformTestUtil.assertDirectoriesEqual(dir2, dir1);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void doMoveSymbolsTest(@NotNull String toFileName, String... symbolNames) {
    String root = "/refactoring/move/" + getTestName(true);
    String rootBefore = root + "/before/src";
    String rootAfter = root + "/after/src";
    VirtualFile dir1 = myFixture.copyDirectoryToProject(rootBefore, "");
    PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();

    final PsiNamedElement[] symbols = ContainerUtil.map2Array(symbolNames, PsiNamedElement.class, name -> {
      final PsiNamedElement found = findFirstNamedElement(name);
      assertNotNull("Symbol '" + name + "' does not exist", found);
      return found;
    });

    VirtualFile toVirtualFile = dir1.findFileByRelativePath(toFileName);
    String path = toVirtualFile != null ? toVirtualFile.getPath() : (dir1.getPath() + "/" + toFileName);
    new PyMoveModuleMembersProcessor(symbols, path).run();

    VirtualFile dir2 = getVirtualFileByName(PythonTestUtil.getTestDataPath() + rootAfter);
    try {
      PlatformTestUtil.assertDirectoriesEqual(dir2, dir1);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  private void doMoveSymbolTest(String symbolName, String toFileName) {
    doMoveSymbolsTest(toFileName, symbolName);
  }

  @Nullable
  private PsiNamedElement findFirstNamedElement(String name) {
    final Project project = myFixture.getProject();
    final Collection<PyClass> classes = PyClassNameIndex.find(name, project, false);
    if (classes.size() > 0) {
      return classes.iterator().next();
    }
    final Collection<PyFunction> functions = PyFunctionNameIndex.find(name, project);
    if (functions.size() > 0) {
      return functions.iterator().next();
    }
    final Collection<PyTargetExpression> targets = PyVariableNameIndex.find(name, project, ProjectScope.getAllScope(project));
    if (targets.size() > 0) {
      return targets.iterator().next();
    }
    return null;
  }
}