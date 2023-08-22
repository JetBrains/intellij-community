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

import com.intellij.psi.PsiComment;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

  public void testAddingNameInFromImportStatement() {
    checkAddingNameInFromImport("from mod import foo", "bar", "foo", true, "from mod import bar, foo");
    checkAddingNameInFromImport("from mod import foo", "bar", "foo", false, "from mod import foo, bar");
    checkAddingNameInFromImport("from mod import foo", "bar", null, false, "from mod import bar, foo");
    checkAddingNameInFromImport("from mod import foo", "bar", null, true, "from mod import foo, bar");
    checkAddingNameInFromImport("from mod import (foo) # comment", "bar", "foo", true, "from mod import (bar, foo) # comment");
    checkAddingNameInFromImport("from mod import (foo) # comment", "bar", "foo", false, "from mod import (foo, bar) # comment");
    checkAddingNameInFromImport("from mod import (foo,)", "bar", "foo", false, "from mod import (foo, bar,)");
    checkAddingNameInFromImport("from mod import (foo # comment\n" +
                                "                )",
                                "bar", "foo", false,
                                "from mod import (foo, bar # comment\n" +
                                "                )");
    checkAddingNameInFromImport("from mod import ", "bar", null, false, "from mod import bar");
    checkAddingNameInFromImport("from mod import ", "bar", null, true, "from mod import bar");
    checkAddingNameInFromImport("from mod import (", "bar", null, true, "from mod import (bar");
    // TODO change where the placeholder empty import element is added in such cases
    //checkAddingNameInFromImport("from mod import ( # comment", "bar", null, true, "from mod import (bar # comment");
    checkAddingNameInFromImport("from mod import ()", "bar", null, true, "from mod import (bar)");
  }

  public void testPrecedingImportBlock() {
    List<List<PsiComment>> blocks;
    blocks = getPrecedingImportBlocks("""
                                        # comment

                                        # comment
                                        # comment
                                        def func():\s
                                            pass""");
    assertSize(2, blocks);
    assertSize(1, blocks.get(0));
    assertSize(2, blocks.get(1));

    blocks = getPrecedingImportBlocks("""
                                        # comment

                                        # comment
                                        # comment

                                        def func():\s
                                            pass""");
    assertSize(3, blocks);
    assertSize(1, blocks.get(0));
    assertSize(2, blocks.get(1));
    assertSize(0, blocks.get(2));

    blocks = getPrecedingImportBlocks("def func(): \n" +
                                      "    pass");
    assertSize(0, blocks);

    blocks = getPrecedingImportBlocks("""
                                        # comment
                                        x = 42

                                        def func():\s
                                            pass""");
    assertSize(0, blocks);

    blocks = getPrecedingImportBlocks("""
                                        # comment
                                        x = 42

                                        # comment
                                        def func():\s
                                            pass""");
    assertSize(1, blocks);
    assertSize(1, blocks.get(0));
  }

  private List<List<PsiComment>> getPrecedingImportBlocks(@NotNull String text) {
    PyFile file = assertInstanceOf(myFixture.configureByText("a.py", text), PyFile.class);
    PyFunction func = file.findTopLevelFunction("func");
    return PyPsiUtils.getPrecedingCommentBlocks(func);
  }

  private void checkAddingNameInFromImport(@NotNull String fromImport,
                                           @NotNull String newName,
                                           @Nullable String anchorName,
                                           boolean before,
                                           @NotNull String result) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    final LanguageLevel languageLevel = LanguageLevel.PYTHON27;
    final PyFromImportStatement fromImportElem = generator.createFromText(languageLevel, PyFromImportStatement.class, fromImport, new int[]{0});
    final PyImportElement anchor;
    if (anchorName != null) {
      anchor = ContainerUtil.find(fromImportElem.getImportElements(),
                                  importElem -> importElem.getImportedQName().toString().equals(anchorName));
      assertNotNull(anchor);
    }
    else {
      anchor = null;
    }
    final PyImportElement newNameElem = generator.createImportElement(languageLevel, newName, null);
    if (before) {
      fromImportElem.addBefore(newNameElem, anchor);
    }
    else {
      fromImportElem.addAfter(newNameElem, anchor);
    }
    assertEquals(result, fromImportElem.getText());
  }

  private void checkAsQualifiedNameResult(@NotNull String expression, @Nullable QualifiedName expectedQualifiedName) {
    final PyElementGenerator generator = PyElementGenerator.getInstance(myFixture.getProject());
    final PyQualifiedExpression expr = (PyQualifiedExpression)generator.createExpressionFromText(LanguageLevel.PYTHON27, expression);
    assertEquals(expectedQualifiedName, expr.asQualifiedName());
  }
}
