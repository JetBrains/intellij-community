// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeletons;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ZipUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.PatternSyntaxException;


public class DefaultPregeneratedSkeletonsProvider implements PyPregeneratedSkeletonsProvider {
  private static final Logger LOG = Logger.getInstance(DefaultPregeneratedSkeletonsProvider.class);

  @Nullable
  private static File findPregeneratedSkeletonsRoot() {
    final String path = PathManager.getHomePath();
    LOG.info("Home path is " + path);
    File f = new File(path, "python/skeletons");  // from sources
    if (f.exists()) return f;
    f = new File(path, "skeletons");              // compiled binary
    if (f.exists()) return f;
    return null;
  }

  @VisibleForTesting
  public static boolean isApplicableZippedSkeletonsFileName(@NotNull String prebuiltSkeletonsName, @NotNull String fileName) {
    try {
      return fileName.matches(".*" + prebuiltSkeletonsName + "\\.?\\d*\\.zip");
    } catch (PatternSyntaxException e) {
      return false;
    }
  }

  @Nullable
  public static String getPregeneratedSkeletonsName(@NotNull Sdk sdk,
                                                    int generatorVersion,
                                                    boolean withMinorVersion,
                                                    boolean withExtension) {
    if (PythonSdkUtil.isRemote(sdk)) {
      return null;
    }

    @NonNls String versionString = sdk.getVersionString();
    if (versionString == null) {
      return null;
    }
    if (PythonSdkUtil.isConda(sdk)) {
      versionString = "Anaconda-" + versionString;
    }

    return getPrebuiltSkeletonsName(generatorVersion, versionString, withMinorVersion, withExtension);
  }

  @NotNull
  @VisibleForTesting
  public static String getPrebuiltSkeletonsName(int generatorVersion,
                                                @NotNull @NonNls String versionString,
                                                boolean withMinorVersion,
                                                boolean withExtension) {

    String version = StringUtil.toLowerCase(versionString).replace(" ", "-");
    if (!withMinorVersion) {
      int ind = version.lastIndexOf(".");
      if (ind != -1) {
        // strip last version
        version = version.substring(0, ind);
      }
    }

    if (SystemInfo.isMac) {
      String osVersion = SystemInfo.OS_VERSION;
      int dot = osVersion.indexOf('.');
      if (dot >= 0) {
        int secondDot = osVersion.indexOf('.', dot + 1);
        if (secondDot >= 0) {
          osVersion = osVersion.substring(0, secondDot);
        }
      }
      return "skeletons-mac-" + generatorVersion + "-" + osVersion + "-" + version + (withExtension ? ".zip" : "");
    }
    else {
      String os = SystemInfo.isWindows ? "win" : "nix";
      return "skeletons-" + os + "-" + generatorVersion + "-" + version + (withExtension ? ".zip" : "");
    }
  }

  @Override
  public PyPregeneratedSkeletons getSkeletonsForSdk(Sdk sdk, int generatorVersion) {

    final File root = findPregeneratedSkeletonsRoot();
    if (root == null || !root.exists()) {
      return null;
    }
    LOG.info("Pregenerated skeletons root is " + root);

    String prebuiltSkeletonsName = getPregeneratedSkeletonsName(sdk, generatorVersion, Registry
      .is("python.prebuilt.skeletons.minor.version.aware"), false);
    if (prebuiltSkeletonsName == null) return null;

    File f = null;

    File[] children = root.listFiles();
    if (children != null) {
      for (File file : children) {
        if (isApplicableZippedSkeletonsFileName(prebuiltSkeletonsName, file.getName())) {
          f = file;
          break;
        }
      }
    }

    if (f != null) {
      LOG.info("Found pre-generated skeletons at " + f.getPath());
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
      if (virtualFile == null) {
        LOG.info("Could not find pre-generated skeletons in VFS");
        return null;
      }
      return new ArchivedSkeletons(JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile));
    }
    else {
      LOG.info("Not found pre-generated skeletons at " + root);
      return null;
    }
  }

  private static class ArchivedSkeletons implements PyPregeneratedSkeletons {
    private final VirtualFile myArchiveRoot;

    ArchivedSkeletons(VirtualFile archiveRoot) {
      myArchiveRoot = archiveRoot;
    }

    @Override
    public boolean copyPregeneratedSkeleton(String moduleName, String skeletonDir) {
      File targetDir;
      final String modulePath = moduleName.replace('.', '/');
      File skeletonsDir = new File(skeletonDir);
      VirtualFile pregenerated = myArchiveRoot.findFileByRelativePath(modulePath + ".py");
      if (pregenerated == null) {
        pregenerated = myArchiveRoot.findFileByRelativePath(modulePath + "/" + PyNames.INIT_DOT_PY);
        targetDir = new File(skeletonsDir, modulePath);
      }
      else {
        int pos = modulePath.lastIndexOf('/');
        if (pos < 0) {
          targetDir = skeletonsDir;
        }
        else {
          final String moduleParentPath = modulePath.substring(0, pos);
          targetDir = new File(skeletonsDir, moduleParentPath);
        }
      }
      if (pregenerated != null && (targetDir.exists() || targetDir.mkdirs())) {
        LOG.info("Pre-generated skeleton for " + moduleName);
        File target = new File(targetDir, pregenerated.getName());
        try (FileOutputStream fos = new FileOutputStream(target)) {
          FileUtil.copy(pregenerated.getInputStream(), fos);
        }
        catch (IOException e) {
          LOG.info("Error copying pre-generated skeleton", e);
          return false;
        }
        return true;
      }
      return false;
    }

    @Override
    public void unpackPreGeneratedSkeletons(String skeletonDir) {
      ProgressManager.progress(PyBundle.message("python.sdk.unpacking.pre.generated.skeletons"));
      try {
        final VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(myArchiveRoot);
        if (jar != null) {
          ZipUtil.extract(new File(jar.getPath()),
                          new File(skeletonDir), null);
        }
      }
      catch (IOException e) {
        LOG.info("Error unpacking pre-generated skeletons", e);
      }
    }
  }
}
