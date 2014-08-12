package com.jetbrains.env.remote;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.env.PyEnvTaskRunner;
import com.jetbrains.env.PyExecutionFixtureTestTask;
import com.jetbrains.env.PyTestTask;
import com.jetbrains.env.python.PythonDebuggerTest;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class PyRemoteDebuggerTest extends PythonDebuggerTest {
  @Override
  protected void doRunTests(PyTestTask testTask, String testName, List<String> roots) {
    if (RUN_REMOTE && PyTestRemoteSdkProvider.shouldRunRemoteSdk(testTask)) {
      if (PyTestRemoteSdkProvider.canRunRemoteSdk()) {
        PyEnvTaskRunner remoteRunner = new RemoteTaskRunner(roots);
        remoteRunner.runTask(testTask, testName);
      }
      else {
        if (PyTestRemoteSdkProvider.shouldFailWhenCantRunRemote()) {
          fail("Cant run with remote sdk: password isn't set");
        }
      }
    }
  }

  private static class RemoteTaskRunner extends PyEnvTaskRunner {
    private RemoteTaskRunner(List<String> roots) {
      super(roots);
    }

    @Override
    protected String getExecutable(String root, PyTestTask testTask) {
      try {
        final Sdk sdk = PyTestRemoteSdkProvider.getInstance().getSdk(getProject(testTask), super.getExecutable(root, testTask));

        if (ProjectJdkTable.getInstance().findJdk(sdk.getName()) == null) {
          UIUtil.invokeAndWaitIfNeeded(new Runnable() {
            @Override
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  ProjectJdkTable.getInstance().addJdk(sdk);
                }
              });
            }
          });
        }

        return ((PyRemoteSdkAdditionalData)sdk.getSdkAdditionalData()).getRemoteSdkCredentials(false).getSdkId();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Nullable
    public Project getProject(PyTestTask task) {
      if (task instanceof PyExecutionFixtureTestTask) {
        return ((PyExecutionFixtureTestTask)task).getProject();
      }
      return null;
    }

    @Override
    protected boolean shouldRun(String root, PyTestTask task) {
      return !isJython(super.getExecutable(root, task)); //TODO: make remote tests work for jython
    }

    @Override
    protected String getEnvType() {
      return "remote";
    }
  }
}
