package com.jetbrains.python.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBusConnection;

/**
 * User: catherine
 */
public class PyTestFrameworksUtil {
  private static VFSTestFrameworkListener ourListener;

  public static boolean isPyTestInstalled(String sdkHome) {
    TestFrameworkService service = TestFrameworkService.getInstance();
    if (!service.getSdks().contains(sdkHome))
      getListener().updateTestFrameworks(service, sdkHome);

    return service.isPyTestInstalled(sdkHome);
  }

  public static boolean isNoseTestInstalled(String sdkHome) {
    TestFrameworkService service = TestFrameworkService.getInstance();
    if (!service.getSdks().contains(sdkHome))
      getListener().updateTestFrameworks(service, sdkHome);

    return service.isNoseTestInstalled(sdkHome);
  }

  public static boolean isAtTestInstalled(String sdkHome) {
    TestFrameworkService service = TestFrameworkService.getInstance();
    if (!service.getSdks().contains(sdkHome))
      getListener().updateTestFrameworks(service, sdkHome);

    return service.isAtTestInstalled(sdkHome);
  }

  public static VFSTestFrameworkListener getListener() {
    if (ourListener == null) {
      ourListener = new VFSTestFrameworkListener();
      final MessageBusConnection myBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
      myBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, ourListener);
    }
    return ourListener;
  }
}
