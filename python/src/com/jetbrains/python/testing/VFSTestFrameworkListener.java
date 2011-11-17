package com.jetbrains.python.testing;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.TransferToPooledThreadQueue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Processor;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.SdkUtil;

import java.io.File;
import java.util.List;

/**
 * User: catherine
 */
public class VFSTestFrameworkListener implements BulkFileListener {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.testing.VFSTestFrameworkListener");
  public static final String PYTESTSEARCHER = "pycharm/finders/find_pytest.py";
  public static final String NOSETESTSEARCHER = "pycharm/finders/find_nosetest.py";
  public static final String ATTESTSEARCHER = "pycharm/finders/find_attest.py";
  private static TestRunnerService ourService;
  private Project myProject;

  private static final TransferToPooledThreadQueue<List<String>> myTFChangePool = new TransferToPooledThreadQueue<List<String>>(
    "Checking test frameworks", new Processor<List<String>>() {
    @Override
    public boolean process(List<String> params) {
      String sdkHome = params.get(0);
      String searcher = params.get(1);
      String name = params.get(2);
      ourService.testInstalled(isTestFrameworkInstalled(sdkHome, searcher), sdkHome, name);
      return true;
    }
  }, ApplicationManager.getApplication().getDisposed(), -1); // drain the whole queue, do not reschedule

  public VFSTestFrameworkListener(Project project) {
    ourService = TestRunnerService.getInstance(project);
    myProject = project;
    updateTestFrameworks(ourService);
  }

  @Override
  public void before(List<? extends VFileEvent> events) {}

  @Override
  public void after(List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      VirtualFile vFile = event.getFile();
      if (vFile != null && ProjectRootManager.getInstance(myProject).getFileIndex().isInLibraryClasses(vFile)) {
        String path = vFile.getPath();
        if (path.contains("nose") || path.contains("py-1") || path.contains("pytest") || path.contains("attest")) {
          updateTestFrameworks(ourService);
          break;
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

  public static void updateTestFrameworks(TestRunnerService service) {
    List<Sdk> sdks = PythonSdkType.getAllSdks();
    for (Sdk sdk : sdks) {
      String sdkHome = sdk.getHomePath();
      updateTestFrameworks(service, sdkHome);
    }
  }

  public static void updateTestFrameworks(final TestRunnerService service, final String sdkHome) {
    service.addSdk(sdkHome);
    myTFChangePool.offer(Lists.newArrayList(sdkHome, PYTESTSEARCHER, "pytest"));
    myTFChangePool.offer(Lists.newArrayList(sdkHome, NOSETESTSEARCHER, "nosetest"));
    myTFChangePool.offer(Lists.newArrayList(sdkHome, ATTESTSEARCHER, "attest"));
  }
}
