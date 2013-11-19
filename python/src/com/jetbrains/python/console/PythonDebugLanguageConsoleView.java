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

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author traff
 */
public class PythonDebugLanguageConsoleView extends JPanel implements ConsoleView, ObservableConsoleView, PyCodeExecutor {

  private final static String TEXT_CONSOLE_PANEL = "TEXT_CONSOLE_PANEL";
  private final static String PYDEV_CONSOLE_PANEL = "PYDEV_CONSOLE_PANEL";

  private final PythonConsoleView myPydevConsoleView;

  private final ConsoleView myTextConsole;

  public boolean myIsDebugConsole = false;

  private ProcessHandler myProcessHandler;

  public PythonDebugLanguageConsoleView(final Project project, Sdk sdk, ConsoleView consoleView) {
    super(new CardLayout());
    myPydevConsoleView = createConsoleView(project, sdk);
    myTextConsole = consoleView;

    add(myTextConsole.getComponent(), TEXT_CONSOLE_PANEL);
    add(myPydevConsoleView.getComponent(), PYDEV_CONSOLE_PANEL);

    showDebugConsole(PyConsoleOptions.getInstance(project).isShowDebugConsoleByDefault());

    Disposer.register(this, myPydevConsoleView);
    Disposer.register(this, myTextConsole);
  }

  public PythonDebugLanguageConsoleView(final Project project, Sdk sdk) {
    this(project, sdk, TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole());
  }

  @Override
  public void executeCode(@NotNull String code, @Nullable Editor e) {
    showDebugConsole(true);
    getPydevConsoleView().executeCode(code, e);
  }

  private static PythonConsoleView createConsoleView(Project project, Sdk sdk) {
    return new PythonConsoleView(project, "Python Console", sdk);
  }

  private void doShowConsole(String type) {
    CardLayout cl = (CardLayout)(getLayout());
    cl.show(this, type);
  }


  public boolean isDebugConsole() {
    return myIsDebugConsole;
  }

  public void showDebugConsole(boolean flag) {
    if (flag) {
      doShowConsole(PYDEV_CONSOLE_PANEL);
      myPydevConsoleView.requestFocus();
    }
    else {
      doShowConsole(TEXT_CONSOLE_PANEL);
    }
    myIsDebugConsole = flag;
  }

  public PythonConsoleView getPydevConsoleView() {
    return myPydevConsoleView;
  }

  public ConsoleViewImpl getTextConsole() {
    if (myTextConsole instanceof ConsoleViewImpl) {
      return (ConsoleViewImpl)myTextConsole;
    }
    return null;
  }

  @Override
  public void allowHeavyFilters() {
    myTextConsole.allowHeavyFilters();
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return this;
  }

  @Override
  public void dispose() {
  }

  @Override
  public void print(String s, ConsoleViewContentType contentType) {
    myPydevConsoleView.print(s, contentType);
    myTextConsole.print(s, contentType);
  }

  @Override
  public void clear() {
    myPydevConsoleView.clear();
    myTextConsole.clear();
  }

  @Override
  public void scrollTo(int offset) {
    myPydevConsoleView.getLanguageConsole().getHistoryViewer().getCaretModel().moveToOffset(offset);
    myPydevConsoleView.getLanguageConsole().getHistoryViewer().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    myTextConsole.scrollTo(offset);
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    myProcessHandler = processHandler;
    myPydevConsoleView.attachToProcess(processHandler);
    myTextConsole.attachToProcess(processHandler);
  }

  @Override
  public void setOutputPaused(boolean value) {
    myPydevConsoleView.setOutputPaused(value);
    myTextConsole.setOutputPaused(value);
  }

  @Override
  public boolean isOutputPaused() {
    return false;
  }

  @Override
  public boolean hasDeferredOutput() {
    return myPydevConsoleView.hasDeferredOutput() && myTextConsole.hasDeferredOutput();
  }

  @Override
  public void performWhenNoDeferredOutput(Runnable runnable) {

  }

  @Override
  public void setHelpId(String helpId) {
    myPydevConsoleView.setHelpId(helpId);
    myTextConsole.setHelpId(helpId);
  }

  @Override
  public void addMessageFilter(Filter filter) {
    myPydevConsoleView.addMessageFilter(filter);
    myTextConsole.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
    myPydevConsoleView.printHyperlink(hyperlinkText, info);
    myTextConsole.printHyperlink(hyperlinkText, info);
  }

  @Override
  public int getContentSize() {
    return myTextConsole.getContentSize();
  }

  @Override
  public boolean canPause() {
    return false;
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    List<AnAction> actions = Lists.newArrayList(myTextConsole.createConsoleActions());
    actions.add(new ShowDebugConsoleAction(this));

    return actions.toArray(new AnAction[actions.size()]);
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener, @NotNull Disposable parent) {
    myPydevConsoleView.addChangeListener(listener, parent);
    if (myTextConsole instanceof ObservableConsoleView) {
      ((ObservableConsoleView)myTextConsole).addChangeListener(listener, parent);
    }
  }

  private static class ShowDebugConsoleAction extends ToggleAction implements DumbAware {
    private final PythonDebugLanguageConsoleView myConsole;


    public ShowDebugConsoleAction(final PythonDebugLanguageConsoleView console) {
      super(ExecutionBundle.message("run.configuration.show.command.line.action.name"), null,
            PythonIcons.Python.Debug.CommandLine);
      myConsole = console;
    }

    public boolean isSelected(final AnActionEvent event) {
      return myConsole.isDebugConsole();
    }

    public void setSelected(final AnActionEvent event, final boolean flag) {
      myConsole.showDebugConsole(flag);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          update(event);
        }
      });
    }

    public void update(final AnActionEvent event) {
      super.update(event);
      final Presentation presentation = event.getPresentation();
      final boolean isRunning = myConsole.myProcessHandler != null && !myConsole.myProcessHandler.isProcessTerminated();
      if (isRunning) {
        presentation.setEnabled(true);
      }
      else {
        myConsole.showDebugConsole(false);
        presentation.putClientProperty(SELECTED_PROPERTY, false);
        presentation.setEnabled(false);
      }
    }
  }
}
