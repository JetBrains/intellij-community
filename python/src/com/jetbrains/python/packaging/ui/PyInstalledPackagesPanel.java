// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.ui;

import com.google.common.collect.Sets;
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
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.InstalledPackagesPanel;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.icons.PythonIcons;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.PyPackagesNotificationPanel;
import com.jetbrains.python.packaging.PyPackagingSettings;
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.jetbrains.python.SdkUiUtilKt.isVirtualEnv;


public class PyInstalledPackagesPanel extends InstalledPackagesPanel {
  static String PYTHON = "python";

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

  public void updateNotifications(final @Nullable Sdk selectedSdk) {
    if (selectedSdk == null) {
      myNotificationArea.hide();
      return;
    }
    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      application.invokeLater(() -> updateUninstallUpgrade(), ModalityState.any());

      application.invokeLater(() -> {
        if (selectedSdk == getSelectedSdk()) {
          myNotificationArea.hide();
          myInstallEnabled = installEnabled();
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
    final Sdk sdk = getSelectedSdk();
    if (sdk == null) return false;
    if (!PyPackageUtil.packageManagementEnabled(sdk, false, false)) return false;

    if (isVirtualEnv(sdk) && pkg instanceof PyPackage) {
      final String location = ((PyPackage)pkg).getLocation();
      if (location != null && location.startsWith(PythonSdkUtil.getUserSite())) {
        return false;
      }
    }
    final String name = pkg.getName();
    if (PyPackageUtil.PIP.equals(name) ||
        PyPackageUtil.SETUPTOOLS.equals(name) ||
        PyPackageUtil.DISTRIBUTE.equals(name) ||
        PYTHON.equals(name)) {
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
    return PyPackageUtil.packageManagementEnabled(getSelectedSdk(), false, false);
  }

  @Override
  protected boolean canUpgradePackage(InstalledPackage pyPackage) {
    if (!PyPackageUtil.packageManagementEnabled(getSelectedSdk(), false, false)) return false;

    return !PYTHON.equals(pyPackage.getName());
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
