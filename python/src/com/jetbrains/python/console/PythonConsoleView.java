/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.OpenFileHyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
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
public class PythonConsoleView extends JPanel implements LanguageConsoleView, ObservableConsoleView, PyCodeExecutor {

  private static final Logger LOG = Logger.getInstance(PythonConsoleView.class);

  private Project myProject;
  private PydevConsoleExecuteActionHandler myExecuteActionHandler;
  private PyConsoleSourceHighlighter mySourceHighlighter;
  private boolean myIsIPythonOutput = false;
  private PyHighlighter myPyHighlighter;
  private EditorColorsScheme myScheme;
  private boolean myHyperlink;

  private final LanguageConsoleViewImpl myLanguageConsoleView;
  
  private Disposable mySplittedDisposable;

  public PythonConsoleView(final Project project, final String title, Sdk sdk) {
    super(new BorderLayout());

    myLanguageConsoleView = new LanguageConsoleViewImpl(new PythonLanguageConsole(project, title, sdk));

    add(myLanguageConsoleView.getComponent(), BorderLayout.CENTER);

    getPythonLanguageConsole().setPrompt(PyConsoleUtil.ORDINARY_PROMPT);
    myLanguageConsoleView.setUpdateFoldingsEnabled(false);
    myProject = project;
    //noinspection ConstantConditions
    myPyHighlighter = new PyHighlighter(
      sdk != null && sdk.getVersionString() != null ? LanguageLevel.fromPythonVersion(sdk.getVersionString()) : LanguageLevel.getDefault());
    myScheme = getPythonLanguageConsole().getConsoleEditor().getColorsScheme();
  }

  public void setConsoleCommunication(final ConsoleCommunication communication) {
    getPythonLanguageConsole().setConsoleCommunication(communication);
  }

  public void setExecutionHandler(@NotNull PydevConsoleExecuteActionHandler consoleExecuteActionHandler) {
    myExecuteActionHandler = consoleExecuteActionHandler;
  }

  public void requestFocus() {
    IdeFocusManager.findInstance().requestFocus(getPythonLanguageConsole().getConsoleEditor().getContentComponent(), true);
    myLanguageConsoleView.updateUI();
    getLanguageConsole().getHistoryViewer().getComponent().updateUI();
  }

  private PythonLanguageConsole getPythonLanguageConsole() {
    return ((PythonLanguageConsole)getLanguageConsole());
  }

  public LanguageConsoleImpl getLanguageConsole() {
    return myLanguageConsoleView.getConsole();
  }

  @Override
  public void executeCode(final @NotNull String code, @Nullable final Editor editor) {
    ProgressManager.getInstance().run(new Task.Backgroundable(null, "Executing code in console...", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        long time = System.currentTimeMillis();
        while (!myExecuteActionHandler.isEnabled() || !myExecuteActionHandler.canExecuteNow()) {
          if (indicator.isCanceled()) {
            break;
          }
          if (System.currentTimeMillis() - time > 1000) {
            if (editor != null) {
              UIUtil.invokeLaterIfNeeded(new Runnable() {
                @Override
                public void run() {
                  HintManager.getInstance().showErrorHint(editor, myExecuteActionHandler.getCantExecuteMessage());
                }
              });
            }
            return;
          }
          try {
            Thread.sleep(300);
          }
          catch (InterruptedException e) {
          }
        }
        if (!indicator.isCanceled()) {
          doExecute(code);
        }
      }
    });
  }


  private void doExecute(String code) {
    executeInConsole(PyConsoleIndentUtil.normalize(code, myExecuteActionHandler.getCurrentIndentSize()));
  }

  public void executeInConsole(final String code) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        String text = getPythonLanguageConsole().getConsoleEditor().getDocument().getText();

        getPythonLanguageConsole().setTextToEditor(code);
        myExecuteActionHandler.runExecuteAction(getPythonLanguageConsole());

        if (!StringUtil.isEmpty(text)) {
          getPythonLanguageConsole().setTextToEditor(text);
        }
      }
    });
  }

  public void executeStatement(@NotNull String statement, @NotNull final Key attributes) {
    print(statement, outputTypeForAttributes(attributes));
    myExecuteActionHandler.processLine(statement, true);
  }

  public void print(String text, @NotNull final Key attributes) {
    print(text, outputTypeForAttributes(attributes));
  }

  public void printText(String text, final ConsoleViewContentType outputType) {
    myLanguageConsoleView.print(text, outputType);
  }

  public void print(String text, final ConsoleViewContentType outputType) {
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
          myLanguageConsoleView.print(text, outputType);
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

  @Override
  public void clear() {
    myLanguageConsoleView.clear();
  }

  @Override
  public void scrollTo(int offset) {
    myLanguageConsoleView.scrollTo(offset);
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    myLanguageConsoleView.attachToProcess(processHandler);
  }

  @Override
  public void setOutputPaused(boolean value) {
    myLanguageConsoleView.setOutputPaused(value);
  }

  @Override
  public boolean isOutputPaused() {
    return myLanguageConsoleView.isOutputPaused();
  }

  @Override
  public boolean hasDeferredOutput() {
    return myLanguageConsoleView.hasDeferredOutput();
  }

  @Override
  public void performWhenNoDeferredOutput(Runnable runnable) {
    myLanguageConsoleView.performWhenNoDeferredOutput(runnable);
  }

  @Override
  public void setHelpId(String helpId) {
    myLanguageConsoleView.setHelpId(helpId);
  }

  @Override
  public void addMessageFilter(Filter filter) {
    myLanguageConsoleView.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
    myLanguageConsoleView.printHyperlink(hyperlinkText, info);
  }

  @Override
  public int getContentSize() {
    return myLanguageConsoleView.getContentSize();
  }

  @Override
  public boolean canPause() {
    return myLanguageConsoleView.canPause();
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return myLanguageConsoleView.createConsoleActions();
  }

  @Override
  public void allowHeavyFilters() {
    myLanguageConsoleView.allowHeavyFilters();
  }

  public void detectIPython(String text, final ConsoleViewContentType outputType) {
    VirtualFile file = getConsoleVirtualFile();
    if (file != null) {
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
  }

  public VirtualFile getConsoleVirtualFile() {
    return getLanguageConsole().getFile().getVirtualFile();
  }

  private boolean detectHyperlink(@NotNull String text) {
    return myIsIPythonOutput && text.startsWith("File:");
  }

  private void printHyperlink(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    if (!StringUtil.isEmpty(text)) {
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(text.trim());

      if (vFile != null) {
        OpenFileHyperlinkInfo hyperlink = new OpenFileHyperlinkInfo(myProject, vFile, -1);

        myLanguageConsoleView.printHyperlink(text, hyperlink);
      }
      else {
        myLanguageConsoleView.print(text, contentType);
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
    getPythonLanguageConsole().setSdk(sdk);
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myLanguageConsoleView.getPreferredFocusableComponent();
  }

  @Override
  public void dispose() {
    myLanguageConsoleView.dispose();
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    myLanguageConsoleView.addChangeListener(listener, parent);
  }

  @NotNull
  @Override
  public LanguageConsoleImpl getConsole() {
    return myLanguageConsoleView.getConsole();
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  public void showVariables(PydevConsoleCommunication consoleCommunication) {
    PyStackFrame stackFrame = new PyStackFrame(myProject, consoleCommunication, new PyStackFrameInfo("", "", "", null), null);
    final XStandaloneVariablesView view = new XStandaloneVariablesView(myProject, new PyDebuggerEditorsProvider(), stackFrame);
    consoleCommunication.addCommunicationListener(new ConsoleCommunicationListener() {
      @Override
      public void commandExecuted() {
        view.rebuildView();
      }

      @Override
      public void inputRequested() {
      }
    });
    splitWindow(view.getPanel(), view);
  }

  private void splitWindow(JComponent component, Disposable componentDisposable) {
    removeAll();
    JSplitPane p = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    p.add(myLanguageConsoleView.getComponent(), JSplitPane.LEFT);
    mySplittedDisposable = componentDisposable;
    p.add(component, JSplitPane.RIGHT);
    p.setDividerLocation((int)getSize().getWidth()*2/3);
    add(p, BorderLayout.CENTER);

    validate();
    repaint();
  }

  public void restoreWindow() {
    removeAll();
    add(myLanguageConsoleView.getComponent(), BorderLayout.CENTER);
    validate();
    repaint();
    if (mySplittedDisposable != null) {
      Disposer.dispose(mySplittedDisposable);
      mySplittedDisposable = null;
    }
  }

  private static class PythonLanguageConsole extends LanguageConsoleImpl {

    public PythonLanguageConsole(final Project project, final String title, final Sdk sdk) {
      super(project, title, PythonLanguage.getInstance(), false);
      initLanguageLevel(sdk);
      // Mark editor as console one, to prevent autopopup completion
      getConsoleEditor().putUserData(PythonConsoleAutopopupBlockingHandler.REPL_KEY, new Object());

      setShowSeparatorLine(PyConsoleOptions.getInstance(project).isShowSeparatorLine());

      initComponents();
    }

    private void initLanguageLevel(@Nullable Sdk sdk) {
      if (myFile.getVirtualFile() != null) {
        //noinspection ConstantConditions
        myFile.getVirtualFile().putUserData(LanguageLevel.KEY, PythonSdkType.getLanguageLevelForSdk(sdk));
      }
    }

    public void setConsoleCommunication(final ConsoleCommunication communication) {
      myFile.putCopyableUserData(PydevConsoleRunner.CONSOLE_KEY, communication);
    }

    public void setSdk(Sdk sdk) {
      myFile.putCopyableUserData(PydevConsoleRunner.CONSOLE_SDK, sdk);
    }
  }
}
