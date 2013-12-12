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
package com.jetbrains.python.sdk;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * A component that initiates a refresh of all project's Python SDKs.
 * Delegates most of the work to PythonSdkType.
 * <br/>
 *
 * @author yole
 */
public class PythonSdkUpdater implements StartupActivity {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.PythonSdkUpdater");

  private final Set<String> myAlreadyUpdated = new HashSet<String>();

  public static PythonSdkUpdater getInstance() {
    final StartupActivity[] extensions = Extensions.getExtensions(StartupActivity.POST_STARTUP_ACTIVITY);
    for (StartupActivity extension : extensions) {
      if (extension instanceof PythonSdkUpdater) {
        return (PythonSdkUpdater)extension;
      }
    }
    throw new UnsupportedOperationException("could not find self");
  }

  public void markAlreadyUpdated(String path) {
    myAlreadyUpdated.add(path);
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }

    updateActiveSdks(project, 7000);
  }

  public void updateActiveSdks(@NotNull final Project project, final int delay) {
    final Set<Sdk> sdksToUpdate = new HashSet<Sdk>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null) {
        final SdkTypeId sdkType = sdk.getSdkType();
        if (sdkType instanceof PythonSdkType && !myAlreadyUpdated.contains(sdk.getHomePath())) {
          sdksToUpdate.add(sdk);
        }
      }
    }

    // NOTE: everything is run later on the AWT thread
    if (!sdksToUpdate.isEmpty()) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          if (delay > 0) {
            try {
              Thread.sleep(delay); // wait until all short-term disk-hitting activity ceases
            }
            catch (InterruptedException ignore) {
            }
          }
          // update skeletons
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              ProgressManager.getInstance().run(new Task.Backgroundable(project, PyBundle.message("sdk.gen.updating.skels"), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                  for (final Sdk sdk : sdksToUpdate) {
                    try {
                      LOG.info("Performing background update of skeletons for SDK " + sdk.getHomePath());
                      updateSdk(project, sdk);
                    }
                    catch (InvalidSdkException e) {
                      if (!PythonSdkType.isInvalid(sdk)) {
                        LOG.error(e);
                      }
                    }
                    myAlreadyUpdated.add(sdk.getHomePath());
                  }
                }
              });
            }
          });
        }
      });
    }
  }

  private static void updateSdk(@NotNull Project project, @NotNull final Sdk sdk) throws InvalidSdkException {
    PySkeletonRefresher.refreshSkeletonsOfSdk(project, sdk); // NOTE: whole thing would need a rename
    if (!PySdkUtil.isRemote(sdk)) {
      updateSysPath(sdk);
    }
  }

  private static void updateSysPath(final Sdk sdk) throws InvalidSdkException {
    long start_time = System.currentTimeMillis();
    final List<String> sysPath = PythonSdkType.getSysPath(sdk.getHomePath());
    final VirtualFile file = PyUserSkeletonsUtil.getUserSkeletonsDirectory();
    if (file != null) {
      sysPath.add(file.getPath());
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        updateSdkPath(sdk, sysPath);
      }
    });
    LOG.info("Updating sys.path took " + (System.currentTimeMillis() - start_time) + " ms");
  }

  private static void updateSdkPath(Sdk sdk, List<String> sysPath) {
    final List<VirtualFile> oldRoots = Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
    final VirtualFile[] sourceRoots = sdk.getRootProvider().getFiles(OrderRootType.SOURCES);
    PythonSdkAdditionalData additionalData = sdk.getSdkAdditionalData() instanceof PythonSdkAdditionalData
                                             ? (PythonSdkAdditionalData)sdk.getSdkAdditionalData()
                                             : null;
    List<String> newRoots = new ArrayList<String>();
    for (String root : sysPath) {
      if (new File(root).exists() &&
          !FileUtilRt.extensionEquals(root, "egg-info") &&
          (additionalData == null || !wasOldRoot(root, additionalData.getExcludedPathFiles())) &&
          !wasOldRoot(root, oldRoots)) {
        newRoots.add(root);
      }
    }
    if (!newRoots.isEmpty() || sourceRoots.length > 0) {
      final SdkModificator modificator = sdk.getSdkModificator();
      for (String root : newRoots) {
        PythonSdkType.addSdkRoot(modificator, root);
      }
      modificator.removeRoots(OrderRootType.SOURCES);
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          modificator.commitChanges();
        }
      });
    }
  }

  private static boolean wasOldRoot(String root, Collection<VirtualFile> virtualFiles) {
    String rootPath = canonicalize(root);
    for (VirtualFile virtualFile : virtualFiles) {
      if (canonicalize(virtualFile.getPath()).equals(rootPath)) {
        return true;
      }
    }
    return false;
  }

  private static String canonicalize(String path) {
    try {
      return new File(path).getCanonicalPath();
    }
    catch (IOException e) {
      return path;
    }
  }
}
