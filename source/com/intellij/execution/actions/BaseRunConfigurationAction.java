package com.intellij.execution.actions;

import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.RuntimeConfiguration;
import com.intellij.execution.applet.AppletConfiguration;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettings;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;

abstract class BaseRunConfigurationAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.actions.BaseRunConfigurationAction");

  protected BaseRunConfigurationAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final ConfigurationContext context = new ConfigurationContext(dataContext);
    final RunnerAndConfigurationSettings configuration = context.getConfiguration();
    if (configuration == null) return;
    perform(context);
  }

  protected abstract void perform(ConfigurationContext context);

  public void update(final AnActionEvent event){
    final ConfigurationContext context = new ConfigurationContext(event.getDataContext());
    final Presentation presentation = event.getPresentation();
    final RunnerAndConfigurationSettings configuration = context.getConfiguration();
    if (configuration == null){
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    else{
      presentation.setEnabled(true);
      presentation.setVisible(true);
      final String name = suggestRunActionName((RuntimeConfiguration)configuration.getConfiguration());
      updatePresentation(presentation, " " + name, context);
    }
  }

  public static String suggestRunActionName(final RuntimeConfiguration configuration) {
    final ConfigurationAccessor accessor = getAccessor(configuration);
    if (!accessor.isGeneratedName()) {
      return "\"" + ExecutionUtil.shortenName(configuration.getName(), 0) + "\"";
    } else return "\"" + accessor.suggestedName() + "\"";
  }

  protected abstract void updatePresentation(Presentation presentation, String actionText, ConfigurationContext context);

  private interface ConfigurationAccessor {
    boolean isGeneratedName();

    String suggestedName();
  }

  private static ConfigurationAccessor getAccessor(final RuntimeConfiguration configuration) {
    if (configuration instanceof ApplicationConfiguration) {
      final ApplicationConfiguration applicationConfiguration = ((ApplicationConfiguration)configuration);
      return new ConfigurationAccessor(){
        public boolean isGeneratedName() {
          return applicationConfiguration.isGeneratedName();
        }

        public String suggestedName() {
          return ExecutionUtil.shortenName(ExecutionUtil.getShortClassName(applicationConfiguration.MAIN_CLASS_NAME), 6) + ".main()";
        }
      };
    } else if (configuration instanceof JUnitConfiguration) {
      final JUnitConfiguration jUnitConfiguration = ((JUnitConfiguration)configuration);
      return new ConfigurationAccessor() {
        public boolean isGeneratedName() {
          return jUnitConfiguration.isGeneratedName();
        }

        public String suggestedName() {
          return jUnitConfiguration.getTestObject().suggestActionName();
        }
      };
    } else if (configuration instanceof AppletConfiguration) {
      final AppletConfiguration appletConfiguration = ((AppletConfiguration)configuration);
      return new ConfigurationAccessor() {
        public boolean isGeneratedName() {
          return appletConfiguration.isGeneratedName();
        }

        public String suggestedName() {
          return ExecutionUtil.shortenName(ExecutionUtil.getShortClassName(appletConfiguration.MAIN_CLASS_NAME), 0);
        }
      };
    } else return new ConfigurationAccessor() {
      public boolean isGeneratedName() {
        return false;
      }

      public String suggestedName() {
        LOG.error("Should not call");
        return "";
      }
    };

  }
}
