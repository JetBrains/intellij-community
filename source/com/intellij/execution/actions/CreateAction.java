package com.intellij.execution.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.Presentation;

public class CreateAction extends BaseRunConfigurationAction {
  public CreateAction() {
    super("Create Run Configuration", null, null);
  }

  protected void perform(final ConfigurationContext context) {
    choosePolicy(context).perform(context);
  }

  protected void updatePresentation(final Presentation presentation, final String actionText, final ConfigurationContext context) {
    choosePolicy(context).update(presentation, context, actionText);
  }

  private BaseCreatePolicy choosePolicy(final ConfigurationContext context) {
    final RunnerAndConfigurationSettings configuration = context.findExisting();
    if (configuration == null) return CREATE_AND_EDIT;
    final RunManager runManager = context.getRunManager();
    if (runManager.getSelectedConfiguration() != configuration) return SELECT;
    if (runManager.isTemporary(configuration.getConfiguration())) return SAVE;
    return SELECTED_STABLE;
  }

  private static abstract class BaseCreatePolicy {
    private final String myName;

    public BaseCreatePolicy(final String name) {
      myName = name;
    }

    public void update(final Presentation presentation, final ConfigurationContext context, final String actionText) {
      updateText(presentation, actionText);
      presentation.setIcon(context.getConfiguration().getFactory().getIcon());
    }

    protected void updateText(final Presentation presentation, final String actionText) {
      presentation.setText(myName + " " + actionText);
    }

    public abstract void perform(ConfigurationContext context);
  }

  private static class SelectPolicy extends BaseCreatePolicy {
    public SelectPolicy() {
      super("Select");
    }

    public void perform(final ConfigurationContext context) {
      final RunnerAndConfigurationSettings configuration = context.findExisting();
      if (configuration == null) return;
      context.getRunManager().setActiveConfiguration(configuration);
    }
  }

  private static class CreatePolicy extends BaseCreatePolicy {
    public CreatePolicy() {
      super("Create");
    }

    public void perform(final ConfigurationContext context) {
      final RunManager runManager = context.getRunManager();
      final RunnerAndConfigurationSettings configuration = context.getConfiguration();
      runManager.addConfiguration(configuration);
      runManager.setActiveConfiguration(configuration);
    }
  }

  private static class CreateAndEditPolicy extends CreatePolicy {
    protected void updateText(final Presentation presentation, final String actionText) {
      presentation.setText("Create " + actionText + "...");
    }

    public void perform(final ConfigurationContext context) {
      final RunnerAndConfigurationSettings configuration = context.getConfiguration();
      if (RunDialog.editConfiguration(context.getProject(), configuration, "Create " + configuration.getName()))
        super.perform(context);
    }
  }

  private static class SavePolicy extends BaseCreatePolicy {
    public SavePolicy() {
      super("Save");
    }

    public void perform(final ConfigurationContext context) {
      RunnerAndConfigurationSettings settings = context.findExisting();
      if (settings != null) context.getRunManager().makeStable(settings.getConfiguration());
    }
  }

  private static final BaseCreatePolicy CREATE_AND_EDIT = new CreateAndEditPolicy();
  private static final BaseCreatePolicy SELECT = new SelectPolicy();
  private static final BaseCreatePolicy SAVE = new SavePolicy();
  private static final BaseCreatePolicy SELECTED_STABLE = new BaseCreatePolicy("Select") {
    public void perform(final ConfigurationContext context) {}

    public void update(final Presentation presentation, final ConfigurationContext context, final String actionText) {
      super.update(presentation, context, actionText);
      presentation.setVisible(false);
    }
  };
}
