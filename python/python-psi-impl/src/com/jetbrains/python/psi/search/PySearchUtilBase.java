package com.jetbrains.python.psi.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

public class PySearchUtilBase {

  /**
   * Creates a scope most suitable for suggesting symbols and files to a user, i.e. in auto-importing or "extended" completion.
   * <p>
   * This scope covers the project's own sources and its libraries, but excludes
   * <ul>
   *   <li>Standard library tests</li>
   *   <li>Stubs for third-party packages in Typeshed</li>
   *   <li>Stubs in python-skeletons</li>
   *   <li>Bundled tests of third-party packages</li>
   *   <li>Bundled dependencies of third-party packages</li>
   * </ul>
   *
   * @param anchor element to detect the corresponding Python SDK
   * @see PySearchScopeBuilder
   */
  @NotNull
  public static GlobalSearchScope defaultSuggestionScope(@NotNull PsiElement anchor) {
    return PySearchScopeBuilder.forPythonSdkOf(anchor)
      .excludeStandardLibraryTests()
      .excludeThirdPartyPackageTypeShedStubs()
      .excludePythonSkeletonsStubs()
      .excludeThirdPartyPackageTests()
      .excludeThirdPartyPackageBundledDependencies()
      .build();
  }

  /**
   * Calculates a search scope which excludes Python standard library tests. Using such scope may be quite a bit slower than using
   * the regular "project and libraries" search scope, so it should be used only for displaying the list of variants to the user
   * (for example, for class name completion or auto-import).
   *
   * @param project the project for which the scope should be calculated
   * @return the resulting scope
   */
  @NotNull
  public static GlobalSearchScope excludeSdkTestsScope(@NotNull Project project) {
    return excludeSdkTestScope(ProjectScope.getAllScope(project));
  }

  @NotNull
  public static GlobalSearchScope excludeSdkTestScope(@NotNull GlobalSearchScope scope) {
    Project project = Objects.requireNonNull(scope.getProject());
    Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    // TODO cache the scope in project userdata (update when SDK paths change or different project SDK is selected)
    if (sdk != null && PythonSdkUtil.isPythonSdk(sdk)) {
      return scope.intersectWith(PySearchScopeBuilder.forPythonSdk(project, sdk)
                                   .excludeStandardLibraryTests()
                                   .excludeThirdPartyPackageTypeShedStubs()
                                   .build());
    }
    return scope;
  }

  @Nullable
  public static VirtualFile findLibDir(@NotNull Sdk sdk) {
    return findLibDir(ReadAction.compute(() -> sdk.getRootProvider().getFiles(OrderRootType.CLASSES)));
  }

  public static VirtualFile findVirtualEnvLibDir(@NotNull Sdk sdk) {
    VirtualFile[] classVFiles = ReadAction.compute(() -> sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
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
