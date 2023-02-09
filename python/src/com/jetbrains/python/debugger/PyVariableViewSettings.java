// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public final class PyVariableViewSettings {
  public static class SimplifiedView extends ToggleAction {
    private final PyDebugProcess myProcess;
    private volatile boolean mySimplifiedView;

    public SimplifiedView(@Nullable PyDebugProcess debugProcess) {
      super(PyBundle.message("debugger.simplified.view.text"), PyBundle.message("debugger.simplified.view.description"), null);
      mySimplifiedView = PyDebuggerSettings.getInstance().isSimplifiedView();
      myProcess = debugProcess;
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return mySimplifiedView;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean hide) {
      mySimplifiedView = hide;
      PyDebuggerSettings.getInstance().setSimplifiedView(hide);
      if (myProcess != null) {
        myProcess.getSession().rebuildViews();
      }
    }
  }

  public static void showWarningMessage(@Nullable final XCompositeNode node) {
    if (node == null) return;
    final PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
    if (debuggerSettings.getValuesPolicy() == PyDebugValue.ValuesPolicy.ON_DEMAND) return;

    node.setMessage(
      PyBundle.message("debugger.variables.view.warning.message"),
      AllIcons.General.BalloonWarning,
      SimpleTextAttributes.REGULAR_ATTRIBUTES,
      new XDebuggerTreeNodeHyperlink(PyBundle.message("debugger.variables.view.switch.to.loading.on.demand")) {
        private boolean linkClicked = false;

        @Override
        public void onClick(MouseEvent event) {
          debuggerSettings.setValuesPolicy(PyDebugValue.ValuesPolicy.ON_DEMAND);
          linkClicked = true;
        }

        @NotNull
        @Override
        public String getLinkText() {
          if (linkClicked) {
            return "";
          }
          else {
            return PyBundle.message("debugger.variables.view.switch.to.loading.on.demand");
          }
        }
      });
  }

  public static class VariablesPolicyGroup extends DefaultActionGroup {
    @NotNull private final List<PolicyAction> myValuesPolicyActions = new ArrayList<>();
    private final List<ValuesPolicyListener> myValuesPolicyListeners = new ArrayList<>();

    public VariablesPolicyGroup() {
      super(PyBundle.message("debugger.variables.loading.policy"), true);
      myValuesPolicyActions
        .add(new PolicyAction(PyBundle.message("debugger.variables.loading.synchronously.text"),
                              PyBundle.message("debugger.variables.loading.synchronously.description"), PyDebugValue.ValuesPolicy.SYNC, this));
      myValuesPolicyActions
        .add(new PolicyAction(PyBundle.message("debugger.variables.loading.asynchronously.text"),
                              PyBundle.message("debugger.variables.loading.asynchronously.description"), PyDebugValue.ValuesPolicy.ASYNC, this));
      myValuesPolicyActions
        .add(new PolicyAction(PyBundle.message("debugger.variables.loading.on.demand.text"),
                              PyBundle.message("debugger.variables.loading.on.demand.description"), PyDebugValue.ValuesPolicy.ON_DEMAND, this));

      for (AnAction action : myValuesPolicyActions) {
        add(action);
      }
    }

    public void updatePolicyActions() {
      final PyDebugValue.ValuesPolicy currentValuesPolicy = PyDebuggerSettings.getInstance().getValuesPolicy();
      for (PolicyAction action : myValuesPolicyActions) {
        action.setEnabled(currentValuesPolicy == action.getPolicy());
      }
    }

    private void notifyValuesPolicyUpdated() {
      for (ValuesPolicyListener listener : myValuesPolicyListeners) {
        listener.valuesPolicyUpdated();
      }
    }

    public void addValuesPolicyListener(@NotNull ValuesPolicyListener listener) {
      myValuesPolicyListeners.add(listener);
    }

    public interface ValuesPolicyListener {
      void valuesPolicyUpdated();
    }
  }

  public static class PolicyAction extends ToggleAction {
    @NotNull private final PyDebugValue.ValuesPolicy myPolicy;
    @NotNull private final VariablesPolicyGroup myActionGroup;
    private volatile boolean isEnabled;

    public PolicyAction(@NotNull @Nls String text,
                        @NotNull @Nls String description,
                        @NotNull PyDebugValue.ValuesPolicy policy,
                        @NotNull VariablesPolicyGroup actionGroup) {
      super(text, description, null);
      myPolicy = policy;
      myActionGroup = actionGroup;
      isEnabled = PyDebuggerSettings.getInstance().getValuesPolicy() == policy;
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      myActionGroup.updatePolicyActions();
      final Presentation presentation = e.getPresentation();
      presentation.setEnabled(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @NotNull
    public PyDebugValue.ValuesPolicy getPolicy() {
      return myPolicy;
    }

    public void setEnabled(boolean enabled) {
      isEnabled = enabled;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return isEnabled;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean hide) {
      isEnabled = hide;
      if (hide) {
        PyDebuggerSettings.getInstance().setValuesPolicy(myPolicy);
        myActionGroup.notifyValuesPolicyUpdated();
      }
      myActionGroup.updatePolicyActions();
    }
  }
}
