package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PySearchUtilBase {
  /**
   * Calculates a search scope which excludes Python standard library tests. Using such scope may be quite a bit slower than using
   * the regular "project and libraries" search scope, so it should be used only for displaying the list of variants to the user
   * (for example, for class name completion or auto-import).
   *
   * @param project the project for which the scope should be calculated
   * @return the resulting scope
   */
  public static GlobalSearchScope excludeSdkTestsScope(Project project) {
    return excludeSdkTestScope(ProjectScope.getAllScope(project));
  }

  @NotNull
  public static GlobalSearchScope excludeSdkTestScope(@NotNull GlobalSearchScope scope) {
    Project project = Objects.requireNonNull(scope.getProject());
    Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    // TODO cache the scope in project userdata (update when SDK paths change or different project SDK is selected)
    GlobalSearchScope exclude = excludeSdkTestsScope(project, sdk);
    return exclude != null ? scope.intersectWith(exclude) : scope;
  }

  public static GlobalSearchScope excludeSdkTestsScope(PsiElement anchor) {
    final Project project = anchor.getProject();
    Module module = ModuleUtilCore.findModuleForPsiElement(anchor);
    if (module != null) {
      Sdk sdk = PythonSdkUtil.findPythonSdk(module);
      if (sdk != null) {
        GlobalSearchScope excludeScope = excludeSdkTestsScope(project, sdk);
        if (excludeScope != null) {
          return GlobalSearchScope.allScope(project).intersectWith(excludeScope);
        }
      }
    }
    return excludeSdkTestsScope(project);
  }

  @Nullable
  private static GlobalSearchScope excludeSdkTestsScope(Project project, Sdk sdk) {
    if (sdk != null && PythonSdkUtil.isPythonSdk(sdk)) {
      List<VirtualFile> excludedDirs = new ArrayList<>();
      VirtualFile libDir = findLibDir(sdk);
      if (libDir != null) {
        // superset of test dirs found in Python 2.5 to 3.1
        excludedDirs.addAll(findTestDirs(libDir, "test", "bsddb/test", "ctypes/test", "distutils/tests", "email/test",
                                         "importlib/test", "json/tests", "lib2to3/tests", "sqlite3/test", "tkinter/test",
                                         "idlelib/testcode.py"));
      }
      // XXX: Disable resolving to any third-party libraries from typeshed in the same places where we don't want SDK tests
      excludedDirs.addAll(Arrays.stream(sdk.getRootProvider().getFiles(OrderRootType.CLASSES))
                            .filter(file -> PyTypeShed.INSTANCE.isInside(file) && PyTypeShed.INSTANCE.isInThirdPartyLibraries(file))
                            .collect(Collectors.toList()));
      if (!excludedDirs.isEmpty()) {
        GlobalSearchScope scope = buildUnionScope(project, excludedDirs);
        return GlobalSearchScope.notScope(scope);
      }
    }
    return null;
  }

  private static GlobalSearchScope buildUnionScope(Project project, List<VirtualFile> testDirs) {
    GlobalSearchScope scope = GlobalSearchScopesCore.directoryScope(project, testDirs.get(0), true);
    for (int i = 1; i < testDirs.size(); i++) {
      scope = scope.union(GlobalSearchScopesCore.directoryScope(project, testDirs.get(i), true));
    }
    return scope;
  }

  private static List<VirtualFile> findTestDirs(VirtualFile baseDir, String... relativePaths) {
    List<VirtualFile> result = new ArrayList<>();
    for (String path : relativePaths) {
      VirtualFile child = baseDir.findFileByRelativePath(path);
      if (child != null) {
        result.add(child);
      }
    }
    return result;
  }

  @Nullable
  public static VirtualFile findLibDir(Sdk sdk) {
    return findLibDir(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
  }

  public static VirtualFile findVirtualEnvLibDir(Sdk sdk) {
    VirtualFile[] classVFiles = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    String homePath = sdk.getHomePath();
    if (homePath != null) {
      File root = PythonSdkUtil.getVirtualEnvRoot(homePath);
      if (root != null) {
        File libRoot = new File(root, "lib");
        File[] versionRoots = libRoot.listFiles();
        if (versionRoots != null && !SystemInfo.isWindows) {
          final File versionRoot = ContainerUtil.find(versionRoots, file -> file.isDirectory() && file.getName().startsWith("python"));
          if (versionRoot != null) {
            libRoot = versionRoot;
          }
        }
        // Empty in case of a temporary empty SDK created to install package management
        if (classVFiles.length == 0) {
          return LocalFileSystem.getInstance().findFileByIoFile(libRoot);
        }

        final String libRootPath = libRoot.getPath();
        for (VirtualFile file : classVFiles) {
          if (FileUtil.pathsEqual(file.getPath(), libRootPath)) {
            return file;
          }
          // venv module doesn't add virtualenv's lib/pythonX.Y directory itself in sys.path
          final VirtualFile parent = file.getParent();
          if (PyNames.SITE_PACKAGES.equals(file.getName()) && FileUtil.pathsEqual(parent.getPath(), libRootPath)) {
            return parent;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile findLibDir(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (!file.isValid()) {
        continue;
      }
      if ((file.findChild("__future__.py") != null || file.findChild("__future__.pyc") != null) &&
          file.findChild("xml") != null && file.findChild("email") != null) {
        return file;
      }
      // Mock SDK does not have aforementioned modules
      if (ApplicationManager.getApplication().isUnitTestMode() && file.getName().equals("Lib")) {
        return file;
      }
    }
    return null;
  }
}
