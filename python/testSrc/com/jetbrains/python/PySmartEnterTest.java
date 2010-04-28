package com.jetbrains.python;

import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor;
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessors;
import com.intellij.lang.Language;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   22.04.2010
 * Time:   17:00:16
 */
public class PySmartEnterTest extends PyLightFixtureTestCase {
  protected static List<SmartEnterProcessor> getSmartProcessors(Language language) {
    return SmartEnterProcessors.INSTANCE.forKey(language);
  }

  public void doTest() throws Exception {
    myFixture.configureByFile("codeInsight/smartEnter/" + getTestName(true) + ".py");
    final List<SmartEnterProcessor> processors = getSmartProcessors(PythonLanguage.getInstance());
    new WriteCommandAction(myFixture.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final Editor editor = myFixture.getEditor();
        for (SmartEnterProcessor processor : processors) {
          processor.process(myFixture.getProject(), editor, myFixture.getFile());
        }
      }
    }.execute();
    myFixture.checkResultByFile("codeInsight/smartEnter/" + getTestName(true) + "_after.py", true);
  }

  public void testIf() throws Exception {
    doTest();
  }

  public void testWhile() throws Exception {
    doTest();
  }

  public void testElif() throws Exception {
    doTest();
  }

  public void testForFirst() throws Exception {
    doTest();
  }

  public void testForSecond() throws Exception {
    doTest();
  }

  public void testTry() throws Exception {
    doTest();
  }

  public void testString() throws Exception {
    doTest();
  }

  public void testDocstring() throws Exception {
    doTest();
  }

  public void testDict() throws Exception {
    doTest();
  }

  public void testParenthesized() throws Exception {
    doTest();
  }

  public void testArgumentsFirst() throws Exception {
    doTest();
  }

  public void testArgumentsSecond() throws Exception {
    doTest();
  }

  public void testFunc() throws Exception {
    doTest();
  }

  public void testClass() throws Exception {
    doTest();
  }

  public void testComment() throws Exception {
    doTest();
  }

  public void testPy891() throws Exception {
    doTest();
  }
}
