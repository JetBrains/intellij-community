package com.intellij.openapi.module;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.FilteredQuery;
import org.jetbrains.annotations.Nullable;

public class ResourceFileUtil {
  private ResourceFileUtil() {
  }

  @Nullable
  public static VirtualFile findResourceFile(final String name, final Module inModule) {
    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(inModule).getSourceRoots();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(inModule.getProject()).getFileIndex();
    for (final VirtualFile sourceRoot : sourceRoots) {
      final String packagePrefix = fileIndex.getPackageNameByDirectory(sourceRoot);
      final String prefix = packagePrefix == null || packagePrefix.length() == 0 ? null : packagePrefix.replace('.', '/') + "/";
      final String relPath = prefix != null && name.startsWith(prefix) && name.length() > prefix.length() ? name.substring(prefix.length()) : name;
      final String fullPath = sourceRoot.getPath() + "/" + relPath;
      final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(fullPath);
      if (fileByPath != null) {
        return fileByPath;
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile findResourceFileInDependents(final Module searchFromModule, final String fileName) {
    return findResourceFileInScope(fileName, searchFromModule.getProject(), searchFromModule.getModuleWithDependenciesScope());
  }

  @Nullable
  public static VirtualFile findResourceFileInProject(final Project project, final String resourceName) {
    return findResourceFileInScope(resourceName, project, GlobalSearchScope.projectScope(project));
  }

  @Nullable
  public static VirtualFile findResourceFileInScope(final String resourceName,
                                                    final Project project,
                                                    final GlobalSearchScope scope) {
    int index = resourceName.lastIndexOf('/');
    String packageName = index >= 0 ? resourceName.substring(0, index).replace('/', '.') : "";
    final String fileName = index >= 0 ? resourceName.substring(index+1) : resourceName;

    final VirtualFile dir = new FilteredQuery<VirtualFile>(
      PackageIndex.getInstance(project).getDirsByPackageName(packageName, false), new Condition<VirtualFile>() {
      public boolean value(final VirtualFile file) {
        final VirtualFile child = file.findChild(fileName);
        return child != null && scope.contains(child);
      }
    }).findFirst();
    return dir != null ? dir.findChild(fileName) : null;
  }
}
