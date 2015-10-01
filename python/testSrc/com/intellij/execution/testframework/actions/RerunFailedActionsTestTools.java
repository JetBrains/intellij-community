package com.intellij.execution.testframework.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction.MyRunProfile;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.python.testing.PyRerunFailedTestsAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utils to work with "rerun failed tests" action
 *
 * @author Ilya.Kazakevich
 */
public final class RerunFailedActionsTestTools {
  private RerunFailedActionsTestTools() {
  }

  /**
   * Fetches "rerun" env from run action
   *
   * @param runAction action to fetch state from
   * @return state or null if not found
   */
  @Nullable
  public static ExecutionEnvironment getReRunEnvironment(@NotNull final AbstractRerunFailedTestsAction runAction) {
    final MyRunProfile profile = runAction.getRunProfile(new ExecutionEnvironment());
    if (profile == null) {
      return null;
    }
    final Ref<ExecutionEnvironment> stateRef = new Ref<ExecutionEnvironment>();
    UsefulTestCase.edt(new Runnable() {
      @Override
      public void run() {
        stateRef.set(ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), profile).build());
      }
    });
    return stateRef.get();
  }


  /**
   * Searches for "rerun failed tests" action and fetches state from it
   *
   * @param descriptor previous run descriptor
   * @return state (if found)
   */
  @Nullable
  public static RunProfileState findRestartActionState(@NotNull final RunContentDescriptor descriptor) {
    final ExecutionEnvironment action = findRestartAction(descriptor);
    if (action == null) {
      return null;
    }
    final Ref<RunProfileState> stateRef = new Ref<RunProfileState>();
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          stateRef.set(action.getState());
        }
        catch (final ExecutionException e) {
          throw new IllegalStateException("Error obtaining execution state", e);
        }
      }
    }, ModalityState.NON_MODAL);

    return stateRef.get();
  }

  /**
   * Searches for "rerun failed tests" action and returns it's environment
   *
   * @param descriptor previous run descriptor
   * @return environment (if found)
   */
  @Nullable
  public static ExecutionEnvironment findRestartAction(@NotNull final RunContentDescriptor descriptor) {
    for (final AnAction action : descriptor.getRestartActions()) {
      if (action instanceof PyRerunFailedTestsAction) {
        final PyRerunFailedTestsAction rerunFailedTestsAction = (PyRerunFailedTestsAction)action;
        return getReRunEnvironment(rerunFailedTestsAction);
      }
    }
    return null;
  }
}
