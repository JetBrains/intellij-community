/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBSplitter;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.frame.XStandaloneVariablesView;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.completion.PythonConsoleAutopopupBlockingHandler;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import com.jetbrains.python.debugger.PyDebuggerEditorsProvider;
import com.jetbrains.python.debugger.PyStackFrame;
import com.jetbrains.python.debugger.PyStackFrameInfo;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author traff
 */
public class PythonConsoleView extends LanguageConsoleImpl implements ObservableConsoleView, PyCodeExecutor {

  private static final Logger LOG = Logger.getInstance(PythonConsoleView.class);

  private PydevConsoleExecuteActionHandler myExecuteActionHandler;
  private PyConsoleSourceHighlighter mySourceHighlighter;
  private boolean myIsIPythonOutput;
  private final PyHighlighter myPyHighlighter;
  private final EditorColorsScheme myScheme;
  private boolean myHyperlink;

  private XStandaloneVariablesView mySplitView;

  public PythonConsoleView(final Project project, final String title, final Sdk sdk) {
    super(project, title, PythonLanguage.getInstance());

    getVirtualFile().putUserData(LanguageLevel.KEY, PythonSdkType.getLanguageLevelForSdk(sdk));
    // Mark editor as console one, to prevent autopopup completion
    getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());

    setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
    setUpdateFoldingsEnabled(false);
    //noinspection ConstantConditions
    myPyHighlighter = new PyHighlighter(
      sdk != null && sdk.getVersionString() != null ? LanguageLevel.fromPythonVersion(sdk.getVersionString()) : LanguageLevel.getDefault());
    myScheme = getConsoleEditor().getColorsScheme();
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    getFile().putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
  }

  public void setExecutionHandler(@NotNull PydevConsoleExecuteActionHandler consoleExecuteActionHandler) {
    myExecuteActionHandler = consoleExecuteActionHandler;
  }

  public void inputRequested() {
    if (myExecuteActionHandler != null) {
      final ConsoleCommunication consoleCommunication = myExecuteActionHandler.getConsoleCommunication();
      if (consoleCommunication instanceof PythonDebugConsoleCommunication) {
        ((PythonDebugConsoleCommunication)consoleCommunication).waitingForInput = true;
        myExecuteActionHandler.inputRequested();
        myExecuteActionHandler.setEnabled(true);
        myExecuteActionHandler.clearInputBuffer();
      }
    }
  }

  @Override
  public void requestFocus() {
    IdeFocusManager.findInstance().requestFocus(getConsoleEditor().getContentComponent(), true);
  }

  @Override
  public void executeCode(final @NotNull String code, @Nullable final Editor editor) {
    showConsole(() -> ProgressManager.getInstance().run(new Task.Backgroundable(null, "Executing Code in Console...", false) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        long time = System.currentTimeMillis();
        while (!myExecuteActionHandler.isEnabled() || !myExecuteActionHandler.canExecuteNow()) {
          if (indicator.isCanceled()) {
            break;
          }
          if (System.currentTimeMillis() - time > 1000) {
            if (editor != null) {
              UIUtil.invokeLaterIfNeeded(
                () -> HintManager.getInstance().showErrorHint(editor, myExecuteActionHandler.getCantExecuteMessage()));
            }
            return;
          }
          TimeoutUtil.sleep(300);
        }
        if (!indicator.isCanceled()) {
          doExecute(code);
        }
      }
    }));
  }

  private void showConsole(@NotNull Runnable runnable) {
    PythonConsoleToolWindow toolWindow = PythonConsoleToolWindow.getInstance(getProject());
    if (toolWindow != null && toolWindow.getToolWindow() != null && !toolWindow.getToolWindow().isVisible() && !ApplicationManager.getApplication().isUnitTestMode()) {
      toolWindow.getToolWindow().activate(runnable);
    }
    else {
      runnable.run();
    }
  }

  private void doExecute(String code) {
    String codeFragment = PyConsoleIndentUtil.normalize(code, myExecuteActionHandler.getCurrentIndentSize());
    codeFragment += "\n";
    executeInConsole(codeFragment);
  }

  public void executeInConsole(final String code) {
    UIUtil.invokeLaterIfNeeded(() -> {
      String text = getConsoleEditor().getDocument().getText();

      setInputText(code);
      myExecuteActionHandler.runExecuteAction(this);

      if (!StringUtil.isEmpty(text)) {
        setInputText(text);
      }
    });
  }

  public void executeStatement(@NotNull String statement, @NotNull final Key attributes) {
    print(statement, outputTypeForAttributes(attributes));
    myExecuteActionHandler.processLine(statement, true);
  }

  public void printText(String text, final ConsoleViewContentType outputType) {
    super.print(text, outputType);
  }

  public void print(String text, @NotNull final Key attributes) {
    print(text, outputTypeForAttributes(attributes));
  }

  @Override
  public void print(@NotNull String text, @NotNull final ConsoleViewContentType outputType) {
    detectIPython(text, outputType);
    if (PyConsoleUtil.detectIPythonEnd(text)) {
      myIsIPythonOutput = false;
      mySourceHighlighter = null;
    }
    else if (PyConsoleUtil.detectIPythonStart(text)) {
      myIsIPythonOutput = true;
    }
    else {
      if (mySourceHighlighter == null || outputType == ConsoleViewContentType.ERROR_OUTPUT) {
        if (myHyperlink) {
          printHyperlink(text, outputType);
        }
        else {
          //Print text normally with converted attributes
          super.print(text, outputType);
        }
        myHyperlink = detectHyperlink(text);
        if (mySourceHighlighter == null && myIsIPythonOutput && PyConsoleUtil.detectSourcePrinting(text)) {
          mySourceHighlighter = new PyConsoleSourceHighlighter(this, myScheme, myPyHighlighter);
        }
      }
      else {
        try {
          mySourceHighlighter.printHighlightedSource(text);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
  }

  public void detectIPython(String text, final ConsoleViewContentType outputType) {
    VirtualFile file = getVirtualFile();
    if (PyConsoleUtil.detectIPythonImported(text, outputType)) {
      PyConsoleUtil.markIPython(file);
    }
    if (PyConsoleUtil.detectIPythonAutomagicOn(text)) {
      PyConsoleUtil.setIPythonAutomagic(file, true);
    }
    if (PyConsoleUtil.detectIPythonAutomagicOff(text)) {
      PyConsoleUtil.setIPythonAutomagic(file, false);
    }
  }

  private boolean detectHyperlink(@NotNull String text) {
    return myIsIPythonOutput && text.startsWith("File:");
  }

  private void printHyperlink(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    if (!StringUtil.isEmpty(text)) {
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(text.trim());

      if (vFile != null) {
        OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(getProject(), vFile, -1);

        super.printHyperlink(text, hyperlink);
      }
      else {
        super.print(text, contentType);
      }
    }
  }

  public ConsoleViewContentType outputTypeForAttributes(Key attributes) {
    final ConsoleViewContentType outputType;
    if (attributes == ProcessOutputTypes.STDERR) {
      outputType = ConsoleViewContentType.ERROR_OUTPUT;
    }
    else if (attributes == ProcessOutputTypes.SYSTEM) {
      outputType = ConsoleViewContentType.SYSTEM_OUTPUT;
    }
    else {
      outputType = ConsoleViewContentType.getConsoleViewType(attributes);
    }

    return outputType;
  }

  public void setSdk(Sdk sdk) {
    getFile().putCopyableUserData(PydevConsoleRunner.CONSOLE_SDK, sdk);
  }

  public void showVariables(PydevConsoleCommunication consoleCommunication) {
    PyStackFrame stackFrame = new PyStackFrame(getProject(), consoleCommunication, new PyStackFrameInfo("", "", "", null), null);
    final XStandaloneVariablesView view = new XStandaloneVariablesView(getProject(), new PyDebuggerEditorsProvider(), stackFrame);
    consoleCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void commandExecuted(boolean more) {
        view.rebuildView();
      }

      @Override
      public void inputRequested() {
      }
    });
    mySplitView = view;
    Disposer.register(this, view);
    splitWindow();
  }

  private void splitWindow() {
    Component console = getComponent(0);
    removeAll();
    JBSplitter p = new JBSplitter(false, 2f/3);
    p.setFirstComponent((JComponent)console);
    p.setSecondComponent(mySplitView.getPanel());
    p.setShowDividerControls(true);
    p.setHonorComponentsMinimumSize(true);

    add(p, BorderLayout.CENTER);
    validate();
    repaint();
  }

  public void restoreWindow() {
    JBSplitter pane = (JBSplitter)getComponent(0);
    removeAll();
    if (mySplitView != null) {
      Disposer.dispose(mySplitView);
      mySplitView = null;
    }
    add(pane.getFirstComponent(), BorderLayout.CENTER);
    validate();
    repaint();
  }
}
