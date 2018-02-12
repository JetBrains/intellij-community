package com.jetbrains.env.python;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.python.console.PyConsoleTask;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * @author traff
 */
public class IPythonConsoleTest extends PyEnvTestCase {
  @Test
  public void testQuestion() {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        exec("import multiprocessing");
        exec("multiprocessing?");
        waitForOutput("Type:", "module");
      }
    });
  }

  @Test
  public void testParsing() {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        waitForReady();
        addTextToEditor("sys?");
        ApplicationManager.getApplication().runReadAction(() -> {
          PsiFile psi =
            PsiDocumentManager.getInstance(getProject())
              .getPsiFile(getConsoleView().getConsoleEditor().getDocument());
          Assert.assertThat("No errors expected", getErrors(psi), Matchers.empty());
        });
      }
    });
  }

  @NotNull
  private static Collection<String> getErrors(PsiFile psi) { //TODO: NotNull?
    if (!PsiTreeUtil.hasErrorElements(psi)) {
      return Collections.emptyList();
    }

    return Collections2.transform(PsiTreeUtil.findChildrenOfType(psi, PsiErrorElement.class), input -> input.getErrorDescription());
  }

  @Test
  public void testParsingNoIPython() {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        waitForReady();
        addTextToEditor("sys?");
        ApplicationManager.getApplication().runReadAction(() -> {
          PsiFile psi =
            PsiDocumentManager.getInstance(getProject()).getPsiFile(getConsoleView().getConsoleEditor().getDocument());
          //TreeUtil.ensureParsed(psi.getNode());
          assertTrue(PsiTreeUtil.hasErrorElements(psi));
        });
      }

      @NotNull
      @Override
      public Set<String> getTags() {
        return ImmutableSet.of("-ipython");
      }
    });
  }

  private static class IPythonTask extends PyConsoleTask {
    @NotNull
    @Override
    public Set<String> getTags() {
      return ImmutableSet.of("ipython");
    }
  }
}
