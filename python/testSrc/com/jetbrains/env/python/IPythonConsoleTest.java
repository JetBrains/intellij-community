package com.jetbrains.env.python;

import com.google.common.collect.ImmutableSet;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.env.python.console.PyConsoleTask;
import com.jetbrains.env.python.debug.PyEnvTestCase;

import java.util.Set;

/**
 * @author traff
 */
public class IPythonConsoleTest extends PyEnvTestCase {
  public void testQuestion() throws Exception {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        exec("import multiprocessing");
        exec("multiprocessing?");
        waitForOutput("Base Class: <type 'module'>");
      }
    });
  }

  public void testParsing() throws Exception {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        waitForReady();
        addTextToEditor("sys?");
        PsiFile psi =
          PsiDocumentManager.getInstance(getProject()).getPsiFile(getConsoleView().getLanguageConsole().getConsoleEditor().getDocument());
        assertFalse(PsiTreeUtil.hasErrorElements(psi));
      }
    });
  }

  public void testParsingNoIPython() throws Exception {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        waitForReady();
        addTextToEditor("sys?");
        PsiFile psi =
          PsiDocumentManager.getInstance(getProject()).getPsiFile(getConsoleView().getLanguageConsole().getConsoleEditor().getDocument());
        //TreeUtil.ensureParsed(psi.getNode());
        assertTrue(PsiTreeUtil.hasErrorElements(psi));
      }

      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-ipython");
      }
    });
  }

  private static class IPythonTask extends PyConsoleTask {
    @Override
    public Set<String> getTags() {
      return ImmutableSet.of("ipython");
    }
  }
}
