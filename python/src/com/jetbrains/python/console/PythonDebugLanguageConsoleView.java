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
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

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

  private final ConsoleViewImpl myTextConsole;

  public boolean myIsDebugConsole = false;

  private ProcessHandler myProcessHandler;

  public PythonDebugLanguageConsoleView(final Project project) {
    super(new CardLayout());
    myPydevConsoleView = createConsoleView(project);
    myTextConsole = (ConsoleViewImpl)TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

    add(myTextConsole.getComponent(), TEXT_CONSOLE_PANEL);
    add(myPydevConsoleView.getComponent(), PYDEV_CONSOLE_PANEL);
    showDebugConsole(false);
  }

  @Override
  public void executeCode(@NotNull String code) {
    showDebugConsole(true);
    getPydevConsoleView().executeCode(code);
  }

  private static PythonConsoleView createConsoleView(Project project) {
    return new PythonConsoleView(project, "");
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
    return myTextConsole;
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
    myPydevConsoleView.getConsole().getHistoryViewer().getCaretModel().moveToOffset(offset);
    myPydevConsoleView.getConsole().getHistoryViewer().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
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
  public void addChangeListener(ChangeListener listener, Disposable parent) {
    myPydevConsoleView.addChangeListener(listener, parent);
    myTextConsole.addChangeListener(listener, parent);
  }

  private static class ShowDebugConsoleAction extends ToggleAction implements DumbAware {
    private final PythonDebugLanguageConsoleView myConsole;


    public ShowDebugConsoleAction(final PythonDebugLanguageConsoleView console) {
      super(ExecutionBundle.message("run.configuration.show.command.line.action.name"), null,
            IconLoader.getIcon("/com/jetbrains/python/icons/debug/commandLine.png"));
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
