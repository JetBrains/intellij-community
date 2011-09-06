package com.jetbrains.python.testing;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;

/**
 * User: catherine
 */
public class PyTestFrameworksUtil {
  private static VFSTestFrameworkListener ourListener;

  public static boolean isPyTestInstalled(Project project, String sdkHome) {
    if (ourListener == null) {
      ourListener = new VFSTestFrameworkListener(project, sdkHome);
      LocalFileSystem.getInstance().addVirtualFileListener(ourListener);
    }
    return Boolean.parseBoolean(TestRunnerService.getInstance(project).isPyTestInstalled());
  }

  public static boolean isNoseTestInstalled(Project project, String sdkHome) {
    if (ourListener == null) {
      ourListener = new VFSTestFrameworkListener(project, sdkHome);
      LocalFileSystem.getInstance().addVirtualFileListener(ourListener);
    }
    return Boolean.parseBoolean(TestRunnerService.getInstance(project).isNoseTestInstalled());
  }

  public static boolean isAtTestInstalled(Project project, String sdkHome) {
    if (ourListener == null) {
      ourListener = new VFSTestFrameworkListener(project, sdkHome);
      LocalFileSystem.getInstance().addVirtualFileListener(ourListener);
    }
    return Boolean.parseBoolean(TestRunnerService.getInstance(project).isAtTestInstalled());
  }
}
