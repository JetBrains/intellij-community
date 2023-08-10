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

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.InvokeTemplateAction;
import com.intellij.codeInsight.template.impl.TemplateContextTypes;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class PyLiveTemplatesContextTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/codeInsight/liveTemplates/context/";
  }

  public void testNotPython() {
    doTest(false, "html");
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

  // PY-52162
  public void testSurroundStringLiteral() {
    doTest(true, "py", PythonTemplateContextType.General.class, PythonTemplateContextType.TopLevel.class);
  }

  // PY-52162
  public void testSurroundTemplateWithStringLiterals() {
    final TemplateManager templateManager = TemplateManager.getInstance(myFixture.getProject());
    final Template template = templateManager.createTemplate("pri", "Python", "print($SELECTION$)$END$");

    TemplateContextType context = TemplateContextTypes.getByClass(PythonTemplateContextType.General.class);
    assertNotNull(context);
    ((TemplateImpl)template).getTemplateContext().setEnabled(context, true);

    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());

    myFixture.configureByText("abc.py", "'test'<caret>");
    myFixture.getEditor().getSelectionModel().setSelection(0, 6);
    new InvokeTemplateAction((TemplateImpl)template, myFixture.getEditor(), myFixture.getProject(), new HashSet<>()).perform();
    myFixture.checkResult("print('test')");
  }

  private void doTest(Class<? extends PythonTemplateContextType> @NotNull ... expectedContextTypes) {
    doTest(false, "py", expectedContextTypes);
  }

  private void doTest(boolean isSurrounding,
                      @NotNull String extension,
                      Class<? extends PythonTemplateContextType> @NotNull ... expectedContextTypes) {
    myFixture.configureByFile(getTestName(true) + "." + extension);

    final List<Class<? extends PythonTemplateContextType>> actualContextTypes =
      calculateEnabledContextTypes(isSurrounding, getRegisteredContextTypes());
    assertSameElements(actualContextTypes, expectedContextTypes);
  }

  @NotNull
  private List<Class<? extends PythonTemplateContextType>> calculateEnabledContextTypes(boolean isSurrounding, @NotNull List<PythonTemplateContextType> registeredContextTypes) {
    TemplateActionContext context = TemplateActionContext.create(myFixture.getFile(), null, myFixture.getCaretOffset(), myFixture.getCaretOffset(), isSurrounding);
    return registeredContextTypes
      .stream()
      .filter(type -> type.isInContext(context))
      .map(PythonTemplateContextType::getClass)
      .sorted(Comparator.comparing(Class::getSimpleName))
      .collect(Collectors.toList());
  }

  @NotNull
  private static List<PythonTemplateContextType> getRegisteredContextTypes() {
    return TemplateContextTypes.getAllContextTypes().stream()
      .filter(PythonTemplateContextType.class::isInstance)
      .map(PythonTemplateContextType.class::cast)
      .collect(Collectors.toList());
  }
}