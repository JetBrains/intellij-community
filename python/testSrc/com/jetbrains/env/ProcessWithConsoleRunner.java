/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.env;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine that runs process and gives access to console.
 * It has a lot of inheritors, and you should instantiate one and return from
 * {@link PyProcessWithConsoleTestTask#createProcessRunner()}
 *
 * @author Ilya.Kazakevich
 */
public abstract class ProcessWithConsoleRunner implements Disposable {
  /**
   * Main process console
   */
  protected volatile ConsoleViewImpl myConsole;

  /**
   * Runs process setting appropriate console.
   * This method should do the following: run process, attach provided listener, set {@link #myConsole}
   *
   * @param sdkPath         path to SDK to be uysed
   * @param project         project
   * @param processListener listener passed from task. Runner <strong>must</strong> attach it to process (otherwise task will not have
   * @param tempWorkingPath path to {@link CodeInsightTestFixture#getTempDirFixture()}. Will be used as working dir.
   */
  abstract void runProcess(@NotNull String sdkPath,
                           @NotNull Project project,
                           @NotNull ProcessListener processListener,
                           @NotNull String tempWorkingPath)
    throws ExecutionException;

  /**
   * @return process console, may be used in tests
   */
  @NotNull
  public final ConsoleViewImpl getConsole() {
    return myConsole;
  }

  /**
   * Gets highlighted information from test console. Some parts of output (like file links) may be highlighted, and you need to check them.
   *
   * @return pair of [[ranges], [texts]] where range is [from,to] in doc. for each region, and "text" is text extracted from this region.
   * For example assume that in document "spam eggs ham" words "ham" and "spam" are highlighted.
   * You should have 2 ranges (0, 4) and (10, 13) and 2 strings (spam and ham)
   */
  @NotNull
  public Pair<List<Pair<Integer, Integer>>, List<String>> getHighlightedStringsInConsole() {
    final List<String> resultStrings = new ArrayList<>();
    final List<Pair<Integer, Integer>> resultRanges = new ArrayList<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      myConsole.flushDeferredText();
      final Editor editor = myConsole.getEditor();
      for (final RangeHighlighter highlighter : editor.getMarkupModel().getAllHighlighters()) {
        if (highlighter instanceof RangeHighlighterEx) {
          final int start = ((RangeHighlighterEx)highlighter).getAffectedAreaStartOffset();
          final int end = ((RangeHighlighterEx)highlighter).getAffectedAreaEndOffset();
          resultRanges.add(Pair.create(start, end));
          resultStrings.add(editor.getDocument().getText().substring(start, end));
        }
      }
    }, ModalityState.NON_MODAL);
    return Pair.create(resultRanges, resultStrings);
  }

  /**
   * @return Short-cut to get text from console
   */
  @NotNull
  public String getAllConsoleText() {
    return myConsole.getEditor().getDocument().getText();
  }

  /**
   * Use this method to dispose any resources and <strong>always</strong> call parent
   */
  @Override
  public void dispose() {
    Disposer.dispose(myConsole);
  }

  /**
   * Called after each run. If returns true, runner will be launched again.
   */
  protected boolean shouldRunAgain() {
    return false;
  }
}
