// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.SdkEditorAdditionalOptionsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.AdditionalDataConfigurable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.packaging.PyPackageManagers;
import com.jetbrains.python.packaging.ui.PyInstalledPackagesPanel;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PythonSdkEditorAdditionalOptionsProvider extends SdkEditorAdditionalOptionsProvider {
  protected PythonSdkEditorAdditionalOptionsProvider() {
    super(PythonSdkType.getInstance());
  }

  @Nullable
  @Override
  public AdditionalDataConfigurable createOptions(@NotNull Project project, @NotNull Sdk sdk) {
    return new PythonSdkOptionsAdditionalDataConfigurable(project);
  }

  private static final class PythonSdkOptionsAdditionalDataConfigurable implements AdditionalDataConfigurable {
    private final Project myProject;

    private Sdk mySdk;

    private PythonSdkOptionsAdditionalDataConfigurable(Project project) {
      myProject = project;
    }

    @Override
    public void setSdk(Sdk sdk) {
      mySdk = sdk;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      final PackagesNotificationPanel notificationsArea = new PackagesNotificationPanel();
      final JComponent notificationsComponent = notificationsArea.getComponent();

      JPanel panel = new JPanel(new BorderLayout());
      panel.add(notificationsComponent, BorderLayout.SOUTH);
      PyInstalledPackagesPanel packagesPanel = new PyInstalledPackagesPanel(myProject, notificationsArea);
      panel.add(packagesPanel, BorderLayout.CENTER);

      packagesPanel.updatePackages(PyPackageManagers.getInstance().getManagementService(myProject, mySdk));
      packagesPanel.updateNotifications(mySdk);
      return panel;
    }

    @Override
    public String getTabName() {
      return "Packages";
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public void apply() throws ConfigurationException {
    }

    @Override
    public void reset() {
    }
  }
}
