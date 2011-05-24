package org.intellij.plugins.xsltDebugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.JavaPatchableProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.intellij.lang.xpath.xslt.run.XsltRunConfiguration;
import org.intellij.plugins.xsltDebugger.impl.XsltDebugProcess;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 16.11.10
*/
public class XsltDebuggerRunner extends JavaPatchableProgramRunner {
  static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();

  @NonNls
  private static final String ID = "XsltDebuggerRunner";


  @NotNull
  @Override
  public String getRunnerId() {
    return ID;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals("Debug") && profile instanceof XsltRunConfiguration;
  }

  @Override
  protected RunContentDescriptor doExecute(Project project,
                                           Executor executor,
                                           RunProfileState state,
                                           RunContentDescriptor contentToReuse,
                                           ExecutionEnvironment env) throws ExecutionException {
    FileDocumentManager.getInstance().saveAllDocuments();
    return createContentDescriptor(project, executor, state, contentToReuse, env);
  }

  @Override
  public void patch(JavaParameters javaParameters, RunnerSettings settings, boolean beforeExecution) throws ExecutionException {
    javaParameters.setJdk(((XsltRunConfiguration)settings.getRunProfile()).getEffectiveJDK());
  }

  protected RunContentDescriptor createContentDescriptor(Project project,
                                                         final Executor executor,
                                                         final RunProfileState runProfileState,
                                                         RunContentDescriptor contentToReuse,
                                                         ExecutionEnvironment executionEnvironment) throws ExecutionException {
    final XDebugSession debugSession =
      XDebuggerManager.getInstance(project).startSession(this, executionEnvironment, contentToReuse, new XDebugProcessStarter() {
        @NotNull
        public XDebugProcess start(@NotNull final XDebugSession session) {
          ACTIVE.set(Boolean.TRUE);
          try {
            final ExecutionResult result = runProfileState.execute(executor, XsltDebuggerRunner.this);
            return new XsltDebugProcess(session, result);
          } catch (ExecutionException e) {
            throw new RuntimeException(e);
          } finally {
            ACTIVE.remove();
          }
        }
      });
    return debugSession.getRunContentDescriptor();
  }
}