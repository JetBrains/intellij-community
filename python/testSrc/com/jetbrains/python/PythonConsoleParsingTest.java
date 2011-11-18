package com.jetbrains.python;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.TestDataPath;
import com.jetbrains.cython.CythonTokenSetContributor;
import com.jetbrains.python.console.PyConsoleUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author traff
 */
@TestDataPath("$CONTENT_ROOT/../testData/ipython/")
public class PythonConsoleParsingTest extends ParsingTestCase {
  private LanguageLevel myLanguageLevel = LanguageLevel.getDefault();

  public PythonConsoleParsingTest() {
    super("psi", "py", new PythonParserDefinition());
    PyTestCase.initPlatformPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerExtension(PythonDialectsTokenSetContributor.EP_NAME, new PythonTokenSetContributor());
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
  protected PsiFile createFile(String name, String text) {
    LightVirtualFile originalFile = new LightVirtualFile(name, myLanguage, text);
    LightVirtualFile virtualFile = new LightVirtualFile(name, myLanguage, text);
    virtualFile.setOriginalFile(originalFile);

    originalFile.setCharset(CharsetToolkit.UTF8_CHARSET);
    originalFile.putUserData(LanguageLevel.KEY, myLanguageLevel);
    PyConsoleUtil.markIPython(originalFile);
    return createFile(virtualFile);
  }
}

