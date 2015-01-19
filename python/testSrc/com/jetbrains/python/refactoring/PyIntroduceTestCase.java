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
package com.jetbrains.python.refactoring;

import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;
import com.jetbrains.python.refactoring.introduce.IntroduceOperation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author yole
 */
public abstract class PyIntroduceTestCase extends PyTestCase {
  protected void doTestSuggestions(Class<? extends PyExpression> parentClass, String... expectedNames) {
    final Collection<String> names = buildSuggestions(parentClass);
    for (String expectedName : expectedNames) {
      assertTrue(StringUtil.join(names, ", "), names.contains(expectedName));
    }
  }

  protected Collection<String> buildSuggestions(Class<? extends PyExpression> parentClass) {
    myFixture.configureByFile(getTestName(true) + ".py");
    final IntroduceHandler handler = createHandler();
    final PyExpression expr = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getCaretOffset()), parentClass);
    assertNotNull(expr);
    return handler.getSuggestedNames(expr);
  }

  protected abstract IntroduceHandler createHandler();

  protected void doTest() {
    doTest(null);
  }

  protected void doTest(@Nullable Consumer<IntroduceOperation> customization) {
    myFixture.configureByFile(getTestName(true) + ".py");
    boolean inplaceEnabled = myFixture.getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(false);
      IntroduceHandler handler = createHandler();
      final IntroduceOperation operation = new IntroduceOperation(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "a");
      operation.setReplaceAll(true);
      if (customization != null) {
        customization.consume(operation);
      }
      handler.performAction(operation);
      myFixture.checkResultByFile(getTestName(true) + ".after.py");
    }
    finally {
      myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(inplaceEnabled);
    }
  }

  protected void doTestInplace(@Nullable Consumer<IntroduceOperation> customization) {
    String name = getTestName(true);
    myFixture.configureByFile(name + ".py");
    final boolean enabled = myFixture.getEditor().getSettings().isVariableInplaceRenameEnabled();
    try {
      TemplateManagerImpl.setTemplateTesting(myFixture.getProject(), getTestRootDisposable());
      myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(true);

      IntroduceHandler handler = createHandler();
      final IntroduceOperation introduceOperation = new IntroduceOperation(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), "a");
      introduceOperation.setReplaceAll(true);
      if (customization != null) {
        customization.consume(introduceOperation);
      }
      handler.performAction(introduceOperation);

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), new Runnable() {
        @Override
        public void run() {
          TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
          assert state != null;
          state.gotoEnd(false);
        }
      });

      myFixture.checkResultByFile(name + ".after.py", true);
    }
    finally {
      myFixture.getEditor().getSettings().setVariableInplaceRenameEnabled(enabled);
    }
  }
}
