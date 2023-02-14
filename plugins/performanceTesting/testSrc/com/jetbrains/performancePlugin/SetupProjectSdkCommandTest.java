package com.jetbrains.performancePlugin;

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.SystemProperties;
import com.jetbrains.performancePlugin.commands.SetupProjectSdkCommand;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;

public class SetupProjectSdkCommandTest extends HeavyPlatformTestCase {
  public void testSetupJavaHome() {
    String home = computeJavaHome();

    doCommand("mock-jdk-6" + System.currentTimeMillis(), SimpleJavaSdkType.getInstance(), home);

    Sdk sdk = ProjectRootManager.getInstance(getProject()).getProjectSdk();
    Assert.assertNotNull(sdk);
    Assert.assertTrue(FileUtil.pathsEqual(sdk.getHomePath(), home));
  }

  private void doCommand(@NotNull String sdkName, @NotNull SdkType type, @NotNull String home) {
    new SetupProjectSdkCommand("command-name " + sdkName + " " + type.getName() + " \"" + home + "\"", 1) {
      @Override
      protected void registerNewSdk(@NotNull Sdk newSdk) {
        ProjectJdkTable.getInstance().addJdk(newSdk, getTestRootDisposable());
      }
    }.computePromise(System.out::println, getProject());
  }

  @NotNull
  private static String computeJavaHome() {
    String home = SystemProperties.getJavaHome();
    if (home.endsWith("/jre")) home = new File(home).getParent();
    return home;
  }
}
