package com.jetbrains.python.sdk;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A component that initiates a refresh of all project's Python SDKs.
 * Delegates most of the work to PythonSdkType.
 * <br/>
 * @author yole
 */
public class PythonSdkUpdater implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.PythonSdkUpdater");

  public static final @NotNull Key<Boolean> SKELETONS_ALREADY_UPDATED_FLAG = new Key<Boolean>("SKELETONS_ALREADY_UPDATED_FLAG");

  public PythonSdkUpdater(final Project project, StartupManager startupManager) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }
    startupManager.registerStartupActivity(new Runnable() {
      public void run() {
        final Boolean flag = application.getUserData(SKELETONS_ALREADY_UPDATED_FLAG);
        if (flag == null || !flag) {
          long start_time = System.currentTimeMillis();
          final Module[] modules = ModuleManager.getInstance(project).getModules();
          if (modules.length > 0) {
            updateSysPath(project, modules[0]);
          }
          LOG.info("Refreshing skeletons took " + (System.currentTimeMillis() - start_time) + " ms");
        }
        application.putUserData(SKELETONS_ALREADY_UPDATED_FLAG, true);
      }
    });
  }

  private static void updateSysPath(final Project project, Module module) {
    // NOTE: everything is run later on the AWT thread
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk != null) {
      final SdkType sdkType = sdk.getSdkType();
      if (sdkType instanceof PythonSdkType) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            try {
              Thread.sleep(7000); // wait until all short-term disk-hitting activity ceases
            }
            catch (InterruptedException ignore) {}
            // update skeletons
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "Updating skeletons", false) {
                  @Override
                  public void run(@NotNull ProgressIndicator indicator) {
                    PythonSdkType.refreshSkeletonsOfAllSDKs(project); // NOTE: whole thing would need a rename
                  }
                });
              }
            });
            // update paths
            final List<String> sysPath = ((PythonSdkType)sdkType).getSysPath(sdk.getHomePath());
            if (sysPath != null) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  updateSdkPath(sdk, sysPath);
                }
              });
            }
          }
        });
      }
    }
  }

  private static void updateSdkPath(Sdk sdk, List<String> sysPath) {
    final List<VirtualFile> oldRoots = Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
    final VirtualFile[] sourceRoots = sdk.getRootProvider().getFiles(OrderRootType.SOURCES);
    PythonSdkAdditionalData additionalData = sdk.getSdkAdditionalData() instanceof PythonSdkAdditionalData
      ? (PythonSdkAdditionalData) sdk.getSdkAdditionalData()
      : null;
    List<String> newRoots = new ArrayList<String>();
    for(String root: sysPath) {
      if (new File(root).exists() &&
          !"egg-info".equals(FileUtil.getExtension(root)) &&
          (additionalData == null || !wasOldRoot(root, additionalData.getExcludedPaths())) &&
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

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "Updating skeletons for binary modules";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
