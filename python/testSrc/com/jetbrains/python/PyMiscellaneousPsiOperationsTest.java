/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyQualifiedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class PyMiscellaneousPsiOperationsTest extends PyTestCase {

  public void testQualifiedNameExtraction() {
    checkAsQualifiedNameResult("foo.bar.baz", QualifiedName.fromDottedString("foo.bar.baz"));
    checkAsQualifiedNameResult("foo().bar.baz", null);
    checkAsQualifiedNameResult("foo.bar[0]", QualifiedName.fromDottedString("foo.bar.__getitem__"));
    checkAsQualifiedNameResult("foo[0].bar", null);
    checkAsQualifiedNameResult("foo[0][0]", null);
    checkAsQualifiedNameResult("-foo.bar", QualifiedName.fromDottedString("foo.bar.__neg__"));
    checkAsQualifiedNameResult("foo + bar", QualifiedName.fromDottedString("foo.__add__"));
    checkAsQualifiedNameResult("foo + bar + baz", null);
    checkAsQualifiedNameResult("foo.bar + baz", QualifiedName.fromDottedString("foo.bar.__add__"));
    checkAsQualifiedNameResult("-foo + bar", null);
  }

  private void checkAsQualifiedNameResult(@NotNull String expression, @Nullable QualifiedName expectedQualifiedName) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    final PyQualifiedExpression expr = (PyQualifiedExpression)generator.createExpressionFromText(LanguageLevel.PYTHON27, expression);
    assertEquals(expectedQualifiedName, expr.asQualifiedName());
  }
}
