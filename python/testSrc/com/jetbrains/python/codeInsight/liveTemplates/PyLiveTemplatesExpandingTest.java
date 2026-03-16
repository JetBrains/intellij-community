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
import com.intellij.codeInsight.template.TemplateManager;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

public class PyLiveTemplatesExpandingTest extends PyTestCase {

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/codeInsight/liveTemplates/expanding/";
  }

  public void testIterableVariableFromImplicitImports() {
    doMultiFileTest();
  }

  private void doMultiFileTest() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    myFixture.configureByFile("a.py");
    myFixture.type("\t");
    myFixture.checkResultByFile(getTestName(false) + "/a_after.py");
  }

  public void testTrimCommentStart() {
    myFixture.configureByText("a.py", "<caret>");

    TemplateManager manager = TemplateManager.getInstance(myFixture.getProject());
    Template template = manager.createTemplate("empty", "user", "$S$ comment $E$");
    template.addVariable("S", "commentStart()", "", false);
    template.addVariable("E", "commentEnd()", "", false);

    manager.startTemplate(myFixture.getEditor(), template);

    myFixture.checkResult("# comment ");
  }

  // PY-43889
  public void testMainInDef() {
    doMultiFileTest();
  }

  // PY-43889
  public void testMainInImport() {
    doMultiFileTest();
  }

  // PY-43889
  public void testMainInMain() {
    doMultiFileTest();
  }

  // PY-43889
  public void testMainTopLevel() {
    doMultiFileTest();
  }

  // PY-26060
  public void testSuperTemplateWithPython2() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      doMultiFileTest();
    });
  }

  // PY-26060
  public void testSuperTemplateWithPython3() {
    runWithLanguageLevel(LanguageLevel.getLatest(), () -> {
      doMultiFileTest();
    });
  }

  // PY-36230
  public void testPropertyDecoratorNoDuplicate() {
    myFixture.configureByText("a.py", "class MyClass:\n    @prop<caret>");
    myFixture.type("\t");
    myFixture.checkResult("class MyClass:\n    @property\n    def <caret>(self):\n        return ");
  }

  // PY-36230
  public void testPropertyDecoratorNoAtSymbol() {
    myFixture.configureByText("a.py", "class MyClass:\n    prop<caret>");
    myFixture.type("\t");
    myFixture.checkResult("class MyClass:\n    @property\n    def <caret>(self):\n        return ");
  }

  // PY-41231
  public void testIterableVariableWithTypeAnnotation() {
    doMultiFileTest();
  }
}
