package com.jetbrains.env.python;

import com.google.common.base.Function;
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

import java.util.Collection;
import java.util.Collections;
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
        waitForOutput("Type:", "module");
      }
    });
  }

  public void testParsing() throws Exception {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        waitForReady();
        addTextToEditor("sys?");
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            PsiFile psi =
              PsiDocumentManager.getInstance(getProject())
                .getPsiFile(getConsoleView().getConsoleEditor().getDocument());
            Assert.assertThat("No errors expected", getErrors(psi), Matchers.empty());
          }
        });
      }
    });
  }

  @NotNull
  private static Collection<String> getErrors(PsiFile psi) { //TODO: NotNull?
    if (!PsiTreeUtil.hasErrorElements(psi)) {
      return Collections.emptyList();
    }

    return Collections2.transform(PsiTreeUtil.findChildrenOfType(psi, PsiErrorElement.class), new Function<PsiErrorElement, String>() {
      @Override
      public String apply(PsiErrorElement input) {
        return input.getErrorDescription();
      }
    });
  }

  public void testParsingNoIPython() throws Exception {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        waitForReady();
        addTextToEditor("sys?");
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            PsiFile psi =
              PsiDocumentManager.getInstance(getProject()).getPsiFile(getConsoleView().getConsoleEditor().getDocument());
            //TreeUtil.ensureParsed(psi.getNode());
            assertTrue(PsiTreeUtil.hasErrorElements(psi));
          }
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
