package com.jetbrains.python.testing;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.SdkUtil;

import java.io.File;

/**
 * User: catherine
 */
public class VFSTestFrameworkListener extends VirtualFileAdapter {
  public static final String PYTESTSEARCHER = "pycharm/finders/find_pytest.py";
  public static final String NOSETESTSEARCHER = "pycharm/finders/find_nosetest.py";
  public static final String ATTESTSEARCHER = "pycharm/finders/find_attest.py";
  private String mySdkHome;
  private TestRunnerService myService;

  public VFSTestFrameworkListener(Project project, String sdkHome) {
    mySdkHome = sdkHome;
    myService = TestRunnerService.getInstance(project);
    updateTestFrameworks(myService, mySdkHome);
  }

  @Override
  public void fileCreated(VirtualFileEvent event) {
    updateTestFrameworks(myService, mySdkHome);
  }

  @Override
  public void fileDeleted(VirtualFileEvent event) {
    updateTestFrameworks(myService, mySdkHome);
  }

  @Override
  public void fileMoved(VirtualFileMoveEvent event) {
    updateTestFrameworks(myService, mySdkHome);
  }

  public static boolean isTestFrameworkInstalled(String sdkHome, String searcher) {
    if (sdkHome == null || sdkHome.isEmpty()) return false;
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
      return false;
    }
    return true;
  }

  public static void updateTestFrameworks(TestRunnerService service, String sdkHome) {
    String testFrameWork = service.getProjectConfiguration();
    if (testFrameWork.equals(PythonTestConfigurationsModel.PY_TEST_NAME))
      service.pyTestInstalled(String.valueOf(isTestFrameworkInstalled(sdkHome, PYTESTSEARCHER)));
    else if (testFrameWork.equals(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME))
      service.noseTestInstalled(String.valueOf(isTestFrameworkInstalled(sdkHome, NOSETESTSEARCHER)));
    else if (testFrameWork.equals(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME))
      service.atTestInstalled(String.valueOf(isTestFrameworkInstalled(sdkHome, ATTESTSEARCHER)));
  }
}
