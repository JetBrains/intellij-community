package com.jetbrains.python.testing;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.SdkUtil;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 */
public class VFSTestFrameworkListener implements BulkFileListener {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.testing.VFSTestFrameworkListener");
  public static final String PYTESTSEARCHER = "pycharm/finders/find_pytest.py";
  public static final String NOSETESTSEARCHER = "pycharm/finders/find_nosetest.py";
  public static final String ATTESTSEARCHER = "pycharm/finders/find_attest.py";
  private static TestFrameworkService ourService;

  private static final MergingUpdateQueue myQueue = new MergingUpdateQueue("TestFrameworkChecker", 5000, true, null);

  public VFSTestFrameworkListener() {
    ourService = TestFrameworkService.getInstance();
    updateTestFrameworks(ourService);
  }

  @Override
  public void before(List<? extends VFileEvent> events) {}

  @Override
  public void after(List<? extends VFileEvent> events) {
  EVENTSLOOP:
    for (VFileEvent event : events) {
      VirtualFile vFile = event.getFile();
      Set<String> sdks = ourService.getSdks();
      Set <String> roots = new HashSet<String>();
      for (String sdk : sdks) {
        Sdk realSdk = PythonSdkType.findSdkByPath(sdk);
        if (realSdk != null)
          roots.addAll(Lists.newArrayList(realSdk.getRootProvider().getUrls(OrderRootType.CLASSES)));
      }
      for (String root :roots) {
        if (vFile != null && vFile.getUrl().contains(root)) {
          String path = vFile.getUrl().toLowerCase();
          if (path.contains("nose") || path.contains("py-1") || path.contains("pytest") || path.contains("attest")) {
            updateTestFrameworks(ourService);
            break EVENTSLOOP;
          }
        }
      }
    }

  }

  public static boolean isTestFrameworkInstalled(String sdkHome, String searcher) {
    if (StringUtil.isEmptyOrSpaces(sdkHome)) {
      LOG.info("Searching test runner in empty sdkHome");
      return false;
    }
    final String formatter = new File(PythonHelpersLocator.getHelpersRoot(), searcher).getAbsolutePath();
    ProcessOutput
      output = SdkUtil.getProcessOutput(new File(sdkHome).getParent(),
                                        new String[]{
                                          sdkHome,
                                          formatter
                                        },
                                        null,
                                        2000);
    if (output.getExitCode() != 0 || !output.getStderr().isEmpty()) {
      LOG.info("Cannot find test runner in " + sdkHome + ". Use searcher " + formatter + ".\nGot exit code: " + output.getExitCode() +
      ".\nError output: " + output.getStderr());
      return false;
    }
    return true;
  }

  public void updateTestFrameworks(TestFrameworkService service) {
    List<Sdk> sdks = PythonSdkType.getAllSdks();
    for (Sdk sdk : sdks) {
      String sdkHome = sdk.getHomePath();
      updateTestFrameworks(service, sdkHome);
    }
  }

  public void updateTestFrameworks(final TestFrameworkService service, final String sdkHome) {
    service.addSdk(sdkHome);
    myQueue.queue(new Update(Pair.create(sdkHome, PYTESTSEARCHER)) {
      public void run() {
        ourService.testInstalled(isTestFrameworkInstalled(sdkHome, PYTESTSEARCHER), sdkHome, "pytest");
      }
    });
    myQueue.queue(new Update(Pair.create(sdkHome, NOSETESTSEARCHER)) {
      public void run() {
        ourService.testInstalled(isTestFrameworkInstalled(sdkHome, NOSETESTSEARCHER), sdkHome, "nosetest");
      }
    });
    myQueue.queue(new Update(Pair.create(sdkHome, ATTESTSEARCHER)) {
      public void run() {
        ourService.testInstalled(isTestFrameworkInstalled(sdkHome, ATTESTSEARCHER), sdkHome, "attest");
      }
    });
    myQueue.flush();
  }
}
