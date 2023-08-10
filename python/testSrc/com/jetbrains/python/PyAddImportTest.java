// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.refactoring.PyPsiRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority.*;


public class PyAddImportTest extends PyTestCase {
  public void testAddBuiltin() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "datetime.py",
          "",
          (___) -> doAddImport("re", BUILTIN)
        )
    );
  }

  // PY-7400
  public void testParens() {
    doAddOrUpdateFromImport("urllib", "unquote_plus", BUILTIN);
  }

  // PY-8034
  public void testComment() {
    doAddOrUpdateFromImport("urllib", "unquote_plus", BUILTIN);
  }

  // PY-14765
  public void testNewFirstImportInBuiltinGroup() {
    doAddImportWithResolveInProject("datetime", BUILTIN);
  }

  // PY-14765
  public void testNewLastImportInBuiltinGroup() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "",
      (__) ->
        runWithAdditionalFileInLibDir(
          "datetime.py",
          "",
          (___) -> doAddImportWithResolveInProject("sys", BUILTIN)
        )
    );
  }

  // PY-14765
  public void testNewFirstImportInProjectGroup() {
    doAddImportWithResolveInProject("a", PROJECT);
  }

  // PY-14765
  public void testNewFirstImportInProjectGroupWithExistingBlankLineAbove() {
    doAddImportWithResolveInProject("a", PROJECT);
  }

  // PY-14765
  public void testNewLastImportInProjectGroup() {
    doAddImportWithResolveInProject("b", PROJECT);
  }

  // PY-14765
  public void testNewThirdPartyImportInBetween() {
    doAddImportWithResolveInProject("third_party", THIRD_PARTY);
  }

  // PY-12018
  public void testNewFromImportFromSameModule() {
    doAddFromImport("mod", "b", THIRD_PARTY);
  }

  // PY-6020
  public void testLocalFromImport() {
    doAddLocalImport("foo", "package.module");
  }

  // PY-6020
  public void testLocalImport() {
    doAddLocalImport("module", null);
  }

  // PY-13668
  public void testLocalImportInlineFunctionBody() {
    testLocalImport();
  }

  // PY-13668
  public void testLocalImportInlineBranch() {
    testLocalImport();
  }

  // PY-18098
  public void testIgnoreImportedAsModule() {
    doAddImport("numpy", THIRD_PARTY);
  }

  // PY-16373
  public void testLocalImportQuickFixAvailable() {
    runWithAdditionalFileInLibDir(
      "sys.py",
      "path = 10",
      (__) -> {
        myFixture.configureByFile(getTestName(true) + ".py");
        myFixture.enableInspections(PyUnresolvedReferencesInspection.class);
        assertNotNull(myFixture.findSingleIntention("Import 'sys' locally"));
      }
    );
  }

  // PY-23475
  public void testModuleLevelDunder() {
    doAddFromImport("collections", "OrderedDict", BUILTIN);
  }

  // PY-23475
  public void testModuleLevelDunderAndImportFromFuture() {
    doAddFromImport("collections", "OrderedDict", BUILTIN);
  }

  // PY-23475
  public void testModuleLevelDunderAndExistingImport(){
    doAddFromImport("collections", "OrderedDict", BUILTIN);
  }

  // PY-23475
  public void testModuleLevelDunderAndDocstring(){
    doAddFromImport("collections", "OrderedDict", BUILTIN);
  }

  // PY-6054
  public void testRelativeImportFromSamePackage() {
    doTestRelativeImport("foo.baz", "baz_func", "foo/test");
  }

  // PY-6054
  public void testRelativeImportFromAlreadyImportedModule() {
    doTestRelativeImport("foo.bar", "no", "foo/test");
  }

  // PY-6054
  public void testRelativeImportInInitFile() {
    doTestRelativeImport("foo.src.baz", "func", "foo/test/__init__");
  }

  // PY-6054
  public void testRelativeImportInFileWithMain() {
    doTestRelativeImport("foo.baz", "baz_func", "foo/test");
  }

  // PY-6054
  public void testRelativeImportTooDeep() {
    doTestRelativeImport("pkg1.foo", "foo_func", "pkg1/pkg2/pkg3/pkg4/test");
  }

  // PY-6054
  public void testRelativeImportTooDeepWithSameLevelUsed() {
    doTestRelativeImport("pkg1.foo", "foo_func", "pkg1/pkg2/pkg3/pkg4/test");
  }

  // PY-6054
  public void testRelativeImportWithDotsOnly() {
    doTestRelativeImport("foo", "lib", "foo/bar/test");
  }

  public void testImportForMethodCannotBeAdded() {
    String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    Collection<PyClass> pyClasses = PyClassNameIndex.find("MyClass", myFixture.getProject(), false);
    PyClass pyClass = assertOneElement(pyClasses);
    TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(myFixture.getProject(), myFixture.getFile());
    PyFunction method = pyClass.findMethodByName("method", false, typeEvalContext);
    assertNotNull(method);
    Ref<Boolean> inserted = Ref.create();
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      inserted.set(PyPsiRefactoringUtil.insertImport(myFixture.getFile(), method, null));
    });
    assertFalse(inserted.get());
    myFixture.checkResultByFile(testName + "/main.py");
  }

  private void doAddOrUpdateFromImport(final String path, final String name, final ImportPriority priority) {
    myFixture.configureByFile(getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      AddImportHelper.addOrUpdateFromImportStatement(myFixture.getFile(), path, name, null, priority, null);
    });
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  private void doAddFromImport(final String path, final String name, final ImportPriority priority) {
    myFixture.configureByFile(getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> AddImportHelper.addFromImportStatement(myFixture.getFile(), path, name, null, priority, null));
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  private void doAddImport(final String name, final ImportPriority priority) {
    myFixture.configureByFile(getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      AddImportHelper.addImportStatement(myFixture.getFile(), name, null, priority, null);
    });
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  private void doAddImportWithResolveInProject(final String name, final ImportPriority priority) {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      AddImportHelper.addImportStatement(myFixture.getFile(), name, null, priority, null);
    });
    myFixture.checkResultByFile(testName + "/main.after.py");
  }

  /**
   * Add local import statement
   *
   * @param name      reference name in corresponding import element
   * @param qualifier if not {@code null} form {@code from qualifier import name} will be used, otherwise {@code import name}
   */
  private void doAddLocalImport(@NotNull final String name, @Nullable final String qualifier) {
    myFixture.configureByFile(getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      final PsiReference reference = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
      if (qualifier != null) {
        AddImportHelper.addLocalFromImportStatement(reference.getElement(), qualifier, name, null);
      }
      else {
        AddImportHelper.addLocalImportStatement(reference.getElement(), name, null);
      }
    });
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  private void doTestRelativeImport(final @NotNull String from, final @NotNull String name, final @NotNull String file) {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile(file + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      AddImportHelper.addOrUpdateFromImportStatement(myFixture.getFile(), from, name, null, PROJECT, null);
    });
    myFixture.checkResultByFile(testName + "/" + file + ".after.py");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/addImport";
  }
}
