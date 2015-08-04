/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

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
      updateSdks(project, delay, sdksToUpdate);
    }
  }

  private void updateSdks(final Project project, final int delay, final Set<Sdk> sdksToUpdate) {
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
                    updateSdk(project, null, sdk, PythonSdkType.findSkeletonsPath(sdk));
                  }
                  catch (InvalidSdkException e) {
                    if (PythonSdkType.isVagrant(sdk)) {
                      PythonSdkType.notifyRemoteSdkSkeletonsFail(e, new Runnable() {
                        @Override
                        public void run() {
                          updateSdks(project, delay, Sets.newHashSet(sdk));
                        }
                      });
                    }
                    else if (!PythonSdkType.isInvalid(sdk)) {
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

  public static void updateSdk(@Nullable Project project, @Nullable Component ownerComponent, @NotNull final Sdk sdk, String skeletonsPath) throws InvalidSdkException {
    PySkeletonRefresher.refreshSkeletonsOfSdk(project, ownerComponent, skeletonsPath, sdk); // NOTE: whole thing would need a rename
    if (!PySdkUtil.isRemote(sdk)) {
      updateSysPath(sdk);
    }
    else {
      PyRemoteSdkAdditionalDataBase remoteSdkData = (PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData();
      assert remoteSdkData != null;
      final List<String> paths = Lists.newArrayList();
      for (PathMappingSettings.PathMapping mapping : remoteSdkData.getPathMappings().getPathMappings()) {
        paths.add(mapping.getLocalRoot());
      }

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          updateSdkPath(sdk, paths);
        }
      });
    }
  }

  private static void updateSysPath(@NotNull final Sdk sdk) throws InvalidSdkException {
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

  /**
   * Updates SDK based on sys.path and cleans legacy information up.
   */
  private static void updateSdkPath(@NotNull Sdk sdk, @NotNull List<String> sysPath) {
    final SdkModificator modificator = sdk.getSdkModificator();
    boolean changed = addNewSysPathEntries(sdk, modificator, sysPath);
    changed = removeSourceRoots(sdk, modificator) || changed;
    changed = removeDuplicateClassRoots(sdk, modificator) || changed;
    changed = updateSkeletonsPath(sdk, modificator) || changed;
    if (changed) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          modificator.commitChanges();
        }
      });
    }
  }

  /**
   * Adds new CLASSES entries found in sys.path.
   */
  private static boolean addNewSysPathEntries(@NotNull Sdk sdk, @NotNull SdkModificator modificator, @NotNull List<String> sysPath) {
    final List<VirtualFile> oldRoots = Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
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
    if (!newRoots.isEmpty()) {
      for (String root : newRoots) {
        PythonSdkType.addSdkRoot(modificator, root);
      }
      return true;
    }
    return false;
  }

  /**
   * Removes duplicate roots that have been added as the result of a bug with *.egg handling.
   */
  private static boolean removeDuplicateClassRoots(@NotNull Sdk sdk, @NotNull SdkModificator modificator) {
    final List<VirtualFile> sourceRoots = Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
    final LinkedHashSet<VirtualFile> uniqueRoots = new LinkedHashSet<VirtualFile>(sourceRoots);
    if (uniqueRoots.size() != sourceRoots.size()) {
      modificator.removeRoots(OrderRootType.CLASSES);
      for (VirtualFile root : uniqueRoots) {
        modificator.addRoot(root, OrderRootType.CLASSES);
      }
      return true;
    }
    return false;
  }

  /**
   * Removes legacy SOURCES entries in Python SDK tables (PY-2891).
   */
  private static boolean removeSourceRoots(@NotNull Sdk sdk, @NotNull SdkModificator modificator) {
    final VirtualFile[] sourceRoots = sdk.getRootProvider().getFiles(OrderRootType.SOURCES);
    if (sourceRoots.length > 0) {
      modificator.removeRoots(OrderRootType.SOURCES);
      return true;
    }
    return false;
  }

  /**
   * Updates binary skeletons path in the Python SDK table.
   */
  private static boolean updateSkeletonsPath(@NotNull Sdk sdk, @NotNull SdkModificator modificator) {
    boolean changed = false;
    final String skeletonsPath = PythonSdkType.getSkeletonsPath(PathManager.getSystemPath(), sdk.getHomePath());
    if (skeletonsPath != null) {
      final VirtualFile skeletonsDir = StandardFileSystems.local().refreshAndFindFileByPath(skeletonsPath);
      if (skeletonsDir != null) {
        LOG.info("Binary skeletons directory for SDK \"" + sdk.getName() + "\" (" + sdk.getHomePath() + "): " + skeletonsDir.getPath());
        final List<VirtualFile> sourceRoots = Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
        boolean skeletonsDirFound = false;
        for (VirtualFile root : sourceRoots) {
          if (root.equals(skeletonsDir)) {
            skeletonsDirFound = true;
          }
          if (PythonSdkType.isSkeletonsPath(root.getPath()) && !skeletonsDirFound) {
            modificator.removeRoot(root, OrderRootType.CLASSES);
            changed = true;
          }
        }
        if (!skeletonsDirFound) {
          modificator.addRoot(skeletonsDir, OrderRootType.CLASSES);
          changed = true;
        }
      }
    }
    return changed;
  }

  private static boolean wasOldRoot(@NotNull String root, @NotNull Collection<VirtualFile> oldRoots) {
    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
    if (file != null) {
      final VirtualFile rootFile = PythonSdkType.getSdkRootVirtualFile(file);
      return oldRoots.contains(rootFile);
    }
    return false;
  }
}
