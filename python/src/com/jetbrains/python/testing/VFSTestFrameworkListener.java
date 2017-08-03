/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VFSTestFrameworkListener {
  private static final Logger LOG = Logger.getInstance(VFSTestFrameworkListener.class);

  private final AtomicBoolean myIsUpdating = new AtomicBoolean(false);
  private final PyTestFrameworkService myService = PyTestFrameworkService.getInstance();
  private final MergingUpdateQueue myQueue;

  public static VFSTestFrameworkListener getInstance() {
    return ApplicationManager.getApplication().getComponent(VFSTestFrameworkListener.class);
  }

  public VFSTestFrameworkListener() {
    final Application application = ApplicationManager.getApplication();
    final MessageBus messageBus = application.getMessageBus();
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (!(event.getFileSystem() instanceof LocalFileSystem) || event instanceof VFileContentChangeEvent) {
            continue;
          }
          final String path = event.getPath();

          final Set<String> existingFrameworks = new HashSet<>();
          for (final String framework : PyTestFrameworkService.getFrameworkNamesSet()) {
            if (path.contains(framework)) {
              existingFrameworks.add(framework);
            }
            if (path.contains("py-1")) {
              existingFrameworks.add(PyNames.PY_TEST);
            }
          }


          if (existingFrameworks.isEmpty()) {
            continue;
          }
          for (Sdk sdk : PythonSdkType.getAllSdks()) {
            if (PySdkUtil.isRemote(sdk)) {
              continue;
            }
            for (VirtualFile virtualFile : sdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
              final String root = virtualFile.getCanonicalPath();
              if (root != null && path.contains(root)) {
                final String framework = existingFrameworks.iterator().next();
                scheduleTestFrameworkCheck(sdk, framework);
                return;
              }
            }
          }
        }
      }
    });
    myQueue = new MergingUpdateQueue("TestFrameworkChecker", 5000, true, null, application, null, Alarm.ThreadToUse.POOLED_THREAD);
  }

  public void updateAllTestFrameworks(@NotNull Sdk sdk) {
    final Map<String, Boolean> whichInstalled = checkTestFrameworksInstalled(sdk, PyTestFrameworkService.getFrameworkNamesArray());
    ApplicationManager.getApplication().invokeLater(() -> {
      for (Map.Entry<String, Boolean> entry : whichInstalled.entrySet()) {
        final Boolean installed = entry.getValue();
        if (installed != null) {
          //noinspection ConstantConditions
          setTestFrameworkInstalled(installed, sdk.getHomePath(), entry.getKey());
        }
      }
    });
  }

  private void scheduleTestFrameworkCheck(@NotNull Sdk sdk, @NotNull String testPackageName) {
    myQueue.queue(new Update(Pair.create(sdk, testPackageName)) {
      @Override
      public void run() {
        checkFrameworkInstalledAndUpdateSettings(sdk, testPackageName);
      }
    });
  }

  private void checkFrameworkInstalledAndUpdateSettings(@Nullable Sdk sdk, @NotNull String testPackageName) {
    final Boolean installed = checkTestFrameworkInstalled(sdk, testPackageName);
    if (installed != null) {
      //noinspection ConstantConditions
      ApplicationManager.getApplication().invokeLater(() -> setTestFrameworkInstalled(installed, sdk.getHomePath(), testPackageName));
    }
  }

  /**
   * @return null if we can't be sure
   */
  @Contract("null, _ -> null")
  private Boolean checkTestFrameworkInstalled(@Nullable Sdk sdk, @NotNull String testFrameworkName) {
    return checkTestFrameworksInstalled(sdk, testFrameworkName).get(testFrameworkName);
  }

  @NotNull
  private Map<String, Boolean> checkTestFrameworksInstalled(@Nullable Sdk sdk, @NotNull String... testFrameworkNames) {
    final Map<String, Boolean> result = new HashMap<>();
    if (sdk == null || StringUtil.isEmptyOrSpaces(sdk.getHomePath())) {
      LOG.info("Searching test runner in empty sdk");
      return result;
    }
    final PyPackageManager manager = PyPackageManager.getInstance(sdk);
    final boolean refreshed = PyPackageUtil.updatePackagesSynchronouslyWithGuard(manager, myIsUpdating);
    if (refreshed) {
      final List<PyPackage> packages = manager.getPackages();
      if (packages != null) {
        for (final String frameworkName : testFrameworkNames) {
          final String packageName = PyTestFrameworkService.getPackageByFramework(frameworkName);
          result.put(frameworkName, PyPackageUtil.findPackage(packages, packageName) != null);
        }
      }
    }
    return result;
  }

  public boolean isTestFrameworkInstalled(@Nullable final Sdk sdk, @NotNull final String name) {
    if (sdk == null) {
      return false;
    }
    final Boolean isInstalled = myService.getSdkToTestRunnerByName(name).get(sdk.getHomePath());
    if (isInstalled == null) {
      scheduleTestFrameworkCheck(sdk, name);
      return true;
    }
    return isInstalled;
  }


  public final void setTestFrameworkInstalled(boolean installed, @NotNull String sdkHome, @NotNull String name) {
    myService.getSdkToTestRunnerByName(name).put(sdkHome, installed);
  }
}
