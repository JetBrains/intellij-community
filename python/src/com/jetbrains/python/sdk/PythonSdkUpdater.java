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
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PythonSdkUpdater implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.PythonSdkUpdater");

  public static int SKELETONS_VERSION = 3;

  public PythonSdkUpdater(final Project project, StartupManager startupManager) {
    startupManager.registerStartupActivity(new Runnable() {
      public void run() {
        final File skeletonDir = new File(PathManager.getSystemPath(), PythonSdkType.SKELETON_DIR_NAME);
        if (skeletonDir.isDirectory()) {
          final File versionFile = new File(skeletonDir, "version");
          if (!versionFile.exists() || readVersion(versionFile) != SKELETONS_VERSION) {
            writeVersion(versionFile, SKELETONS_VERSION);
            final List<Sdk> sdkList = PythonSdkType.getAllSdks();
            for (Sdk sdk : sdkList) {
              final String path = PythonSdkType.findSkeletonsPath(sdk);
              if (path == null) {
                LOG.info("Could not find skeletons path for SDK path " + sdk.getHomePath());
              }
              else {
                PythonSdkType.generateSkeletons(ProgressManager.getInstance().getProgressIndicator(), sdk.getHomePath());
              }
            }
          }
        }
        final Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules.length > 0) {
          updateSysPath(modules [0]);
        }
      }
    });
  }

  private static void updateSysPath(Module module) {
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk != null) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          final List<String> sysPath = PythonSdkType.getSysPath(sdk.getHomePath());
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              updateSdkPath(sdk, sysPath);
            }
          });
        }
      });
    }
  }

  private static void updateSdkPath(Sdk sdk, List<String> sysPath) {
    // HACK: SDK roots configured by user are added as roots of type CLASSES only, and roots configured from sys.path
    // are both classes and sources
    final VirtualFile[] oldClassesRoots = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    final VirtualFile[] oldSourcesRoots = sdk.getRootProvider().getFiles(OrderRootType.SOURCES);
    List<String> newRoots = new ArrayList<String>();
    for(String root: sysPath) {
      if (new File(root).exists() &&
          !"egg-info".equals(FileUtil.getExtension(root)) &&
          !wasOldRoot(root, oldClassesRoots) &&
          !wasOldRoot(root, oldSourcesRoots)) {
        newRoots.add(root);
      }
    }
    if (!newRoots.isEmpty()) {
      final SdkModificator modificator = sdk.getSdkModificator();
      for (String root : newRoots) {
        PythonSdkType.addSdkRoot(modificator, root);
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          modificator.commitChanges();
        }
      });
    }
  }

  private static boolean wasOldRoot(String root, VirtualFile[] virtualFiles) {
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
