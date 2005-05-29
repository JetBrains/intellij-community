package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Iterator;

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
                                           Iterator files,
                                           Module module,
                                           TodoTreeBuilder builder,
                                           Project project) {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final PsiManager psiManager = PsiManager.getInstance(project);
    for (Iterator i = files; i.hasNext();) {
      final PsiFile psiFile = (PsiFile)i.next();
      if (psiFile == null) { // skip invalid PSI files
        continue;
      }
      final VirtualFile virtualFile = psiFile.getVirtualFile();
      final PsiDirectory containingDirectory = psiFile.getContainingDirectory();
      PsiPackage aPackage = containingDirectory != null ? containingDirectory.getPackage() : null;
      PsiPackage psiPackage = containingDirectory != null ? containingDirectory.getPackage() : null;
      if (psiPackage != null) {
        if (PackageUtil.isPackageDefault(psiPackage)){
          TodoFileNode fileNode = new TodoFileNode(project, psiFile, builder, false);
          if (!children.contains(fileNode)){
            children.add(fileNode);
          }
        } else {
          while (psiPackage.getParentPackage() != null &&
                 !PackageUtil.isPackageDefault(psiPackage.getParentPackage()) &&
                 psiManager.findPackage(psiPackage.getParentPackage().getQualifiedName()) != null) {  //check for package prefix
            psiPackage = psiPackage.getParentPackage();
          }
          PackageElement element = new PackageElement(module, psiPackage, false);
          if (!builder.getTodoTreeStructure().getIsFlattenPackages() || !TodoPackageUtil.isPackageEmpty(element, builder, project)) {
            TodoPackageNode rootPackageNode = new TodoPackageNode(project, element, builder);
            if (psiPackage != null && !children.contains(rootPackageNode)) {
              children.add(rootPackageNode);
            }
          }
          else {
            while (TodoPackageUtil.isPackageEmpty(element, builder, project)) {
              final PsiPackage[] subPackages = psiPackage.getSubPackages(module != null
                                                                         ? GlobalSearchScope.moduleScope(module)
                                                                         : GlobalSearchScope.projectScope(project));
              for (PsiPackage pack : subPackages) {
                if (PsiTreeUtil.isAncestor(pack, aPackage, false)) {
                  psiPackage = pack;
                  element = new PackageElement(module, psiPackage, false);
                  break;
                }
              }
            }
            TodoPackageNode rootPackageNode = new TodoPackageNode(project, element, builder);
            if (psiPackage != null && !children.contains(rootPackageNode)) {
              children.add(rootPackageNode);
            }
          }
        }
      }
      else {
        final VirtualFile contentRoot = projectFileIndex.getContentRootForFile(virtualFile);
        if (contentRoot != null) {
          PsiDirectory rootDirectory = psiManager.findDirectory(contentRoot);
          TodoDirNode projectRootNode = new TodoDirNode(project, rootDirectory, builder);
          if (!builder.getTodoTreeStructure().getIsFlattenPackages() || !builder.isDirectoryEmpty(rootDirectory)) {
            if (!children.contains(projectRootNode)) children.add(projectRootNode);
          }
          else {
            while (builder.isDirectoryEmpty(rootDirectory)) {
              PsiDirectory[] subdirectories = rootDirectory.getSubdirectories();
              for (PsiDirectory directory : subdirectories) {
                if (PsiTreeUtil.isAncestor(directory, containingDirectory, false)) {
                  rootDirectory = directory;
                  break;
                }
              }
            }
            projectRootNode = new TodoDirNode(project, rootDirectory, builder);
            if (!children.contains(projectRootNode)) children.add(projectRootNode);
          }
        }
      }
    }
  }
}
