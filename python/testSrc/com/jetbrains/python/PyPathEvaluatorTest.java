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

import com.intellij.idea.RecordExecution;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPathEvaluator;

import java.util.List;

/**
 * @author yole
 */
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
    final PyExpression expression = PyElementGenerator.getInstance(myFixture.getProject()).createExpressionFromText("['a' + 'b'] + ['c']");
    List<Object> result = (List<Object>) new PyPathEvaluator("").evaluate(expression);
    assertEquals(2, result.size());
    assertEquals("ab", result.get(0));
    assertEquals("c", result.get(1));
  }

  public void testParDir() {
    assertEquals("/foo/subfolder/../bar.py", doEvaluate("os.path.abspath(os.path.join(os.path.join('/foo/subfolder',  os.path.pardir, 'bar.py')))", "/foo/bar.py"));
  }

  private String doEvaluate(final String text, final String file) {
    final PyExpression expression = PyElementGenerator.getInstance(myFixture.getProject()).createExpressionFromText(text);
    return FileUtil.toSystemIndependentName((String) new PyPathEvaluator(file).evaluate(expression));
  }
}
