// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.ui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.InstalledPackagesPanel;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.icons.PythonIcons;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;


public class PyInstalledPackagesPanel extends InstalledPackagesPanel {
  private volatile boolean myHasManagement = false;

  public PyInstalledPackagesPanel(@NotNull Project project, @NotNull PackagesNotificationPanel area) {
    super(project, area);
  }

  public void setShowGrid(boolean v) {
    myPackagesTable.setShowGrid(v);
  }

  private Sdk getSelectedSdk() {
    PyPackageManagementService service = (PyPackageManagementService)myPackageManagementService;
    return service != null ? service.getSdk() : null;
  }

  class PyInstallPackageManagementFix implements PyExecutionFix {
    @Override
    public @NotNull String getName() {
      return PyBundle.message("python.packaging.install.packaging.tools");
    }

    @Override
    public void run(final @NotNull Sdk sdk) {
      final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, sdk, new PyPackageManagerUI.Listener() {
        @Override
        public void started() {
          myPackagesTable.setPaintBusy(true);
        }

        @Override
        public void finished(List<ExecutionException> exceptions) {
          myPackagesTable.setPaintBusy(false);
          PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
          final PyPackageManagementService.PyPackageInstallationErrorDescription description =
            PyPackageManagementService.toErrorDescription(exceptions, sdk, "packaging tools");
          if (description != null) {
            PyPackagesNotificationPanel.showPackageInstallationError(
              PyBundle.message("python.packaging.failed.to.install.packaging.tools.title"), description);
          }
          packageManager.refresh();
          updatePackages(PyPackageManagers.getInstance().getManagementService(myProject, sdk));
          updateNotifications(sdk);
        }
      });
      ui.installManagement();
    }
  }

  public void updateNotifications(final @Nullable Sdk selectedSdk) {
    if (selectedSdk == null) {
      myNotificationArea.hide();
      return;
    }
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      PyExecutionException exception = null;
      try {
        myHasManagement = PyPackageManager.getInstance(selectedSdk).hasManagement();
        application.invokeLater(() -> updateUninstallUpgrade(), ModalityState.any());
        if (!myHasManagement) {
          throw new PyExecutionException(PySdkBundle.message("python.sdk.packaging.tools.not.found"), "pip", Collections.emptyList(), "",
                                         "", 0,
                                         ImmutableList.of(new PyInstallPackageManagementFix()));
        }
      }
      catch (PyExecutionException e) {
        exception = e;
      }
      catch (ExecutionException e) {
        return;
      }
      final PyExecutionException problem = exception;
      application.invokeLater(() -> {
        if (selectedSdk == getSelectedSdk()) {
          myNotificationArea.hide();
          if (problem != null) {
            final boolean invalid = !PySdkExtKt.getSdkSeemsValid(selectedSdk);
            if (!invalid) {
              HtmlBuilder builder = new HtmlBuilder();
              builder.append(problem.getMessage()).append(". ");
              for (final PyExecutionFix fix : problem.getFixes()) {
                String key = "id" + fix.hashCode();
                builder.appendLink(key, fix.getName());
                builder.append(" ");
                myNotificationArea.addLinkHandler(key, () -> {
                  final Sdk sdk = getSelectedSdk();
                  if (sdk != null) {
                    fix.run(sdk);
                    myNotificationArea.removeLinkHandler(key);
                    updatePackages(PyPackageManagers.getInstance().getManagementService(myProject, sdk));
                    updateNotifications(sdk);
                  }
                });
              }
              myNotificationArea.showWarning(builder.toString());
            }
            myInstallEnabled = !invalid && installEnabled();
          }
        }
      }, ModalityState.any());
    });
  }

  @Override
  protected Set<String> getPackagesToPostpone() {
    return Sets.newHashSet("pip", "distutils", "setuptools");
  }

  @Override
  protected boolean canUninstallPackage(InstalledPackage pkg) {
    if (!myHasManagement) return false;

    final Sdk sdk = getSelectedSdk();
    if (sdk == null) return false;
    if (!PyPackageUtil.packageManagementEnabled(sdk, false, false)) return false;

    if (PythonSdkUtil.isVirtualEnv(sdk) && pkg instanceof PyPackage) {
      final String location = ((PyPackage)pkg).getLocation();
      if (location != null && location.startsWith(PythonSdkUtil.getUserSite())) {
        return false;
      }
    }
    final String name = pkg.getName();
    if (PyPackageUtil.PIP.equals(name) ||
        PyPackageUtil.SETUPTOOLS.equals(name) ||
        PyPackageUtil.DISTRIBUTE.equals(name) ||
        PyCondaPackageManagerImpl.PYTHON.equals(name)) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canInstallPackage(final @NotNull InstalledPackage pyPackage) {
    return installEnabled();
  }

  @Override
  protected boolean installEnabled() {
    if (!PyPackageUtil.packageManagementEnabled(getSelectedSdk(), false, false)) return false;

    return myHasManagement;
  }

  @Override
  protected boolean canUpgradePackage(InstalledPackage pyPackage) {
    if (!PyPackageUtil.packageManagementEnabled(getSelectedSdk(), false, false)) return false;

    return myHasManagement && !PyCondaPackageManagerImpl.PYTHON.equals(pyPackage.getName());
  }

  @Override
  protected AnAction @NotNull [] getExtraActions() {
    AnAction useCondaButton = new DumbAwareToggleAction(
      PyBundle.messagePointer("action.AnActionButton.text.use.conda.package.manager"),
      Presentation.NULL_STRING, PythonIcons.Python.Anaconda) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        final Sdk sdk = getSelectedSdk();
        if (myPackageManagementService instanceof PythonPackageManagementServiceBridge bridge) {
          return sdk != null && bridge.isConda() && bridge.getUseConda();
        }
        return false;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        final Sdk sdk = getSelectedSdk();
        if (sdk == null || !(myPackageManagementService instanceof PythonPackageManagementServiceBridge bridge)) return;
        if (bridge.isConda()) {
          bridge.setUseConda(state);
        }
        updatePackages(myPackageManagementService);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Sdk sdk = getSelectedSdk();
        e.getPresentation().setVisible(
          sdk != null && myPackageManagementService instanceof PythonPackageManagementServiceBridge bridge &&
          bridge.isConda());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };

    AnAction showEarlyReleasesButton = new DumbAwareToggleAction(
      PyBundle.messagePointer("action.AnActionButton.text.show.early.releases"),
      Presentation.NULL_STRING, AllIcons.Actions.Show) {
      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return PyPackagingSettings.getInstance(myProject).earlyReleasesAsUpgrades;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PyPackagingSettings.getInstance(myProject).earlyReleasesAsUpgrades = state;
        updatePackages(myPackageManagementService);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }
    };

    return new AnAction[]{useCondaButton, showEarlyReleasesButton};
  }

  @Override
  protected @NotNull PackagesNotificationPanel createNotificationPanel() {
    return new PyPackagesNotificationPanel();
  }
}
