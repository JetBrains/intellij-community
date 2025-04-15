// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tests;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.env.PyEnvTestCase;
import com.jetbrains.env.debug.tasks.PyConsoleTask;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertTrue;

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

  @Test
  public void testCheckForThreadLeaks() {
    runPythonTest(new IPythonTask() {
      @Override
      public void testing() throws Exception {
        exec("x = 42");
        exec("print(x)");
        waitForOutput("42");
      }

      @Override
      public boolean reportThreadLeaks() {
        return true;
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
