/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority;
import com.jetbrains.python.fixtures.PyResolveTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.codeInsight.imports.AddImportHelper.ImportPriority.*;

/**
 * @author yole
 */
public class PyAddImportTest extends PyTestCase {
  public void testAddBuiltin() {
    doAddImport("re", BUILTIN);
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
    doAddImportWithResolveInProject("sys", BUILTIN);
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
    doAddFromImport("mod", "b", BUILTIN);
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

  private void doAddOrUpdateFromImport(final String path, final String name, final ImportPriority priority) {
    myFixture.configureByFile(getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        AddImportHelper.addOrUpdateFromImportStatement(myFixture.getFile(), path, name, null, priority, null);
      }
    });
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  private void doAddFromImport(final String path, final String name, final ImportPriority priority) {
    myFixture.configureByFile(getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        AddImportHelper.addFromImportStatement(myFixture.getFile(), path, name, null, priority, null);
      }
    });
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  private void doAddImport(final String name, final ImportPriority priority) {
    myFixture.configureByFile(getTestName(true) + ".py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        AddImportHelper.addImportStatement(myFixture.getFile(), name, null, priority, null);
      }
    });
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  private void doAddImportWithResolveInProject(final String name, final ImportPriority priority) {
    final String testName = getTestName(true);
    myFixture.copyDirectoryToProject(testName, "");
    myFixture.configureByFile("main.py");
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        AddImportHelper.addImportStatement(myFixture.getFile(), name, null, priority, null);
      }
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
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), new Runnable() {
      @Override
      public void run() {
        final PsiPolyVariantReference reference = PyResolveTestCase.findReferenceByMarker(myFixture.getFile());
        if (qualifier != null) {
          AddImportHelper.addLocalFromImportStatement(reference.getElement(), qualifier, name);
        }
        else {
          AddImportHelper.addLocalImportStatement(reference.getElement(), name);
        }
      }
    });
    myFixture.checkResultByFile(getTestName(true) + ".after.py");
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/addImport";
  }
}
