// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.idea.TestFor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.jetbrains.python.fixture.PythonCommonCodeInsightTestFixture;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.fixtures.PythonPlatformCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.opentest4j.AssertionFailedError;

@Subsystems.Formatter
@Layers.Functional
public class PyFormatterTest extends PythonCommonFormatterTest {
  @Override
  protected PythonCommonCodeInsightTestFixture getFixture() {
    return new PythonPlatformCodeInsightTestFixture(LANGUAGE_LEVEL);
  }

  @TestFor(issues = "PY-54885")
  public void testSingleSpaceAfterAwait() {
    PyTestCase.fixme("PY-54885 the formatter keeps the extra space after await", AssertionFailedError.class, "await  g()",
                     () -> doTestText("""
                                        async def g(): ...


                                        async def h():
                                            await  g()
                                        """, """
                                        async def g(): ...


                                        async def h():
                                            await g()
                                        """));
  }

  @TestFor(issues = "PY-92827")
  public void testNoSpaceBeforeTypeParameterListInTypeAliasStatement() {
    PyTestCase.fixme("PY-92827 the formatter keeps the space before the type parameter list", AssertionFailedError.class,
                     "type Alias [T]",
                     () -> doTestText("type Alias [T] = list[T]\n", "type Alias[T] = list[T]\n"));
  }

  /** Reformats {@code before} and compares the text with {@code after}. The comparison ignores the line breaks at the end. */
  private void doTestText(@NotNull String before, @NotNull String after) {
    myFixture.configureByText(PythonFileType.INSTANCE, before);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> {
      CodeStyleManager.getInstance(myFixture.getProject()).reformat(myFixture.getFile());
    });
    Assertions.assertEquals(after.stripTrailing(), myFixture.getFile().getText().stripTrailing());
  }
}
