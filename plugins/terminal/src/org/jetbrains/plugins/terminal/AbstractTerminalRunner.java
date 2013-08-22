package org.jetbrains.plugins.terminal;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.ColorPalette;
import com.jediterm.terminal.ui.AbstractSystemSettingsProvider;
import com.jediterm.terminal.ui.TerminalSession;
import com.jediterm.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author traff
 */
public abstract class AbstractTerminalRunner<T extends Process> {
  private static final Logger LOG = Logger.getInstance(AbstractTerminalRunner.class.getName());
  @NotNull
  protected final Project myProject;

  public AbstractTerminalRunner(@NotNull Project project) {
    myProject = project;
  }

  public void run() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Running the terminal", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText("Running the terminal...");
        try {
          doRun();
        }
        catch (final Exception e) {
          LOG.warn("Error running terminal", e);

          UIUtil.invokeLaterIfNeeded(new Runnable() {

            @Override
            public void run() {
              Messages.showErrorDialog(AbstractTerminalRunner.this.getProject(), e.getMessage(), getTitle());
            }
          });
        }
      }
    });
  }

  private void doRun() {
    // Create Server process
    try {
      final T process = createProcess();

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          initConsoleUI(process);
        }
      });
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  protected abstract T createProcess() throws ExecutionException;

  protected abstract ProcessHandler createProcessHandler(T process);

  public JBTabbedTerminalWidget createTerminalWidget() {
    JBTerminalSystemSettingsProvider provider = new JBTerminalSystemSettingsProvider();
    JBTabbedTerminalWidget terminalWidget = new JBTabbedTerminalWidget(provider);
    provider.setTerminalWidget(terminalWidget);
    openSession(terminalWidget);
    return terminalWidget;
  }

  private void initConsoleUI(final T process) {
    final Executor defaultExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);

    JBTerminalSystemSettingsProvider provider = new JBTerminalSystemSettingsProvider();
    TerminalWidget widget = new JBTabbedTerminalWidget(provider);
    provider.setTerminalWidget(widget);

    openSession(widget, createTtyConnector(process));

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);

    panel.add(widget.getComponent(), BorderLayout.CENTER);

    actionToolbar.setTargetComponent(panel);

    ProcessHandler processHandler = createProcessHandler(process);

    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(null, processHandler, panel, getTerminalConnectionName(process));

    contentDescriptor.setAutoFocusContent(true);

    toolbarActions.add(createCloseAction(defaultExecutor, contentDescriptor));

    showConsole(defaultExecutor, contentDescriptor, widget.getComponent());

    processHandler.startNotify();
  }

  public static void openSession(TerminalWidget terminal, TtyConnector ttyConnector) {
    TerminalSession session = terminal.createTerminalSession(ttyConnector);
    session.start();
  }

  protected abstract String getTerminalConnectionName(T process);

  protected abstract TtyConnector createTtyConnector(T process);

  protected AnAction createCloseAction(final Executor defaultExecutor, final RunContentDescriptor myDescriptor) {
    return new CloseAction(defaultExecutor, myDescriptor, myProject);
  }


  protected void showConsole(Executor defaultExecutor, RunContentDescriptor myDescriptor, final Component toFocus) {
    // Show in run toolwindow
    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(defaultExecutor, myDescriptor);

// Request focus
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(defaultExecutor.getId());
    window.activate(new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(myProject).requestFocus(toFocus, true);
      }
    });
  }

  protected Project getProject() {
    return myProject;
  }

  private class JBTerminalSystemSettingsProvider extends AbstractSystemSettingsProvider {
    private TerminalWidget myTerminalWidget;

    public void setTerminalWidget(TerminalWidget terminalWidget) {
      myTerminalWidget = terminalWidget;
    }

    @Override
    public AbstractAction getNewSessionAction() {
      return new AbstractAction("New Session") {
        @Override
        public void actionPerformed(ActionEvent event) {
          openSession(myTerminalWidget);
        }
      };
    }

    @Override
    public KeyStroke[] getCopyKeyStrokes() {
      return getKeyStrokesByActionId("$Copy");
    }

    @Override
    public KeyStroke[] getPasteKeyStrokes() {
      return getKeyStrokesByActionId("$Paste");
    }

    @Override
    public ColorPalette getPalette() {
      return SystemInfo.isWindows ? ColorPalette.WINDOWS_PALETTE : ColorPalette.XTERM_PALETTE;
    }

    private KeyStroke[] getKeyStrokesByActionId(String actionId) {
      List<KeyStroke> keyStrokes = new ArrayList<KeyStroke>();
      Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId);
      for (Shortcut sc : shortcuts) {
        if (sc instanceof KeyboardShortcut) {
          KeyStroke ks = ((KeyboardShortcut)sc).getFirstKeyStroke();
          keyStrokes.add(ks);
        }
      }

      return keyStrokes.toArray(new KeyStroke[keyStrokes.size()]);
    }

    @Override
    public boolean shouldCloseTabOnLogout(TtyConnector ttyConnector) {
      return ttyConnector instanceof PtyProcessTtyConnector; //close tab only on logout of local pty, not remote
    }
  }

  public void openSession(TerminalWidget terminalWidget) {
    // Create Server process
    try {
      final T process = createProcess();

      openSession(terminalWidget, createTtyConnector(process));
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
