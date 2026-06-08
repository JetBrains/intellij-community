// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPathEvaluator;
import org.jetbrains.annotations.NotNull;

import java.util.List;


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
    final String result = ((String) new PyPathEvaluator("").evaluate(value)).replace('\\', '/');
    assertEquals("/foo/templates", result);
  }

  public void testList() {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    final PyExpression expression = generator.createExpressionFromText(LanguageLevel.getLatest(), "['a' + 'b'] + ['c']");
    List<?> result = (List<?>) new PyPathEvaluator("").evaluate(expression);
    assertEquals(List.of("ab", "c"), result);
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

  // PY-89877
  public void testOsPathJoinAtRoot() {
    assertEquals("/foo", doEvaluate("os.path.join('/', 'foo')", "/irrelevant"));
  }

  // PY-89877
  public void testPathlibSlashAtRoot() {
    assertEquals("/foo", doEvaluate("Path('/') / 'foo'", "/irrelevant"));
  }

  private String doEvaluate(final @NotNull String text, final @NotNull String file) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    final PyExpression expression = generator.createExpressionFromText(LanguageLevel.getLatest(), text);
    return ((String) new PyPathEvaluator(file).evaluate(expression)).replace('\\', '/');
  }
}
