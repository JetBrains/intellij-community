package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User: anna
 * Date: May 27, 2005
 */
public class TodoPackageUtil {
  public static boolean isPackageEmpty(PackageElement packageElement, TodoTreeBuilder builder, Project project) {
    if (packageElement == null) return true;
    final PsiPackage psiPackage = packageElement.getPackage();
    final Module module = packageElement.getModule();
    GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
    final PsiDirectory[] directories = psiPackage.getDirectories(scope);
    boolean isEmpty = true;
    for (PsiDirectory psiDirectory : directories) {
      isEmpty &= builder.isDirectoryEmpty(psiDirectory);
    }
    return isEmpty;
  }

  public static void addPackagesToChildren(ArrayList<AbstractTreeNode> children,
                                           Module module,
                                           TodoTreeBuilder builder,
                                           Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final List<VirtualFile> roots = new ArrayList<VirtualFile>();
    final List<VirtualFile> sourceRoots = new ArrayList<VirtualFile>();
    if (module == null){
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      roots.addAll(Arrays.asList(projectRootManager.getContentRoots()));
      sourceRoots.addAll(Arrays.asList(projectRootManager.getContentSourceRoots()));
    } else {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      roots.addAll(Arrays.asList(moduleRootManager.getContentRoots()));
      sourceRoots.addAll(Arrays.asList(moduleRootManager.getSourceRoots()));
    }
    final Set<PsiPackage> topLevelPackages = new HashSet<PsiPackage>();
    for (final VirtualFile root : sourceRoots) {
      final PsiDirectory directory = psiManager.findDirectory(root);
      if (directory == null) {
        continue;
      }
      final PsiPackage directoryPackage = directory.getPackage();
      if (directoryPackage == null || PackageUtil.isPackageDefault(directoryPackage)) {
        // add subpackages
        final PsiDirectory[] subdirectories = directory.getSubdirectories();
        for (PsiDirectory subdirectory : subdirectories) {
          final PsiPackage aPackage = subdirectory.getPackage();
          if (aPackage != null && !PackageUtil.isPackageDefault(aPackage)) {
            topLevelPackages.add(aPackage);
          } else {
            final Iterator<PsiFile> files = builder.getFiles(subdirectory);
            if (!files.hasNext()) continue;
            TodoDirNode dirNode = new TodoDirNode(project, subdirectory, builder);
            if (!children.contains(dirNode)){
              children.add(dirNode);
            }
          }
        }
        // add non-dir items
        final Iterator<PsiFile> filesUnderDirectory = builder.getFilesUnderDirectory(directory);
        for (;filesUnderDirectory.hasNext();) {
          final PsiFile file = filesUnderDirectory.next();
          TodoFileNode todoFileNode = new TodoFileNode(project, file, builder, false);
          if (!children.contains(todoFileNode)){
            children.add(todoFileNode);
          }
        }
      }
      else {
        // this is the case when a source root has pakage prefix assigned
        PackageElement element = new PackageElement(module, directoryPackage, false);
        TodoPackageNode packageNode = new TodoPackageNode(project, element, builder, directoryPackage.getQualifiedName());
        if (!children.contains(packageNode)) {
          children.add(packageNode);
        }
      }
    }

    GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
    ArrayList<PsiPackage> packages = new ArrayList<PsiPackage>();
    for (PsiPackage psiPackage : topLevelPackages) {
      final PsiPackage aPackage = findNonEmptyPackages(psiPackage, module, project, builder, scope, packages);
      if (aPackage != null){
        packages.add(aPackage);
      }
    }
    for (PsiPackage psiPackage : packages) {
      PackageElement element = new PackageElement(module, psiPackage, false);
      TodoPackageNode packageNode = new TodoPackageNode(project, element, builder, psiPackage.getQualifiedName());
      if (!children.contains(packageNode)) {
        children.add(packageNode);
      }
    }
    roots.removeAll(sourceRoots);
    for (VirtualFile dir : roots) {
      final PsiDirectory directory = psiManager.findDirectory(dir);
      if (directory == null) {
        continue;
      }
      final Iterator<PsiFile> files = builder.getFiles(directory);
      if (!files.hasNext()) continue;
      TodoDirNode dirNode = new TodoDirNode(project, directory, builder);
      if (!children.contains(dirNode)){
        children.add(dirNode);
      }
    }
  }

  @Nullable
  private static PsiPackage findNonEmptyPackages(PsiPackage psiPackage, Module module, Project project, TodoTreeBuilder builder, GlobalSearchScope scope, List<PsiPackage> packages){
    if (!isPackageEmpty(new PackageElement(module, psiPackage, false), builder, project)){
      return psiPackage;
    }
    final PsiPackage[] subPackages = psiPackage.getSubPackages(scope);
    ArrayList<PsiPackage> nonEmptyPackages = new ArrayList<PsiPackage>();
    for (PsiPackage aPackage : subPackages) {
      if (!isPackageEmpty(new PackageElement(module, aPackage, false), builder, project)){
        nonEmptyPackages.add(aPackage);
      }
    }
    if (nonEmptyPackages.size() > 1){
      return psiPackage;
    } else {
      int count = nonEmptyPackages.size();
      PsiPackage pack = count > 0 ? nonEmptyPackages.get(0) : null;
      for (PsiPackage aPackage : subPackages) {
        if (!nonEmptyPackages.contains(aPackage)) {
          PsiPackage pack1 = findNonEmptyPackages(aPackage, module, project, builder, scope, packages);
          if (pack1 != null){
            if (count > 0){
              return psiPackage;
            } else {
              count ++;
              pack = pack1;
            }
          }
        }
      }
      return pack;
    }
  }
}
