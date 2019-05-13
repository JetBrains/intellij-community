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
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author vlan
 */
public class PyStatementEffectInspectionTest extends PyInspectionTestCase {
  public void testBasic() {
    doTest();
  }

  public void testAwait() {
    doTest(LanguageLevel.PYTHON35);
  }

  public void testComparison() {
    doTest();
  }

  // PY-23057
  public void testFunctionWithEllipsis() {
    doTest(LanguageLevel.PYTHON35);
  }

  private void doTest(@NotNull LanguageLevel level) {
    runWithLanguageLevel(level, this::doTest);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyStatementEffectInspection.class;
  }
}
