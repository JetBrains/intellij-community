package com.jetbrains.python.sdk;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;

/**
 * @author yole
 */
public class SkeletonVersionUpdater implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.SkeletonVersionUpdater");

  public static int SKELETONS_VERSION = 3;

  public SkeletonVersionUpdater(StartupManager startupManager) {
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
                PythonSdkType.generateBuiltinStubs(sdk.getHomePath(), path);
                PythonSdkType.generateBinarySkeletons(sdk.getHomePath(), path, ProgressManager.getInstance().getProgressIndicator());
              }
            }
          }
        }
      }
    });
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
