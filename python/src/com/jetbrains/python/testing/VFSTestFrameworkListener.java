/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * User: catherine
 */
@State(
  name = "VFSTestFrameworkListener",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )}
)
public class VFSTestFrameworkListener implements ApplicationComponent, PersistentStateComponent<VFSTestFrameworkListener> {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.testing.VFSTestFrameworkListener");

  private static final MergingUpdateQueue myQueue = new MergingUpdateQueue("TestFrameworkChecker", 5000, true, null);

  public VFSTestFrameworkListener() {
    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (!(event.getFileSystem() instanceof LocalFileSystem) || event instanceof VFileContentChangeEvent)
            continue;
          final String path = event.getPath();
          boolean containsNose = path.contains(PyNames.NOSE_TEST);
          boolean containsPy = path.contains("py-1") || path.contains(PyNames.PY_TEST);
          boolean containsAt = path.contains(PyNames.AT_TEST);
          if (!containsAt && !containsNose && !containsPy) continue;
          for (Sdk sdk : PythonSdkType.getAllSdks()) {
            if (PySdkUtil.isRemote(sdk)) {
              continue;
            }
            for (String root : sdk.getRootProvider().getUrls(OrderRootType.CLASSES)) {
              if (path.contains(root)) {
                if (containsNose) {
                  updateTestFrameworks(sdk, PyNames.NOSE_TEST);
                  return;
                }
                else if (containsPy) {
                  updateTestFrameworks(sdk, PyNames.PY_TEST);
                  return;
                }
                else {
                  updateTestFrameworks(sdk, PyNames.AT_TEST);
                  return;
                }
              }
            }
          }
        }
      }
    });
  }

  public void updateAllTestFrameworks(final Sdk sdk) {
    updateTestFrameworks(sdk, PyNames.PY_TEST);
    updateTestFrameworks(sdk, PyNames.NOSE_TEST);
    updateTestFrameworks(sdk, PyNames.AT_TEST);
    myQueue.flush();
  }

  public void updateTestFrameworks(final Sdk sdk, final String testPackageName) {
    myQueue.queue(new Update(Pair.create(sdk, testPackageName)) {
      @Override
      public void run() {
        final Boolean installed = isTestFrameworkInstalled(sdk, testPackageName);
        if (installed != null)
          testInstalled(installed, sdk.getHomePath(), testPackageName);
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

  /**
   * @return null if we can't be sure
   */
  public static Boolean isTestFrameworkInstalled(Sdk sdk, String testPackageName) {
    if (sdk == null || StringUtil.isEmptyOrSpaces(sdk.getHomePath())) {
      LOG.info("Searching test runner in empty sdk");
      return null;
    }
    final PyPackageManagerImpl packageManager = (PyPackageManagerImpl)PyPackageManager.getInstance(sdk);
    try {
      return packageManager.findPackage(testPackageName) != null;
    }
    catch (PyExternalProcessException e) {
      LOG.info("Can't load package list " + e.getMessage());
    }
    return null;
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

  public boolean isPyTestInstalled(final Sdk sdk) {
    Boolean isInstalled = SDK_TO_PYTEST.get(sdk.getHomePath());
    if (isInstalled == null) {
      updateTestFrameworks(sdk, PyNames.PY_TEST);
      return true;
    }
    return isInstalled;
  }

  public void noseTestInstalled(boolean installed, String sdkHome) {
    SDK_TO_NOSETEST.put(sdkHome, installed);
  }

  public boolean isNoseTestInstalled(final Sdk sdk) {
    Boolean isInstalled = SDK_TO_NOSETEST.get(sdk.getHomePath());
    if (isInstalled == null) {
      updateTestFrameworks(sdk, PyNames.NOSE_TEST);
      return true;
    }
    return isInstalled;
  }

  public void atTestInstalled(boolean installed, String sdkHome) {
    SDK_TO_ATTEST.put(sdkHome, installed);
  }

  public boolean isAtTestInstalled(final Sdk sdk) {
    Boolean isInstalled = SDK_TO_ATTEST.get(sdk.getHomePath());
    if (isInstalled == null) {
      updateTestFrameworks(sdk, PyNames.AT_TEST);
      return true;
    }
    return isInstalled;
  }

  public void testInstalled(boolean installed, String sdkHome, String name) {
    if (name.equals(PyNames.NOSE_TEST))
      noseTestInstalled(installed, sdkHome);
    else if (name.equals(PyNames.PY_TEST))
      pyTestInstalled(installed, sdkHome);
    else if (name.equals(PyNames.AT_TEST))
      atTestInstalled(installed, sdkHome);
  }
}
