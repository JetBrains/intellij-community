/*
 * @author max
 */
package com.jetbrains.python.psi.stubs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PyClassNameIndex extends StringStubIndexExtension<PyClass> {
  public static final StubIndexKey<String,PyClass> KEY = StubIndexKey.createIndexKey("Py.class.shortName");

  public StubIndexKey<String, PyClass> getKey() {
    return KEY;
  }

  public static Collection<PyClass> find(String name, Project project, GlobalSearchScope scope) {
    return StubIndex.getInstance().get(KEY, name, project, scope);
  }

  public static Collection<PyClass> find(String name, Project project, boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? projectWithLibrariesScope(project)
                                    : GlobalSearchScope.projectScope(project);
    return find(name, project, scope);
  }

  /**
   * Calculates a search scope which excludes Python standard library tests. Using such scope may be quite a bit slower than using
   * the regular "project and libraries" search scope, so it should be used only for displaying the list of variants to the user
   * (for example, for class name completion or auto-import).
   *
   * @param project the project for which the scope should be calculated
   * @return the resulting scope
   */
  public static GlobalSearchScope projectWithLibrariesScope(Project project) {
    final Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
    // TODO cache the scope in project userdata (update when SDK paths change or different project SDK is selected)
    if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
      VirtualFile libDir = findLibDir(sdk);
      if (libDir != null) {
        // superset of test dirs found in Python 2.5 to 3.1
        List<VirtualFile> testDirs = findTestDirs(libDir, "test", "bsddb/test", "ctypes/test", "distutils/tests", "email/test",
                                                  "importlib/test", "json/tests", "lib2to3/tests", "sqlite3/test", "tkinter/test");
        if (!testDirs.isEmpty()) {
          GlobalSearchScope scope = buildUnionScope(project, testDirs);
          return ProjectScope.getAllScope(project).intersectWith(GlobalSearchScope.notScope(scope));
        }
      }
    }
    return ProjectScope.getAllScope(project);
  }

  private static GlobalSearchScope buildUnionScope(Project project, List<VirtualFile> testDirs) {
    GlobalSearchScope scope = GlobalSearchScope.directoryScope(project, testDirs.get(0), true);
    for (int i = 1; i < testDirs.size(); i++) {
      scope = scope.union(GlobalSearchScope.directoryScope(project, testDirs.get(i), true));
    }
    return scope;
  }


  private static List<VirtualFile> findTestDirs(VirtualFile baseDir, String... relativePaths) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
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

  @Nullable
  private static VirtualFile findLibDir(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if ((file.findChild("__future__.py") != null || file.findChild("__future__.pyc") != null) && file.findChild("test") != null) {
        return file;
      }
    }
    return null;
  }

  @Nullable
  public static PyClass findClass(@NotNull String qName, Project project, GlobalSearchScope scope) {
    int pos = qName.lastIndexOf(".");
    assert pos > 0;
    String shortName = qName.substring(pos+1);
    for (PyClass pyClass : find(shortName, project, scope)) {
      if (pyClass.getQualifiedName().equals(qName)) {
        return pyClass;
      }
    }
    return null;
  }

  @Nullable
  public static PyClass findClass(@Nullable String qName, Project project) {
    if (qName == null) {
      return null;
    }
    return findClass(qName, project, ProjectScope.getAllScope(project));
  }

  public static Collection<String> allKeys(Project project) {
    return StubIndex.getInstance().getAllKeys(KEY, project);
  }
}