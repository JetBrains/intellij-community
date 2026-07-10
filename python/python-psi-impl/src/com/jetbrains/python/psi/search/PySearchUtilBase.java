package com.jetbrains.python.psi.search;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.jetbrains.python.sdk.PythonInterpreterKt;
import com.jetbrains.python.sdk.PythonInterpreterExtKt;
import com.jetbrains.python.sdk.legacy.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PySearchUtilBase {

  /**
   * Creates a scope most suitable for suggesting symbols and files to a user, i.e. in auto-importing or "extended" completion.
   * <p>
   * This scope covers the project's own sources and its libraries, but excludes
   * <ul>
   *   <li>Standard library tests</li>
   *   <li>Stubs for third-party packages in Typeshed</li>
   *   <li>Bundled tests of third-party packages</li>
   *   <li>Bundled dependencies of third-party packages</li>
   * </ul>
   *
   * @param anchor element to detect the corresponding Python SDK
   * @see PySearchScopeBuilder
   */
  public static @NotNull GlobalSearchScope defaultSuggestionScope(@NotNull PsiElement anchor) {
    return PySearchScopeBuilder.forPythonSdkOf(anchor)
      .excludeStandardLibraryTests()
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
  public static @NotNull GlobalSearchScope excludeSdkTestsScope(@NotNull Project project) {
    return excludeSdkTestScope(ProjectScope.getAllScope(project));
  }

  public static @NotNull GlobalSearchScope excludeSdkTestScope(@NotNull GlobalSearchScope scope) {
    Project project = Objects.requireNonNull(scope.getProject());
    Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    // TODO cache the scope in project userdata (update when SDK paths change or different project SDK is selected)
    if (sdk != null && PythonSdkUtil.isPythonSdk(sdk)) {
      return scope.intersectWith(PySearchScopeBuilder.forPythonSdk(project, sdk)
                                   .excludeStandardLibraryTests()
                                   .build());
    }
    return scope;
  }

  public static @Nullable VirtualFile findLibDir(@NotNull Sdk sdk) {
    return PythonInterpreterExtKt.stdlibLibDirectory(PythonInterpreterKt.pythonInterpreter(sdk, false));
  }
}
