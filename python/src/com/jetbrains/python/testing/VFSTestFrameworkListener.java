package com.jetbrains.python.testing;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * User: catherine
 */
@State(
  name = "VFSTestFrameworkListener",
  storages = {
    @Storage(
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class VFSTestFrameworkListener implements ApplicationComponent, PersistentStateComponent<VFSTestFrameworkListener> {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.testing.VFSTestFrameworkListener");
  private static final String PYTESTSEARCHER = "pycharm/finders/find_pytest.py";
  private static final String NOSETESTSEARCHER = "pycharm/finders/find_nosetest.py";
  private static final String ATTESTSEARCHER = "pycharm/finders/find_attest.py";

  private static final MergingUpdateQueue myQueue = new MergingUpdateQueue("TestFrameworkChecker", 5000, true, null);

  public VFSTestFrameworkListener() {
    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          VirtualFile vFile = event.getFile();
          if (vFile == null) continue;
          String path = vFile.getUrl().toLowerCase();
          boolean containsNose = path.contains("nose");
          boolean containsPy = path.contains("py-1") || path.contains("pytest");
          boolean containsAt = path.contains("attest");
          if (!containsAt && !containsNose && !containsPy) continue;
          SDKLOOP:
          for (Sdk sdk : PythonSdkType.getAllSdks()) {
            for (String root : sdk.getRootProvider().getUrls(OrderRootType.CLASSES)) {
              if (vFile.getUrl().contains(root)) {
                if (containsNose) {
                  updateTestFrameworks(sdk.getHomePath(), NOSETESTSEARCHER, "nose");
                  break SDKLOOP;
                }
                else if (containsPy) {
                  updateTestFrameworks(sdk.getHomePath(), PYTESTSEARCHER, "pytest");
                  break SDKLOOP;
                } else {
                  updateTestFrameworks(sdk.getHomePath(), ATTESTSEARCHER, "attest");
                  break SDKLOOP;
                }
              }
            }
          }
        }
      }
    });
  }

  public void updateAllTestFrameworks(final String sdkHome) {
    updateTestFrameworks(sdkHome, PYTESTSEARCHER, "pytest");
    updateTestFrameworks(sdkHome, NOSETESTSEARCHER, "nose");
    updateTestFrameworks(sdkHome, ATTESTSEARCHER, "attest");
    myQueue.flush();
  }

  public void updateTestFrameworks(final String sdkHome, final String searcher, final String sdkType) {
    myQueue.queue(new Update(Pair.create(sdkHome, searcher)) {
      @Override
      public void run() {
        testInstalled(isTestFrameworkInstalled(sdkHome, searcher), sdkHome, sdkType);
      }
    });
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "VFSTestFrameworkListener";
  }

  public static boolean isTestFrameworkInstalled(String sdkHome, String searcher) {
    if (StringUtil.isEmptyOrSpaces(sdkHome)) {
      LOG.info("Searching test runner in empty sdkHome");
      return false;
    }
    final String formatter = new File(PythonHelpersLocator.getHelpersRoot(), searcher).getAbsolutePath();
    ProcessOutput
      output = PySdkUtil.getProcessOutput(new File(sdkHome).getParent(),
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

  public static VFSTestFrameworkListener getInstance() {
    return ServiceManager.getService(VFSTestFrameworkListener.class);
  }

  public Map<String, Boolean> SDK_TO_PYTEST = new HashMap<String, Boolean>();
  public Map <String, Boolean> SDK_TO_NOSETEST = new HashMap<String, Boolean>();
  public Map <String, Boolean> SDK_TO_ATTEST = new HashMap<String, Boolean>();

  @Override
  public VFSTestFrameworkListener getState() {
    return this;
  }

  @Override
  public void loadState(VFSTestFrameworkListener state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void pyTestInstalled(boolean installed, String sdkHome) {
    SDK_TO_PYTEST.put(sdkHome, installed);
  }

  public boolean isPyTestInstalled(final String sdkHome) {
    Boolean isInstalled = SDK_TO_PYTEST.get(sdkHome);
    if (isInstalled == null) {
      updateTestFrameworks(sdkHome, PYTESTSEARCHER, "pytest");
      return true;
    }
    return isInstalled;
  }

  public void noseTestInstalled(boolean installed, String sdkHome) {
    SDK_TO_NOSETEST.put(sdkHome, installed);
  }

  public boolean isNoseTestInstalled(final String sdkHome) {
    Boolean isInstalled = SDK_TO_NOSETEST.get(sdkHome);
    if (isInstalled == null) {
      updateTestFrameworks(sdkHome, NOSETESTSEARCHER, "nose");
      return true;
    }
    return isInstalled;
  }

  public void atTestInstalled(boolean installed, String sdkHome) {
    SDK_TO_ATTEST.put(sdkHome, installed);
  }

  public boolean isAtTestInstalled(final String sdkHome) {
    Boolean isInstalled = SDK_TO_ATTEST.get(sdkHome);
    if (isInstalled == null) {
      updateTestFrameworks(sdkHome, ATTESTSEARCHER, "attest");
      return true;
    }
    return isInstalled;
  }

  public void testInstalled(boolean installed, String sdkHome, String name) {
    if (name.equals("nose"))
      noseTestInstalled(installed, sdkHome);
    else if (name.equals("pytest"))
      pyTestInstalled(installed, sdkHome);
    else if (name.equals("attest"))
      atTestInstalled(installed, sdkHome);
  }
}
