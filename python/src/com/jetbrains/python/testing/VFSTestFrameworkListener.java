package com.jetbrains.python.testing;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.SdkUtil;

import java.io.File;
import java.util.List;

/**
 * User: catherine
 */
public class VFSTestFrameworkListener extends VirtualFileAdapter {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.testing.VFSTestFrameworkListener");
  public static final String PYTESTSEARCHER = "pycharm/finders/find_pytest.py";
  public static final String NOSETESTSEARCHER = "pycharm/finders/find_nosetest.py";
  public static final String ATTESTSEARCHER = "pycharm/finders/find_attest.py";
  private TestRunnerService myService;

  public VFSTestFrameworkListener(Project project) {
    myService = TestRunnerService.getInstance(project);
    updateTestFrameworks(myService);
  }

  @Override
  public void fileCreated(VirtualFileEvent event) {
    updateTestFrameworks(myService);
  }

  @Override
  public void fileDeleted(VirtualFileEvent event) {
    updateTestFrameworks(myService);
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
    updateTestFrameworks(myService);
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

  public static void updateTestFrameworks(TestRunnerService service) {
    List<Sdk> sdks = PythonSdkType.getAllSdks();
    for (Sdk sdk : sdks) {
      String sdkHome = sdk.getHomePath();
      updateTestFrameworks(service, sdkHome);
    }
  }

  public static void updateTestFrameworks(TestRunnerService service, String sdkHome) {
    service.addSdk(sdkHome);
    service.pyTestInstalled(isTestFrameworkInstalled(sdkHome, PYTESTSEARCHER), sdkHome);
    service.noseTestInstalled(isTestFrameworkInstalled(sdkHome, NOSETESTSEARCHER), sdkHome);
    service.atTestInstalled(isTestFrameworkInstalled(sdkHome, ATTESTSEARCHER), sdkHome);
  }
}
