package com.jetbrains.python.console;

import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.Consumer;
import com.intellij.util.NotNullFunction;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author traff
 */
public class PyOpenDebugConsoleAction extends AnAction implements DumbAware {

  public PyOpenDebugConsoleAction() {
    super();
    getTemplatePresentation().setIcon(PythonIcons.Python.Debug.CommandLine);
  }

  @Override
  public void update(final AnActionEvent e) {
    e.getPresentation().setVisible(false);
    e.getPresentation().setEnabled(true);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      e.getPresentation().setVisible(getConsoles(project).size() > 0);
    }
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      selectRunningProcess(e.getDataContext(), project, new Consumer<PythonDebugLanguageConsoleView>() {
        @Override
        public void consume(PythonDebugLanguageConsoleView view) {
          view.showDebugConsole(true);
          IdeFocusManager.getInstance(project).requestFocus(view.getPydevConsoleView().getComponent(), true);
        }
      });
    }
  }


  private static void selectRunningProcess(@NotNull DataContext dataContext, @NotNull Project project,
                                           final Consumer<PythonDebugLanguageConsoleView> consumer) {
    Collection<RunContentDescriptor> consoles = getConsoles(project);

    ExecutionHelper
      .selectContentDescriptor(dataContext, project, consoles, "Select running python process", new Consumer<RunContentDescriptor>() {
        @Override
        public void consume(RunContentDescriptor descriptor) {
          if (descriptor != null && descriptor.getExecutionConsole() instanceof PythonDebugLanguageConsoleView) {
            consumer.consume((PythonDebugLanguageConsoleView)descriptor.getExecutionConsole());
          }
        }
      });
  }

  private static Collection<RunContentDescriptor> getConsoles(Project project) {
    return ExecutionHelper.findRunningConsole(project, new NotNullFunction<RunContentDescriptor, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(RunContentDescriptor dom) {
        return dom.getExecutionConsole() instanceof PythonDebugLanguageConsoleView && isAlive(dom);
      }
    });
  }

  private static boolean isAlive(RunContentDescriptor dom) {
    ProcessHandler processHandler = dom.getProcessHandler();
    return processHandler != null && !processHandler.isProcessTerminated();
  }
}
