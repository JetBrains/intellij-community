// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Objects;

@SuppressWarnings("unused")
@VisibleForTesting
public final class SetupProjectSdkUtil {
  private static final Logger LOG = Logger.getInstance(SetupProjectSdkUtil.class);

  @VisibleForTesting
  public static Sdk setupOrDetectSdk(@NotNull String name,
                                     @NotNull String type,
                                     @NotNull String home) {
    Ref<Sdk> sdkRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      WriteAction.run(() -> {
        Sdk sdk = setup(name, type, home);
        sdkRef.set(sdk);
      });
    });
    return sdkRef.get();
  }

  @VisibleForTesting
  public static boolean isApplicationLoaded() {
    return LoadingState.APP_STARTED.isOccurred();
  }

  @VisibleForTesting
  public static void setupOrDetectSdk(@NotNull Project project,
                                      @NotNull String name,
                                      @NotNull String type,
                                      @NotNull String home) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      WriteAction.run(() -> {
        Sdk sdk = setup(name, type, home);

        ProjectRootManager rootManager = ProjectRootManager.getInstance(project);

        Sdk projectSdk = rootManager.getProjectSdk();
        if (!Objects.equals(projectSdk, sdk)) {
          LOG.info("Project uses different SDK: " + projectSdk + " " +
                   "(sdkName is " + rootManager.getProjectSdkName() + ", " +
                   "type " + rootManager.getProjectSdkTypeName() + "). Updating...");
          rootManager.setProjectSdk(sdk);
        }

        LOG.info("Project SDK is set to use the new SDK");

        for (Module module : ModuleManager.getInstance(project).getModules()) {
          Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
          if (!Objects.equals(moduleSdk, sdk)) {
            LOG.info("Module " + module.getName() + " uses different SDK: " + moduleSdk + " IGNORING!");
          }
        }
      });
    });
  }

  private static @NotNull Sdk setup(String mySdkName, String mySdkType, String mySdkHome) {
    Sdk oldSdk = ProjectJdkTable.getInstance().findJdk(mySdkName);
    if (oldSdk != null) {
      if (Objects.equals(oldSdk.getSdkType().getName(), mySdkName) && FileUtil.pathsEqual(oldSdk.getHomePath(), mySdkHome)) {
        LOG.info("Existing SDK is already configured the expected way");
        return oldSdk;
      }

      LOG.info("Existing different SDK will be removed: " + oldSdk);
      ProjectJdkTable.getInstance().removeJdk(oldSdk);
    }

    SdkType sdkType = SdkType.findByName(mySdkType);
    if (sdkType == null) {
      throw new IllegalArgumentException("Failed to find SdkType: " + mySdkType);
    }

    boolean isValidSdkHome;
    try {
      isValidSdkHome = sdkType.isValidSdkHome(mySdkHome);
    }
    catch (Throwable t) {
      throw new IllegalArgumentException("Sdk home " + mySdkHome + " for " + sdkType + " is not valid. " + t.getMessage(), t);
    }

    if (!isValidSdkHome) {
      throw new IllegalArgumentException("Sdk home " + mySdkHome + " for " + sdkType + " is not valid");
    }

    Sdk newSdk = ProjectJdkTable.getInstance().createSdk(mySdkName, sdkType);
    SdkModificator mod = newSdk.getSdkModificator();
    try {
      mod.setVersionString(sdkType.getVersionString(mySdkHome));
      mod.setHomePath(mySdkHome);
    }
    catch (Throwable t) {
      throw new IllegalArgumentException(
        "Failed to configure Sdk instance home for " + mySdkHome + " for " + sdkType + " is not valid. " + t.getMessage(), t);
    }
    finally {
      mod.commitChanges();
    }

    try {
      sdkType.setupSdkPaths(newSdk);
    }
    catch (Throwable t) {
      throw new IllegalArgumentException(
        "Failed to setup Sdk home for " + mySdkHome + " for " + sdkType + " is not valid. " + t.getMessage(), t);
    }

    ProjectJdkTable.getInstance().addJdk(newSdk);
    LOG.info("Registered new SDK to ProjectJdkTable: " + newSdk);
    return newSdk;
  }
}
