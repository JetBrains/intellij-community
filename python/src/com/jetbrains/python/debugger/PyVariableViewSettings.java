// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.NlsActions;
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
    if (debuggerSettings.getValuesPolicy() == ValuesPolicy.ON_DEMAND) return;

    node.setMessage(
      PyBundle.message("debugger.variables.view.warning.message"),
      AllIcons.General.BalloonWarning,
      SimpleTextAttributes.REGULAR_ATTRIBUTES,
      new XDebuggerTreeNodeHyperlink(PyBundle.message("debugger.variables.view.switch.to.loading.on.demand")) {
        private boolean linkClicked = false;

        @Override
        public void onClick(MouseEvent event) {
          debuggerSettings.setValuesPolicy(ValuesPolicy.ON_DEMAND);
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

  public static class VariablePolicyAction extends AbstractPolicyAction<ValuesPolicy, VariablesPolicyGroup> {

    public VariablePolicyAction(@Nls @NotNull String text,
                                @Nls @NotNull String description,
                                @NotNull ValuesPolicy policy,
                                @NotNull VariablesPolicyGroup actionGroup) {
      super(text, description, policy, actionGroup);
    }

    @Override
    protected void changeDebuggerSettings() {
      PyDebuggerSettings.getInstance().setValuesPolicy(getPolicy());
    }
  }

  public static class VariablesPolicyGroup extends AbstractPolicyGroup<ValuesPolicy, VariablePolicyAction> {
    public VariablesPolicyGroup() {
      super(PyBundle.message("debugger.variables.loading.policy"));
      addPolicyActions(new VariablePolicyAction(PyBundle.message("debugger.variables.loading.synchronously.text"),
                                                PyBundle.message("debugger.variables.loading.synchronously.description"),
                                                ValuesPolicy.SYNC, this),
                       new VariablePolicyAction(PyBundle.message("debugger.variables.loading.asynchronously.text"),
                                                PyBundle.message("debugger.variables.loading.asynchronously.description"),
                                                ValuesPolicy.ASYNC, this),
                       new VariablePolicyAction(PyBundle.message("debugger.variables.loading.on.demand.text"),
                                                PyBundle.message("debugger.variables.loading.on.demand.description"),
                                                ValuesPolicy.ON_DEMAND, this));
    }

    @Override
    protected ValuesPolicy getDebuggerPolicy() {
      return PyDebuggerSettings.getInstance().getValuesPolicy();
    }
  }

  public static class QuotingPolicyAction extends AbstractPolicyAction<QuotingPolicy, QuotingPolicyGroup> {

    public QuotingPolicyAction(@Nls @NotNull String text,
                               @Nls @NotNull String description,
                               @NotNull QuotingPolicy policy,
                               @NotNull QuotingPolicyGroup actionGroup) {
      super(text, description, policy, actionGroup);
    }

    @Override
    protected void changeDebuggerSettings() {
      PyDebuggerSettings.getInstance().setQuotingPolicy(getPolicy());
    }
  }

  public static class QuotingPolicyGroup extends AbstractPolicyGroup<QuotingPolicy, QuotingPolicyAction> {
    public QuotingPolicyGroup() {
      super(PyBundle.message("debugger.variables.view.quoting.policy"));
      addPolicyActions(new QuotingPolicyAction(PyBundle.message("debugger.variables.view.quoting.single.text"),
                                               PyBundle.message("debugger.variables.view.quoting.single.description"),
                                               QuotingPolicy.SINGLE, this),
                       new QuotingPolicyAction(PyBundle.message("debugger.variables.view.quoting.double.text"),
                                               PyBundle.message("debugger.variables.view.quoting.double.description"),
                                               QuotingPolicy.DOUBLE, this),
                       new QuotingPolicyAction(PyBundle.message("debugger.variables.view.quoting.without.text"),
                                               PyBundle.message("debugger.variables.view.quoting.without.description"),
                                               QuotingPolicy.NONE, this));
    }

    @Override
    protected QuotingPolicy getDebuggerPolicy() {
      return PyDebuggerSettings.getInstance().getQuotingPolicy();
    }
  }

  private static abstract class AbstractPolicyAction<Policy extends AbstractPolicy, PolicyGroup extends AbstractPolicyGroup<Policy, ? extends ToggleAction>>
    extends ToggleAction {
    @NotNull private final Policy myPolicy;
    @NotNull private final PolicyGroup myActionGroup;
    private volatile boolean isEnabled;

    private AbstractPolicyAction(@NotNull @Nls String text,
                                 @NotNull @Nls String description,
                                 @NotNull Policy policy,
                                 @NotNull PolicyGroup actionGroup) {
      super(text, description, null);
      myPolicy = policy;
      myActionGroup = actionGroup;
      isEnabled = PyDebuggerSettings.getInstance().getValuesPolicy() == policy;
    }

    public @NotNull PolicyGroup getActionGroup() {
      return myActionGroup;
    }

    public @NotNull Policy getPolicy() {
      return myPolicy;
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
        changeDebuggerSettings();
        myActionGroup.notifyPolicyUpdated();
      }
      myActionGroup.updatePolicyActions();
    }

    protected abstract void changeDebuggerSettings();
  }

  private static abstract class AbstractPolicyGroup<Policy extends AbstractPolicy, PolicyAction extends AbstractPolicyAction<Policy, ? extends DefaultActionGroup>>
    extends DefaultActionGroup {
    @NotNull private final List<PolicyAction> myPolicyActions = new ArrayList<>();
    private final List<PolicyListener> myPolicyListeners = new ArrayList<>();

    private AbstractPolicyGroup(@NlsActions.ActionText String name) {
      super(name, true);
    }

    public void addPolicyActions(PolicyAction... actions) {
      myPolicyActions.addAll(List.of(actions));
      for (AnAction action : myPolicyActions) {
        add(action);
      }
    }

    public void notifyPolicyUpdated() {
      for (PolicyListener listener : myPolicyListeners) {
        listener.valuesPolicyUpdated();
      }
    }

    public void addValuesPolicyListener(@NotNull PolicyListener listener) {
      myPolicyListeners.add(listener);
    }

    protected void updatePolicyActions() {
      final AbstractPolicy currentPolicy = getDebuggerPolicy();
      for (PolicyAction action : myPolicyActions) {
        action.setEnabled(currentPolicy == action.getPolicy());
      }
    }

    protected abstract AbstractPolicy getDebuggerPolicy();
  }
}
