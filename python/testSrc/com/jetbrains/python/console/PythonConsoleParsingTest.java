// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.mock.MockApplication;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.python.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

@TestDataPath("$CONTENT_ROOT/../testData/ipython/")
public class PythonConsoleParsingTest extends ParsingTestCase {
  private LanguageLevel myLanguageLevel = LanguageLevel.getDefault();

  private Disposable myServiceDisposable;


  public PythonConsoleParsingTest() {
    super("psi", "py", new PythonParserDefinition());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());

    if (PythonRuntimeService.getInstance() == null) {
      myServiceDisposable = Disposer.newDisposable();
      ((MockApplication)ApplicationManager.getApplication()).registerService(PythonRuntimeService.class, new PythonRuntimeServiceImpl(), myServiceDisposable);
    }
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

    originalFile.setCharset(StandardCharsets.UTF_8);
    PythonLanguageLevelPusher.specifyFileLanguageLevel(originalFile, myLanguageLevel);
    PyConsoleUtil.markIPython(originalFile);
    return createFile(virtualFile);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myServiceDisposable != null) {
        Disposer.dispose(myServiceDisposable);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}

