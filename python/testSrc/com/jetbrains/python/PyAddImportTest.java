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

  public void testImportInParens() {  // PY-7400
    myFixture.configureByFile("addImport/parens.py");
    new WriteCommandAction(myFixture.getProject(), myFixture.getFile()) {
      @Override
      protected void run(Result result) throws Throwable {
        AddImportHelper.addImportFrom(myFixture.getFile(), null, "urllib", "unquote_plus", null, AddImportHelper.ImportPriority.BUILTIN);
      }
    }.execute();
    myFixture.checkResultByFile("addImport/parens.after.py");
  }
}
