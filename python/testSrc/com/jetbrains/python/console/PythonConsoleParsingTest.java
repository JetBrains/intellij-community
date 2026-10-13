// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.allure.Layers;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.jetbrains.python.PythonParserDefinition;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.ParsingTestFixtureKt.parsingFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestDataPath("$CONTENT_ROOT/../testData/ipython/")
@Subsystems.PythonConsole
@Layers.Functional
@TestApplication
public class PythonConsoleParsingTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  private final TestFixture<ParsingTestFixture> parsingFixture =
    parsingFixture(projectFixture, PythonTestUtil.getTestDataPath() + "/psi", "py", false, new PythonParserDefinition());

  private LanguageLevel myLanguageLevel = LanguageLevel.getDefault();

  @Test
  public void testQuestionEnd() {
    PsiFile psiFile = consoleFile("sys?");
    assertFalse(ReadAction.computeBlocking(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @Test
  public void testDoubleQuestionEnd() {
    PsiFile psiFile = consoleFile("sys??");
    assertFalse(ReadAction.computeBlocking(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @Test
  public void testQuestionStart() {
    PsiFile psiFile = consoleFile("?sys");
    assertFalse(ReadAction.computeBlocking(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @Test
  public void testDoubleQuestionStart() {
    PsiFile psiFile = consoleFile("??sys");
    assertFalse(ReadAction.computeBlocking(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @Test
  public void testSlashGlobals() {
    PsiFile psiFile = consoleFile("/globals");
    assertFalse(ReadAction.computeBlocking(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @Test
  public void testComma() {
    PsiFile psiFile = consoleFile(", call while True");
    assertFalse(ReadAction.computeBlocking(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @Test
  public void testSemicolon() {
    PsiFile psiFile = consoleFile("; length str");
    assertFalse(ReadAction.computeBlocking(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @Test
  public void testCallNoAutomagic() {
    PsiFile psiFile = consoleFile("; length str");
    assertFalse(ReadAction.computeBlocking(() -> PsiTreeUtil.hasErrorElements(psiFile)));
  }

  @Test
  public void testConsoleSingleStringLiteral() {
    PsiFile psiFile = consoleFile("\"foo\"");
    parsingFixture.get().checkResult(parsingFixture.get().getTestName(), psiFile);
  }

  @Test
  public void testConsoleSingleStringLiteralTripleQuoted() {
    PsiFile psiFile = consoleFile("\"\"\"foo\"\"\"");
    parsingFixture.get().checkResult(parsingFixture.get().getTestName(), psiFile);
  }

  @Test
  public void testConsoleSingleBytesLiteral() {
    PsiFile psiFile = consoleFile("b\"foo\"");
    parsingFixture.get().checkResult(parsingFixture.get().getTestName(), psiFile);
  }

  @Test
  public void testConsoleSingleBytesLiteralTripleQuoted() {
    PsiFile psiFile = consoleFile("b\"\"\"foo\"\"\"");
    parsingFixture.get().checkResult(parsingFixture.get().getTestName(), psiFile);
  }

  @Test
  public void testConsoleSingleStringLiteralNewLineBefore() {
    PsiFile psiFile = consoleFile("\n\"foo\"");
    parsingFixture.get().checkResult(parsingFixture.get().getTestName(), psiFile);
  }

  @Test
  public void testConsoleSingleStringLiteralNewLineAfter() {
    PsiFile psiFile = consoleFile("\"foo\"\n");
    parsingFixture.get().checkResult(parsingFixture.get().getTestName(), psiFile);
  }

  @Test
  public void testConsoleSingleStringLiteralWhitespaceAfter() {
    PsiFile psiFile = consoleFile("\"foo\"    ");
    parsingFixture.get().checkResult(parsingFixture.get().getTestName(), psiFile);
  }

  public void doTest(LanguageLevel languageLevel) {
    LanguageLevel prev = myLanguageLevel;
    myLanguageLevel = languageLevel;
    try {
      doTest();
    }
    finally {
      myLanguageLevel = prev;
    }
  }

  private void doTest() {
    ParsingTestFixture fixture = parsingFixture.get();
    String name = fixture.getTestName();
    String text = fixture.loadFile(name + "." + fixture.getFileExt());
    PsiFile file = fixture.checkParsedFile(createFile(name + "." + fixture.getFileExt(), text), text);
    fixture.checkResult(name, file);
  }

  private PsiFile consoleFile(String text) {
    return createFile("Console." + parsingFixture.get().getFileExt(), text);
  }

  private PsiFile createFile(@NotNull String name, @NotNull String text) {
    Language language = parsingFixture.get().getLanguage();
    LightVirtualFile originalFile = new LightVirtualFile(name, language, text);
    LightVirtualFile virtualFile = new LightVirtualFile(name, language, text);
    virtualFile.setOriginalFile(originalFile);

    originalFile.setCharset(StandardCharsets.UTF_8);
    PythonLanguageLevelPusher.specifyFileLanguageLevel(originalFile, myLanguageLevel);
    PyConsoleUtil.markIPython(originalFile);
    return parsingFixture.get().createFile(virtualFile);
  }
}
