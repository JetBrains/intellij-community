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
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.template.TemplateContextType;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PyLiveTemplatesContextTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/codeInsight/liveTemplates/context/";
  }

  public void testNotPython() {
    doTest("html");
  }

  // PY-12212
  public void testAfterDot() {
    doTest();
  }

  // PY-13076
  public void testInComment() {
    doTest();
  }

  // PY-12349
  public void testInStringLiteral() {
    doTest();
  }

  // PY-12395
  public void testInParameterList() {
    doTest();
  }

  public void testGeneral() {
    doTest(PythonTemplateContextType.General.class);
  }

  // PY-12396
  public void testClass() {
    doTest(PythonTemplateContextType.Class.class, PythonTemplateContextType.General.class);
  }

  private void doTest(@NotNull Class<? extends PythonTemplateContextType>... expectedContextTypes) {
    doTest("py", expectedContextTypes);
  }

  private void doTest(@NotNull String extension, @NotNull Class<? extends PythonTemplateContextType>... expectedContextTypes) {
    myFixture.configureByFile(getTestName(true) + "." + extension);

    final List<Class<? extends PythonTemplateContextType>> actualContextTypes = calculateEnabledContextTypes(getRegisteredContextTypes());
    assertSameElements(actualContextTypes, expectedContextTypes);
  }

  @NotNull
  private List<Class<? extends PythonTemplateContextType>> calculateEnabledContextTypes(@NotNull List<PythonTemplateContextType> registeredContextTypes) {
    return registeredContextTypes
      .stream()
      .filter(type -> type.isInContext(myFixture.getFile(), myFixture.getCaretOffset()))
      .map(PythonTemplateContextType::getClass)
      .sorted(Comparator.comparing(Class::getSimpleName))
      .collect(Collectors.toList());
  }

  @NotNull
  private static List<PythonTemplateContextType> getRegisteredContextTypes() {
    return Arrays
      .stream(TemplateContextType.EP_NAME.getExtensions())
      .filter(PythonTemplateContextType.class::isInstance)
      .map(PythonTemplateContextType.class::cast)
      .collect(Collectors.toList());
  }
}