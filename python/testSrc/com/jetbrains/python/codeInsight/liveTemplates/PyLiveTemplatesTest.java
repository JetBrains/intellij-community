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
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PyLiveTemplatesTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/codeInsight/liveTemplates/context/";
  }

  public void testNotPython() {
    doTest(Collections.emptyList(), "html");
  }

  // PY-12212
  public void testAfterDot() {
    doTest(Collections.emptyList());
  }

  // PY-13076
  public void testInComment() {
    doTest(Collections.emptyList());
  }

  // PY-12349
  public void testInStringLiteral() {
    doTest(Collections.emptyList());
  }

  // PY-12395
  public void testInParameterList() {
    doTest(Collections.emptyList());
  }

  public void testGeneral() {
    doTest(
      Collections.singletonList(PythonTemplateContextType.General.class)
    );
  }

  // PY-12396
  public void testClass() {
    doTest(
      Arrays.asList(PythonTemplateContextType.Class.class, PythonTemplateContextType.General.class)
    );
  }

  private void doTest(@NotNull List<Class<? extends PythonTemplateContextType>> expectedContextTypes) {
    doTest(expectedContextTypes, "py");
  }

  private void doTest(@NotNull List<Class<? extends PythonTemplateContextType>> expectedContextTypes, @NotNull String extension) {
    myFixture.configureByFile(getTestName(true) + "." + extension);

    UsefulTestCase.assertSameElements(
      calculateEnabledContextTypes(getRegisteredContextTypes()),
      expectedContextTypes
    );
  }

  @NotNull
  private List<Class<? extends PythonTemplateContextType>> calculateEnabledContextTypes(@NotNull List<PythonTemplateContextType> registeredContextTypes) {
    //noinspection Convert2MethodRef
    return registeredContextTypes
      .stream()
      .filter(type -> type.isInContext(myFixture.getFile(), myFixture.getCaretOffset()))
      .map(type -> type.getClass())
      .sorted((o1, o2) -> o1.getSimpleName().compareTo(o2.getSimpleName()))
      .collect(Collectors.toList());
  }

  @NotNull
  private static List<PythonTemplateContextType> getRegisteredContextTypes() {
    return Arrays
      .stream(TemplateContextType.EP_NAME.getExtensions())
      .filter(type -> type instanceof PythonTemplateContextType)
      .map(type -> (PythonTemplateContextType)type)
      .collect(Collectors.toList());
  }
}