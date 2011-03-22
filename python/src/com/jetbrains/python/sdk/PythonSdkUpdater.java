package com.jetbrains.python.sdk;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PythonSdkUpdater implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.PythonSdkUpdater");

  public static int SKELETONS_VERSION = 18;

  public PythonSdkUpdater(final Project project, StartupManager startupManager) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    startupManager.registerStartupActivity(new Runnable() {
      public void run() {
        final File skeletonDir = new File(PathManager.getSystemPath(), PythonSdkType.SKELETON_DIR_NAME);
        final File versionFile = new File(skeletonDir, "version");
        boolean versionUpdated = false;
        int version = versionFile.exists() ? readVersion(versionFile) : -1;
        if (version != SKELETONS_VERSION) {
          if (version == -1) {
            LOG.info("Version file " + versionFile.getPath() +  " did not exist, rebuilding all skeletons");
          }
          else {
            LOG.info("Skeletons version incremented from " + version + " to " + SKELETONS_VERSION + ", rebuilding all skeletons");
          }
          skeletonDir.mkdirs();
          writeVersion(versionFile, SKELETONS_VERSION);
          versionUpdated = true;
        }
        final List<Sdk> sdkList = PythonSdkType.getAllSdks();
        for (Sdk sdk : sdkList) {
          final String skeletonsPath = PythonSdkType.findSkeletonsPath(sdk);
          if (skeletonsPath == null) {
            LOG.info("Could not find skeletons path for SDK path " + sdk.getHomePath());
          }
          else if (versionUpdated || !new File(skeletonsPath).isDirectory()) {
            if (!versionUpdated) {
              LOG.info("Rebuilding skeletons for " + sdk.getHomePath() + " because skeletons directory " + skeletonsPath + " was not found");
            }
            PythonSdkType.generateSkeletons(ProgressManager.getInstance().getProgressIndicator(), sdk.getHomePath(), skeletonsPath);
          }
        }
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length > 0) {
          updateSysPath(modules[0]);
        }
      }
    });
  }

  public static boolean skeletonsUpToDate() {
    final File skeletonDir = new File(PathManager.getSystemPath(), PythonSdkType.SKELETON_DIR_NAME);
    final File versionFile = new File(skeletonDir, "version");
    int version = versionFile.exists() ? readVersion(versionFile) : -1;
    return version == SKELETONS_VERSION;
  }

  private static void updateSysPath(Module module) {
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk != null) {
      final SdkType sdkType = sdk.getSdkType();
      if (sdkType instanceof PythonSdkType) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
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

  private static int readVersion(File versionFile) {
    try {
      final FileInputStream stream = new FileInputStream(versionFile);
      try {
        DataInputStream dataInputStream = new DataInputStream(stream);
        try {
          return dataInputStream.readInt();
        }
        finally {
          dataInputStream.close();
        }
      }
      finally {
        stream.close();
      }
    }
    catch (IOException e) {
      return -1;
    }
  }

  private static void writeVersion(File versionFile, int version) {
    try {
      final FileOutputStream stream = new FileOutputStream(versionFile);
      try {
        DataOutputStream dataOutputStream = new DataOutputStream(stream);
        try {
          dataOutputStream.writeInt(version);
        }
        finally {
          dataOutputStream.close();
        }
      }
      finally {
        stream.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
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
