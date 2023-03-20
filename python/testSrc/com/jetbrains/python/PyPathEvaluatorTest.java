// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.idea.RecordExecution;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPathEvaluator;

import java.util.List;


@RecordExecution(includePackages = {"com.jetbrains.python.**"})
public class PyPathEvaluatorTest extends PyTestCase {
  public void testDirName() {
    assertEquals("/foo/bar", doEvaluate("os.path.dirname(__file__)", "/foo/bar/baz.py"));
  }

  public void testOsPathJoin() {
    assertEquals("/foo/bar/db.sqlite3", doEvaluate("os.path.join(__file__, 'db.sqlite3'", "/foo/bar"));
  }

  public void testNormPath() {  // PY-10194
    assertEquals("/foo/bar/baz.py", doEvaluate("os.path.normpath(__file__)", "/foo/bar/baz.py"));
  }

  public void testReplace() {
    assertEquals("/foo/Bar/Baz.py", doEvaluate("__file__.replace('b', 'B')", "/foo/bar/baz.py"));
  }

  public void testConstants() {
    myFixture.configureByText(PythonFileType.INSTANCE, "ROOT_PATH = '/foo'\nTEMPLATES_DIR = os.path.join(ROOT_PATH, 'templates')");
    PyFile file = (PyFile) myFixture.getFile();
    final PyTargetExpression expression = file.findTopLevelAttribute("TEMPLATES_DIR");
    final PyExpression value = expression.findAssignedValue();
    final String result = FileUtil.toSystemIndependentName((String) new PyPathEvaluator("").evaluate(value));
    assertEquals(result, "/foo/templates");
  }

  public void testList() {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    final PyExpression expression = generator.createExpressionFromText(LanguageLevel.getLatest(), "['a' + 'b'] + ['c']");
    List<Object> result = (List<Object>) new PyPathEvaluator("").evaluate(expression);
    assertEquals(2, result.size());
    assertEquals("ab", result.get(0));
    assertEquals("c", result.get(1));
  }

  public void testParDir() {
    assertEquals("/foo/subfolder/../bar.py", doEvaluate("os.path.abspath(os.path.join(os.path.join('/foo/subfolder',  os.path.pardir, 'bar.py')))", "/foo/bar.py"));
  }

  // PY-13911
  public void testPathlibRoot() {
    assertEquals("/foo/bar/baz.py", doEvaluate("Path(__file__)",  "/foo/bar/baz.py"));
  }

  // PY-13911
  public void testPathlibParent() {
    assertEquals("/foo", doEvaluate("Path(__file__).resolve().parent.parent",  "/foo/bar/baz.py"));
  }

  // PY-13911
  public void testPathlibAbsolutePath() {
    assertEquals("/foo/bar", doEvaluate("Path('/foo/bar')",  "/irrelevant"));
  }

  // PY-13911
  public void testPathlibJoin() {
    assertEquals("/foo/bar/baz.py", doEvaluate("Path(__file__).resolve() / 'bar' / 'baz.py'",  "/foo"));
  }

  private String doEvaluate(final String text, final String file) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    final PyExpression expression = generator.createExpressionFromText(LanguageLevel.getLatest(), text);
    return FileUtil.toSystemIndependentName((String) new PyPathEvaluator(file).evaluate(expression));
  }
}
