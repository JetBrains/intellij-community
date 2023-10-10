package com.jetbrains.python.packaging;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class CondaExecutablesLocator {
  private final static String[] CONDA_DEFAULT_ROOTS =
    new String[]{"anaconda", "anaconda3", "miniconda", "miniconda3", "Anaconda", "Anaconda3", "Miniconda", "Miniconda3"};
  private static final String CONDA_ENVS_DIR = "envs";
  private static final String CONDA_BAT_NAME = "conda.bat";
  private static final String CONDA_BINARY_NAME = "conda";
  private static final String WIN_CONDA_BIN_DIR_NAME = "condabin";
  private static final String UNIX_CONDA_BIN_DIR_NAME = "bin";
  private static final String PYTHON_EXE_NAME = "python.exe";
  private static final String PYTHON_UNIX_BINARY_NAME = "python";
  private static final String WIN_CONTINUUM_DIR_PATH = "AppData\\Local\\Continuum\\";
  private static final String WIN_PROGRAM_DATA_PATH = "C:\\ProgramData\\";
  private static final String WIN_C_ROOT_PATH = "C:\\";
  private static final String UNIX_OPT_PATH = "/opt/";

  private static final Logger LOG = Logger.getInstance(CondaExecutablesLocator.class);

  private CondaExecutablesLocator() {
  }

  @Nullable
  public static String getCondaBasePython(@NotNull String systemCondaExecutable) {
    final VirtualFile condaFile = LocalFileSystem.getInstance().findFileByPath(systemCondaExecutable);
    if (condaFile != null) {
      final VirtualFile condaDir = SystemInfo.isWindows ? condaFile.getParent().getParent() : condaFile.getParent();
      final VirtualFile python = condaDir.findChild(getPythonName());
      if (python != null) {
        return python.getPath();
      }
    }
    return null;
  }

  @NotNull
  private static String getPythonName() {
    return SystemInfo.isWindows ? PYTHON_EXE_NAME : PYTHON_UNIX_BINARY_NAME;
  }

  @Nullable
  static String findCondaExecutableRelativeToEnv(@NotNull String sdkPath) {
    final VirtualFile pyExecutable = StandardFileSystems.local().findFileByPath(sdkPath);
    if (pyExecutable == null) {
      return null;
    }
    final VirtualFile pyExecutableDir = pyExecutable.getParent();
    final boolean isBaseConda = pyExecutableDir.findChild(CONDA_ENVS_DIR) != null;
    final String condaName;
    final VirtualFile condaFolder;
    if (SystemInfo.isWindows) {
      condaName = CONDA_BAT_NAME;
      // On Windows python.exe is directly inside base interpreter/environment directory.
      // On other systems executable normally resides in "bin" subdirectory.
      condaFolder = pyExecutableDir;
    }
    else {
      condaName = CONDA_BINARY_NAME;
      condaFolder = pyExecutableDir.getParent();
    }

    // XXX Do we still need to support this? When did they drop per-environment conda executable?
    final String localCondaName = SystemInfo.isWindows && !isBaseConda ? CONDA_BAT_NAME : condaName;
    final String immediateConda = findExecutable(localCondaName, condaFolder);
    if (immediateConda != null) {
      return immediateConda;
    }
    final VirtualFile envsDir = condaFolder.getParent();
    if (!isBaseConda && envsDir != null && envsDir.getName().equals(CONDA_ENVS_DIR)) {
      return findExecutable(condaName, envsDir.getParent());
    }
    return null;
  }

  @Nullable
  private static String getCondaExecutableByName(@NotNull final String condaName) {
    final VirtualFile userHome = LocalFileSystem.getInstance().findFileByPath(SystemProperties.getUserHome().replace('\\', '/'));

    for (String root : CONDA_DEFAULT_ROOTS) {
      VirtualFile condaFolder = userHome == null ? null : userHome.findChild(root);
      String executableFile = findExecutable(condaName, condaFolder);
      if (executableFile != null) return executableFile;

      //noinspection IfStatementWithIdenticalBranches
      if (SystemInfo.isWindows) {
        condaFolder = userHome == null ? null : userHome.findFileByRelativePath(WIN_CONTINUUM_DIR_PATH + root);
        executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;

        condaFolder = LocalFileSystem.getInstance().findFileByPath(WIN_PROGRAM_DATA_PATH + root);
        executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;

        condaFolder = LocalFileSystem.getInstance().findFileByPath(WIN_C_ROOT_PATH + root);
        executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;
      }
      else {
        condaFolder = LocalFileSystem.getInstance().findFileByPath(UNIX_OPT_PATH + root);
        executableFile = findExecutable(condaName, condaFolder);
        if (executableFile != null) return executableFile;
      }
    }

    return null;
  }

  @Nullable
  private static String findExecutable(String condaName, @Nullable final VirtualFile condaFolder) {
    if (condaFolder != null) {
      final VirtualFile binFolder = condaFolder.findChild(SystemInfo.isWindows ? WIN_CONDA_BIN_DIR_NAME : UNIX_CONDA_BIN_DIR_NAME);
      if (binFolder != null) {
        final VirtualFile bin = binFolder.findChild(condaName);
        if (bin != null) {
          String directoryPath = bin.getPath();
          final String executableFile = PythonSdkUtil.getExecutablePath(directoryPath, condaName);
          if (executableFile != null) {
            return executableFile;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  static String getSystemCondaExecutable() {
    final String condaName = SystemInfo.isWindows ? CONDA_BAT_NAME : CONDA_BINARY_NAME;

    final File condaInPath = PathEnvironmentVariableUtil.findInPath(condaName);
    if (condaInPath != null) {
      LOG.info("Using " + condaInPath + " as a conda executable (found in PATH)");
      return condaInPath.getPath();
    }

    final String condaInRoots = getCondaExecutableByName(condaName);
    if (condaInRoots != null) {
      LOG.info("Using " + condaInRoots + " as a conda executable (found by visiting possible conda roots)");
      return condaInRoots;
    }

    LOG.info("System conda executable is not found");
    return null;
  }
}
