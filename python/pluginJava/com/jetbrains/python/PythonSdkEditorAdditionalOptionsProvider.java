/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author traff
 */
public class PythonSdkEditorAdditionalOptionsProvider extends SdkEditorAdditionalOptionsProvider {
  protected PythonSdkEditorAdditionalOptionsProvider() {
    super(PythonSdkType.getInstance());
  }

  @Nullable
  @Override
  public AdditionalDataConfigurable createOptions(@NotNull Project project, @NotNull Sdk sdk) {
    return new PythonSdkOptionsAdditionalDataConfigurable(project);
  }

  private static class PythonSdkOptionsAdditionalDataConfigurable implements AdditionalDataConfigurable {
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
