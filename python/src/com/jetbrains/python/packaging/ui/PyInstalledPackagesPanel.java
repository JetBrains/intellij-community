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
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.IronPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyInstalledPackagesPanel extends InstalledPackagesPanel {
  public static final String INSTALL_SETUPTOOLS = "installSetuptools";
  public static final String INSTALL_PIP = "installPip";
  public static final String CREATE_VENV = "createVEnv";

  private boolean myHasSetuptools;
  private boolean myHasPip = true;

  public PyInstalledPackagesPanel(Project project, PackagesNotificationPanel area) {
    super(project, area);

    myNotificationArea.addLinkHandler(INSTALL_SETUPTOOLS, new Runnable() {
      @Override
      public void run() {
        final Sdk sdk = getSelectedSdk();
        if (sdk != null) {
          installManagementTool(sdk, PyPackageManagerImpl.SETUPTOOLS);
        }
      }
    });
    myNotificationArea.addLinkHandler(INSTALL_PIP, new Runnable() {
      @Override
      public void run() {
        final Sdk sdk = getSelectedSdk();
        if (sdk != null) {
          installManagementTool(sdk, PyPackageManagerImpl.PIP);
        }
      }
    });
  }

  private Sdk getSelectedSdk() {
    PyPackageManagementService service = (PyPackageManagementService)myPackageManagementService;
    return service != null ? service.getSdk() : null;
  }

  public void updateNotifications(@NotNull final Sdk selectedSdk) {
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        PyExternalProcessException exc = null;
        try {
          PyPackageManagerImpl packageManager = (PyPackageManagerImpl)PyPackageManager.getInstance(selectedSdk);
          myHasSetuptools = packageManager.findPackage(PyPackageManagerImpl.PACKAGE_SETUPTOOLS) != null;
          if (!myHasSetuptools) {
            myHasSetuptools = packageManager.findPackage(PyPackageManagerImpl.PACKAGE_DISTRIBUTE) != null;
          }
          myHasPip = packageManager.findPackage(PyPackageManagerImpl.PACKAGE_PIP) != null;
        }
        catch (PyExternalProcessException e) {
          exc = e;
        }
        final PyExternalProcessException externalProcessException = exc;
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
                if (externalProcessException != null) {
                  final int retCode = externalProcessException.getRetcode();
                  if (retCode == PyPackageManagerImpl.ERROR_NO_PIP) {
                    myHasPip = false;
                  }
                  else if (retCode == PyPackageManagerImpl.ERROR_NO_SETUPTOOLS) {
                    myHasSetuptools = false;
                  }
                  else {
                    text = externalProcessException.getMessage();
                  }
                  final boolean hasPackagingTools = myHasPip && myHasSetuptools;
                  allowCreateVirtualEnv &= !hasPackagingTools;
                }
                if (text == null) {
                  if (!myHasSetuptools) {
                    text = "Python package management tools not found. <a href=\"" + INSTALL_SETUPTOOLS + "\">Install 'setuptools'</a>";
                  }
                  else if (!myHasPip) {
                    text = "Python packaging tool 'pip' not found. <a href=\"" + INSTALL_PIP + "\">Install 'pip'</a>";
                  }
                }
                if (text != null) {
                  if (allowCreateVirtualEnv) {
                    text += " or " + createVirtualEnvLink;
                  }
                  myNotificationArea.showWarning(text);
                }
              }

              myInstallButton.setEnabled(!invalid && externalProcessException == null && myHasPip);
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

  private void installManagementTool(@NotNull final Sdk sdk, final String name) {
    final PyPackageManagerImpl.UI ui = new PyPackageManagerImpl.UI(myProject, sdk, new PyPackageManagerImpl.UI.Listener() {
      @Override
      public void started() {
        myPackagesTable.setPaintBusy(true);
      }

      @Override
      public void finished(List<PyExternalProcessException> exceptions) {
        myPackagesTable.setPaintBusy(false);
        PyPackageManagerImpl packageManager = (PyPackageManagerImpl)PyPackageManager.getInstance(sdk);
        if (!exceptions.isEmpty()) {
          final String firstLine = "Install package failed. ";
          final String description = PyPackageManagerImpl.UI.createDescription(exceptions, firstLine);
          packageManager.showInstallationError(myProject, "Failed to install " + name, description);
        }
        packageManager.refresh();
        updatePackages(new PyPackageManagementService(myProject, sdk));
        for (Consumer<Sdk> listener : myPathChangedListeners) {
          listener.consume(sdk);
        }
        updateNotifications(sdk);
      }
    });
    ui.installManagement(name);
  }

  @Override
  protected boolean canUninstallPackage(InstalledPackage pkg) {
    if (!myHasPip) return false;
    if (PythonSdkType.isVirtualEnv(getSelectedSdk()) && pkg instanceof PyPackage) {
      final String location = ((PyPackage) pkg).getLocation();
      if (location != null && location.startsWith(PyPackageManagerImpl.getUserSite())) {
        return false;
      }
    }
    final String name = pkg.getName();
    if (PyPackageManagerImpl.PACKAGE_PIP.equals(name) ||
        PyPackageManagerImpl.PACKAGE_SETUPTOOLS.equals(name) ||
        PyPackageManagerImpl.PACKAGE_DISTRIBUTE.equals(name)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canUpgradePackage(InstalledPackage pyPackage) {
    return myHasPip;
  }
}
