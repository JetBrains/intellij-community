// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

public class SetupProjectSdkCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "setupSDK";

  private final String mySdkName;
  private final String mySdkType;
  private final String mySdkHome;

  private static String nextArg(@NotNull Iterator<String> args, @NotNull String text) {
    if (!args.hasNext()) throw new RuntimeException("Too few arguments in " + text);
    return args.next();
  }

  public SetupProjectSdkCommand(String text, int line) {
    super(text, line);

    Iterator<String> args = StringUtil.splitHonorQuotes(text, ' ').stream().map(StringUtil::unquoteString).iterator();
    //the command name
    nextArg(args, text);

    mySdkName = nextArg(args, text);
    mySdkType = nextArg(args, text);
    mySdkHome = nextArg(args, text);
  }

  private void runUnderPromiseInEDT(@NotNull Consumer<String> logMessage,
                                    @NotNull Project project) {
    logMessage.accept("Settings up SDK: name: " + mySdkName + ", type: " + mySdkType + ", home: " + mySdkHome);
    Sdk sdk = setupOrDetectSdk(logMessage);

    ProjectRootManager rootManager = ProjectRootManager.getInstance(project);

    Sdk projectSdk = rootManager.getProjectSdk();
    if (!Objects.equals(projectSdk, sdk)) {
      logMessage.accept("Project uses different SDK: " + projectSdk + " " +
                        "(sdkName is " + rootManager.getProjectSdkName() + ", " +
                        "type " + rootManager.getProjectSdkTypeName() + "). Updating...");
      rootManager.setProjectSdk(sdk);
    }

    logMessage.accept("Project SDK is set to use the new SDK");

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
      if (!Objects.equals(moduleSdk, sdk)) {
        logMessage.accept("Module " + module.getName() + " uses different SDK: " + moduleSdk + " IGNORING!");
      }
    }
  }

  private @NotNull Sdk setupOrDetectSdk(@NotNull Consumer<String> logMessage) {
    Sdk oldSdk = ProjectJdkTable.getInstance().findJdk(mySdkName);
    if (oldSdk != null) {
      if (Objects.equals(oldSdk.getSdkType().getName(), mySdkName) && FileUtil.pathsEqual(oldSdk.getHomePath(), mySdkHome)) {
        logMessage.accept("Existing SDK is already configured the expected way");
        return oldSdk;
      }

      logMessage.accept("Existing different SDK will be removed: " + oldSdk);
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

    registerNewSdk(newSdk);
    logMessage.accept("Registered new SDK to ProjectJdkTable: " + newSdk);
    return newSdk;
  }

  protected void registerNewSdk(@NotNull Sdk newSdk) {
    ProjectJdkTable.getInstance().addJdk(newSdk);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    AsyncPromise<Object> promise = new AsyncPromise<>();

    ApplicationManager.getApplication().invokeLater(() -> {
      Promises.compute(promise, () -> {
        computePromise(s -> context.message(s, getLine()), context.getProject());
        return null;
      });
    });
    return promise;
  }

  public void computePromise(@NotNull Consumer<String> logMessage,
                             @NotNull Project project) {
    WriteAction.run(() -> {
      runUnderPromiseInEDT(logMessage, project);
    });
  }
}
