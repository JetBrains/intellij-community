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

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.fixtures.PyTestCase;

/**
 * @author yole
 */
public class PyAddImportTest extends PyTestCase {
  public void testAddBuiltin() {
    myFixture.configureByFile("addImport/addBuiltin.py");
    new WriteCommandAction(myFixture.getProject(), myFixture.getFile()) {
      @Override
      protected void run(Result result) throws Throwable {
        AddImportHelper.addImportStatement(myFixture.getFile(), "re", null, AddImportHelper.ImportPriority.BUILTIN);
      }
    }.execute();
    myFixture.checkResultByFile("addImport/addBuiltin.after.py");
  }

  public void testParens() {  // PY-7400
    doAddImportFrom("urllib", "unquote_plus");
  }

  public void testComment() {  // PY-8034
    doAddImportFrom("urllib", "unquote_plus");
  }

  private void doAddImportFrom(final String path, final String name) {
    myFixture.configureByFile("addImport/" + getTestName(true) + ".py");
    new WriteCommandAction(myFixture.getProject(), myFixture.getFile()) {
      @Override
      protected void run(Result result) throws Throwable {
        AddImportHelper.addImportFrom(myFixture.getFile(), null, path, name, null, AddImportHelper.ImportPriority.BUILTIN);
      }
    }.execute();
    myFixture.checkResultByFile("addImport/" + getTestName(true) + ".after.py");
  }
}
