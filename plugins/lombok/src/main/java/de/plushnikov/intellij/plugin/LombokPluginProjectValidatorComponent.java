package de.plushnikov.intellij.plugin;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.options.AnnotationProcessorsConfigurable;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;

import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * Shows notifications about project setup issues, that make the plugin not working.
 * @author Alexej Kubarev
 */
public class LombokPluginProjectValidatorComponent extends AbstractProjectComponent {

  private Project project;

  public LombokPluginProjectValidatorComponent(Project project) {

    super(project);
    this.project = project;

  }

  @NotNull
  @Override
  public String getComponentName() {

    return "lombok.ProjectValidatiorComponent";
  }

  @Override
  public void projectOpened() {

    CompilerConfiguration config = CompilerConfiguration.getInstance(project);
    boolean enabled = config.isAnnotationProcessorsEnabled();

    if (!enabled) {
      NotificationGroup group = new NotificationGroup("Lombok Plugin", NotificationDisplayType.BALLOON, true);
      Notification notification = group.createNotification(LombokBundle.message("config.warn.annotation-processing.disabled.title", ""),
          LombokBundle.message("config.warn.annotation-processing.disabled.message", project.getName()),
          NotificationType.ERROR,
          new SettingsOpeningListener(project));

      Notifications.Bus.notify(notification);
    }
  }

  private static class SettingsOpeningListener extends NotificationListener.Adapter {

    private Project project;

    public SettingsOpeningListener(Project project) {
      this.project = project;
    }

    @Override
    protected void hyperlinkActivated(@NotNull final Notification notification, @NotNull final HyperlinkEvent e) {

      ShowSettingsUtil.getInstance()
          .showSettingsDialog(project, new AnnotationProcessorsConfigurable(project).getDisplayName());
    }
  }
}
