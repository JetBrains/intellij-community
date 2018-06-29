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
package com.jetbrains.python;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.console.PyConsoleUtil;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
@TestDataPath("$CONTENT_ROOT/../testData/ipython/")
public class PythonConsoleParsingTest extends ParsingTestCase {
  private LanguageLevel myLanguageLevel = LanguageLevel.getDefault();

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public PythonConsoleParsingTest() {
    super("psi", "py", new PythonParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
    PythonDialectsTokenSetProvider.reset();
  }

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  public void testQuestionEnd() {
    PsiFile psiFile = consoleFile("sys?");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testDoubleQuestionEnd() {
    PsiFile psiFile = consoleFile("sys??");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testQuestionStart() {
    PsiFile psiFile = consoleFile("?sys");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testDoubleQuestionStart() {
    PsiFile psiFile = consoleFile("??sys");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testSlashGlobals() {
    PsiFile psiFile = consoleFile("/globals");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testComma() {
    PsiFile psiFile = consoleFile(", call while True");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testSemicolon() {
    PsiFile psiFile = consoleFile("; length str");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }

  public void testCallNoAutomagic() {
    PsiFile psiFile = consoleFile("; length str");
    assertFalse(PsiTreeUtil.hasErrorElements(psiFile));
  }


  public void doTest(LanguageLevel languageLevel) {
    LanguageLevel prev = myLanguageLevel;
    myLanguageLevel = languageLevel;
    try {
      doTest(true);
    }
    finally {
      myLanguageLevel = prev;
    }
  }

  private PsiFile consoleFile(String text) {
    return createPsiFile("Console.py", text);
  }

  @Override
  protected PsiFile createFile(@NotNull String name, @NotNull String text) {
    LightVirtualFile originalFile = new LightVirtualFile(name, myLanguage, text);
    LightVirtualFile virtualFile = new LightVirtualFile(name, myLanguage, text);
    virtualFile.setOriginalFile(originalFile);

    originalFile.setCharset(CharsetToolkit.UTF8_CHARSET);
    originalFile.putUserData(LanguageLevel.KEY, myLanguageLevel);
    PyConsoleUtil.markIPython(originalFile);
    return createFile(virtualFile);
  }
}

