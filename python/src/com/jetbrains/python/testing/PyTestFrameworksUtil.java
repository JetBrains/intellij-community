package com.jetbrains.python.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;

/**
 * User: catherine
 */
public class PyTestFrameworksUtil {
  private static VFSTestFrameworkListener ourListener;

  public static boolean isPyTestInstalled(Project project, String sdkHome) {
    if (ourListener == null) {
      ourListener = new VFSTestFrameworkListener(project);
      final MessageBusConnection myBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
      myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, ourListener);
    }
    TestRunnerService service = TestRunnerService.getInstance(project);
    if (!service.getSdks().contains(sdkHome))
      VFSTestFrameworkListener.updateTestFrameworks(service, sdkHome);

    return service.isPyTestInstalled(sdkHome);
  }

  public static boolean isNoseTestInstalled(Project project, String sdkHome) {
    if (ourListener == null) {
      ourListener = new VFSTestFrameworkListener(project);
      final MessageBusConnection myBusConnection = project.getMessageBus().connect();
      myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, ourListener);
    }
    TestRunnerService service = TestRunnerService.getInstance(project);
    if (!service.getSdks().contains(sdkHome))
      VFSTestFrameworkListener.updateTestFrameworks(service, sdkHome);

    return service.isNoseTestInstalled(sdkHome);
  }

  public static boolean isAtTestInstalled(Project project, String sdkHome) {
    if (ourListener == null) {
      ourListener = new VFSTestFrameworkListener(project);
      final MessageBusConnection myBusConnection = project.getMessageBus().connect();
      myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, ourListener);
    }
    TestRunnerService service = TestRunnerService.getInstance(project);
    if (!service.getSdks().contains(sdkHome))
      VFSTestFrameworkListener.updateTestFrameworks(service, sdkHome);

    return service.isAtTestInstalled(sdkHome);
  }
}
