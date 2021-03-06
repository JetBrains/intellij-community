package com.jetbrains.python.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.sdk.PythonSdkUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Constructs an instance of {@link GlobalSearchScope} for a Python SDK, allowing to exclude some standard synthetic roots and
 * sources not intended for direct use.
 */
@ApiStatus.Experimental
public final class PySearchScopeBuilder {
  private static final Set<String> STDLIB_TEST_DIRS = Set.of(
    "bsddb/test",
    "ctypes/test",
    "distutils/tests",
    "email/test",
    "idlelib/idle_test",
    "importlib/test",
    "json/tests",
    "lib-tk/test",
    "lib2to3/tests",
    "sqlite3/test",
    "test",
    "tkinter/test",
    "unittest/test"
  );
  private static final Set<String> TEST_ROOT_NAMES = Set.of("test", "tests");
  private static final Set<String> BUNDLED_DEPS_ROOT_NAMES = Set.of("_vendor", "vendor", "vendored", "_vendored_packages");
  private static final Set<String> THIRD_PARTY_PACKAGE_ROOT_NAMES = Set.of(PyNames.SITE_PACKAGES, PyNames.DIST_PACKAGES);

  private boolean myExcludeStdlibTests = false;
  private boolean myExcludeThirdPartyTypeShedStubs = false;
  private boolean myExcludePythonSkeletonsStubs = false;
  private boolean myExcludeThirdPartyBundledDeps = false;
  private boolean myExcludeThirdPartyTests = false;
  private final @NotNull Project myProject;
  private final @Nullable Sdk mySdk;

  /**
   * Creates a new builder for the given Python SDK.
   */
  @NotNull
  public static PySearchScopeBuilder forPythonSdk(@NotNull Project project, @NotNull Sdk sdk) {
    return new PySearchScopeBuilder(project, sdk);
  }

  /**
   * Creates a new builder, deducing Python SDK from the given element.
   * <p>
   * Both module-level and project-level SDKs are considered.
   */
  @NotNull
  public static PySearchScopeBuilder forPythonSdkOf(@NotNull PsiElement element) {
    return new PySearchScopeBuilder(element.getProject(), findPythonSdkForElement(element));
  }

  private PySearchScopeBuilder(@NotNull Project project, @Nullable Sdk sdk) {
    myProject = project;
    mySdk = sdk;
  }

  /**
   * Excludes tests of the standard library from the resulting scope, e.g. "ctypes/test" or "tkinter/test".
   */
  @NotNull
  public PySearchScopeBuilder excludeStandardLibraryTests() {
    myExcludeStdlibTests = true;
    return this;
  }

  /**
   * Excludes .pyi stubs for third-party packages included in Typeshed from the resulting scope.
   */
  @NotNull
  public PySearchScopeBuilder excludeThirdPartyPackageTypeShedStubs() {
    myExcludeThirdPartyTypeShedStubs = true;
    return this;
  }

  /**
   * Excludes the legacy <a href="https://github.com/JetBrains/python-skeletons">python-skeletons</a> (aka "user skeletons")
   * stubs from the resulting scope.
   */
  @NotNull
  public PySearchScopeBuilder excludePythonSkeletonsStubs() {
    myExcludePythonSkeletonsStubs = true;
    return this;
  }

  /**
   * Excludes "vendored" dependencies bundled with third-party packages from the resulting scope, e.g. the content of "pip/_vendor".
   */
  @NotNull
  public PySearchScopeBuilder excludeThirdPartyPackageBundledDependencies() {
    myExcludeThirdPartyBundledDeps = true;
    return this;
  }

  /**
   * Excludes tests bundled with third-party packages from the resulting scope, e.g. "numpy/tests" or "scipy/stats/tests".
   */
  @NotNull
  public PySearchScopeBuilder excludeThirdPartyPackageTests() {
    myExcludeThirdPartyTests = true;
    return this;
  }

  /**
   * Builds a {@link GlobalSearchScope} instance for the specified SDK according to the configuration.
   */
  @NotNull
  public GlobalSearchScope build() {
    GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
    if (myExcludeStdlibTests) {
      scope = scope.intersectWith(GlobalSearchScope.notScope(buildStdlibTestsScope()));
    }
    if (myExcludeThirdPartyTypeShedStubs) {
      scope = scope.intersectWith(GlobalSearchScope.notScope(buildThirdPartyTypeShedStubsScope()));
    }
    if (myExcludePythonSkeletonsStubs) {
      scope = scope.intersectWith(GlobalSearchScope.notScope(buildPythonSkeletonsStubsScope()));
    }
    if (myExcludeThirdPartyTests || myExcludeThirdPartyBundledDeps) {
      scope = scope.intersectWith(GlobalSearchScope.notScope(new QualifiedNameFinder.QualifiedNameBasedScope(myProject) {
        @Override
        protected boolean containsQualifiedNameInRoot(@NotNull VirtualFile root, @NotNull QualifiedName qName) {
          if (THIRD_PARTY_PACKAGE_ROOT_NAMES.contains(root.getName())) {
            List<String> internalQNameComponents = qName.removeHead(1).getComponents();
            if (myExcludeThirdPartyTests && ContainerUtil.exists(internalQNameComponents, TEST_ROOT_NAMES::contains)) {
              return true;
            }
            if (myExcludeThirdPartyBundledDeps && ContainerUtil.exists(internalQNameComponents, BUNDLED_DEPS_ROOT_NAMES::contains)) {
              return true;
            }
          }
          return false;
        }
      }));
    }
    return scope;
  }

  @NotNull
  private GlobalSearchScope buildStdlibTestsScope() {
    if (mySdk != null) {
      VirtualFile libDir = PySearchUtilBase.findLibDir(mySdk);
      if (libDir != null) {
        return StreamEx.of(STDLIB_TEST_DIRS)
          .map(relPath -> libDir.findFileByRelativePath(relPath))
          .nonNull()
          .map(dir -> GlobalSearchScopesCore.directoryScope(myProject, dir, true))
          .reduce(GlobalSearchScope.EMPTY_SCOPE, GlobalSearchScope::union);
      }
    }
    return GlobalSearchScope.EMPTY_SCOPE;
  }

  @NotNull
  private GlobalSearchScope buildThirdPartyTypeShedStubsScope() {
    if (mySdk != null) {
      return StreamEx.of(mySdk.getRootProvider().getFiles(OrderRootType.CLASSES))
        .filter(root -> PyTypeShed.INSTANCE.isInside(root) && PyTypeShed.INSTANCE.isInThirdPartyLibraries(root))
        .map(dir -> GlobalSearchScopesCore.directoryScope(myProject, dir, true))
        .reduce(GlobalSearchScope.EMPTY_SCOPE, GlobalSearchScope::union);
    }
    return GlobalSearchScope.EMPTY_SCOPE;
  }

  @NotNull
  private GlobalSearchScope buildPythonSkeletonsStubsScope() {
    return PyUserSkeletonsUtil.getUserSkeletonsDirectoryScope(myProject);
  }

  @Nullable
  private static Sdk findPythonSdkForElement(@NotNull PsiElement element) {
    Project project = element.getProject();
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null) {
      Sdk sdk = PythonSdkUtil.findPythonSdk(module);
      if (sdk != null) {
        return sdk;
      }
    }
    return ProjectRootManager.getInstance(project).getProjectSdk();
  }
}
