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
package com.jetbrains.python.quickFixes;

import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyQuickFixTestCase;
import com.jetbrains.python.inspections.PyTypeCheckerInspection;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author lada
 */
@TestDataPath("$CONTENT_ROOT/../testData/quickFixes/PyMakeFunctionReturnTypeQuickFixTest/")
public class PyMakeFunctionReturnTypeQuickFixTest extends PyQuickFixTestCase {
    public void testOneReturn() {
      doQuickFixTest(PyTypeCheckerInspection.class, PyPsiBundle.message("QFIX.make.function.return.type", "f", "int"), LanguageLevel.PYTHON27);
    }

    public void testPy3OneReturn() {
      doQuickFixTest(PyTypeCheckerInspection.class, PyPsiBundle.message("QFIX.make.function.return.type", "f", "int"), LanguageLevel.PYTHON34);
    }

  // PY-27128
  public void testPy39UnionTypeImports() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doMultiFileTest(PyTypeCheckerInspection.class, PyPsiBundle.message("QFIX.make.function.return.type", "foo", "type[Union[X, Y]]"));
    });
  }

  // PY-27128
  public void testPy39BitwiseOrUnionFromFutureAnnotationsUnionTypeImports() {
    runWithLanguageLevel(LanguageLevel.PYTHON39, () -> {
      doMultiFileTest(PyTypeCheckerInspection.class, PyPsiBundle.message("QFIX.make.function.return.type", "foo", "type[X | Y]"));
    });
  }

  // PY-27128
  public void testBitwiseOrUnionTypeImports() {
    doMultiFileTest(PyTypeCheckerInspection.class, PyPsiBundle.message("QFIX.make.function.return.type", "foo", "type[X | Y]"));
  }

  // PY-27128
  public void testAncestorAndInheritorReturn() {
    doMultiFileTest(PyTypeCheckerInspection.class, PyPsiBundle.message("QFIX.make.function.return.type", "foo", "type[X | Y]"));
  }

  // PY-27128 PY-48466
  public void testLambda() {
    doQuickFixTest(PyTypeCheckerInspection.class,
                   PyPsiBundle.message("QFIX.make.function.return.type", "func", "Callable[[Any], int]"),
                   LanguageLevel.getLatest());
  }

  // PY-20710
  public void testChangeGenerator() {
      doQuickFixTest(PyTypeCheckerInspection.class, 
                     PyPsiBundle.message("QFIX.make.function.return.type", "gen", "Generator[str, bool, int]"),
                     LanguageLevel.getLatest());
  }

  // PY-20710
  public void testMakeGenerator() {
    doQuickFixTest(PyTypeCheckerInspection.class,
                   PyPsiBundle.message("QFIX.make.function.return.type", "gen", "AsyncGenerator[str | float, Any]"),
                   LanguageLevel.getLatest());
  }
}
