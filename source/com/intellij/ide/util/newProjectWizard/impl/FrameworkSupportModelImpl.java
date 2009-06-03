package com.intellij.ide.util.newProjectWizard.impl;

import com.intellij.ide.util.newProjectWizard.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class FrameworkSupportModelImpl extends UserDataHolderBase implements FrameworkSupportModel {
  private final Project myProject;
  private final EventDispatcher<FrameworkSupportModelListener> myDispatcher = EventDispatcher.create(FrameworkSupportModelListener.class);
  private final Map<String, AddSupportForFrameworksPanel.FrameworkSupportSettings> mySettingsMap = new HashMap<String, AddSupportForFrameworksPanel.FrameworkSupportSettings>();

  public FrameworkSupportModelImpl(final @Nullable Project project) {
    myProject = project;
  }

  public void registerComponent(@NotNull final FrameworkSupportProvider provider, @NotNull final AddSupportForFrameworksPanel.FrameworkSupportSettings settings) {
    mySettingsMap.put(provider.getId(), settings);
    final JCheckBox checkBox = settings.getCheckBox();
    checkBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        if (checkBox.isSelected()) {
          myDispatcher.getMulticaster().frameworkSelected(provider);
        }
        else {
          myDispatcher.getMulticaster().frameworkUnselected(provider);
        }
      }
    });
  }

  public Project getProject() {
    return myProject;
  }

  public boolean isFrameworkSelected(@NotNull @NonNls final String providerId) {
    final AddSupportForFrameworksPanel.FrameworkSupportSettings settings = mySettingsMap.get(providerId);
    return settings != null && settings.getCheckBox().isSelected();
  }

  public void addFrameworkListener(@NotNull final FrameworkSupportModelListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeFrameworkListener(@NotNull final FrameworkSupportModelListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void setFrameworkComponentEnabled(@NotNull @NonNls final String providerId, final boolean enable) {
    final AddSupportForFrameworksPanel.FrameworkSupportSettings settings = mySettingsMap.get(providerId);
    if (settings == null) {
      throw new IllegalArgumentException("provider '" + providerId + " not found");
    }
    if (enable != settings.getCheckBox().isEnabled()) {
      settings.setEnabled(enable);
    }
  }

  public FrameworkSupportConfigurable getFrameworkConfigurable(@NotNull @NonNls String providerId) {
    final AddSupportForFrameworksPanel.FrameworkSupportSettings settings = mySettingsMap.get(providerId);
    if (settings == null) {
      throw new IllegalArgumentException("provider '" + providerId + " not found");
    }
    return settings.getConfigurable();
  }
}
