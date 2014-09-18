/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.packaging.ui;

import com.google.common.collect.Sets;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.util.Consumer;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.InstalledPackagesPanel;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.IronPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyInstalledPackagesPanel extends InstalledPackagesPanel {
  public static final String INSTALL_MANAGEMENT = "installManagement";
  public static final String CREATE_VENV = "createVEnv";

  private boolean myHasManagement = false;

  public PyInstalledPackagesPanel(Project project, PackagesNotificationPanel area) {
    super(project, area);
  }

  private Sdk getSelectedSdk() {
    PyPackageManagementService service = (PyPackageManagementService)myPackageManagementService;
    return service != null ? service.getSdk() : null;
  }

  public void updateNotifications(@Nullable final Sdk selectedSdk) {
    if (selectedSdk == null) {
      myNotificationArea.hide();
      return;
    }
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        PyPackageManager packageManager = PyPackageManager.getInstance(selectedSdk);
        myHasManagement = packageManager.hasManagement(false);
        application.invokeLater(new Runnable() {
          @Override
          public void run() {
            if (selectedSdk == getSelectedSdk()) {
              final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(selectedSdk);
              final boolean invalid = PythonSdkType.isInvalid(selectedSdk);
              boolean allowCreateVirtualEnv =
                !(PythonSdkType.isRemote(selectedSdk) || flavor instanceof IronPythonSdkFlavor) &&
                !PythonSdkType.isVirtualEnv(selectedSdk) &&
                myNotificationArea.hasLinkHandler(CREATE_VENV);
              final String createVirtualEnvLink = "<a href=\"" + CREATE_VENV + "\">create new VirtualEnv</a>";
              myNotificationArea.hide();
              if (!invalid) {
                String text = null;
                if (!myHasManagement) {
                  myNotificationArea.addLinkHandler(INSTALL_MANAGEMENT,
                                                    new Runnable() {
                                                      @Override
                                                      public void run() {
                                                        final Sdk sdk = getSelectedSdk();
                                                        if (sdk != null) {
                                                          installManagementTools(sdk);
                                                        }
                                                        myNotificationArea.removeLinkHandler(INSTALL_MANAGEMENT);
                                                        updateNotifications(selectedSdk);
                                                      }
                                                    }
                  );
                }

                if (!myHasManagement) {
                  text = "Python packaging tools not found. <a href=\"" + INSTALL_MANAGEMENT + "\">Install packaging tools</a>";
                }
                if (text != null) {
                  if (allowCreateVirtualEnv) {
                    text += " or " + createVirtualEnvLink;
                  }
                  myNotificationArea.showWarning(text);
                }
              }

              myInstallButton.setEnabled(!invalid && myHasManagement);
            }
          }
        }, ModalityState.any());
      }
    });
  }

  @Override
  protected Set<String> getPackagesToPostpone() {
    return Sets.newHashSet("pip", "distutils", "setuptools");
  }

  private void installManagementTools(@NotNull final Sdk sdk) {
    final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, sdk, new PyPackageManagerUI.Listener() {
      @Override
      public void started() {
        myPackagesTable.setPaintBusy(true);
      }

      @Override
      public void finished(List<PyExternalProcessException> exceptions) {
        myPackagesTable.setPaintBusy(false);
        PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
        if (!exceptions.isEmpty()) {
          final String firstLine = "Install Python packaging tools failed. ";
          final String description = PyPackageManagerUI.createDescription(exceptions, firstLine);
          PackagesNotificationPanel.showError(myProject, "Failed to install Python packaging tools", description);
        }
        packageManager.refresh();
        updatePackages(new PyPackageManagementService(myProject, sdk));
        for (Consumer<Sdk> listener : myPathChangedListeners) {
          listener.consume(sdk);
        }
        updateNotifications(sdk);
      }
    });
    ui.installManagement();
  }

  @Override
  protected boolean canUninstallPackage(InstalledPackage pkg) {
    if (!myHasManagement) return false;
    if (PythonSdkType.isVirtualEnv(getSelectedSdk()) && pkg instanceof PyPackage) {
      final String location = ((PyPackage)pkg).getLocation();
      if (location != null && location.startsWith(PySdkUtil.getUserSite())) {
        return false;
      }
    }
    final String name = pkg.getName();
    if (PyPackageManager.PACKAGE_PIP.equals(name) ||
        PyPackageManager.PACKAGE_SETUPTOOLS.equals(name) ||
        PyPackageManager.PACKAGE_DISTRIBUTE.equals(name)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canUpgradePackage(InstalledPackage pyPackage) {
    return myHasManagement;
  }
}
