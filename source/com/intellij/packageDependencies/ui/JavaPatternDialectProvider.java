/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.packageDependencies.ui;

import com.intellij.ide.util.scopeChooser.GroupByScopeTypeAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.scope.packageSet.PackageSet;
import com.intellij.psi.search.scope.packageSet.PatternPackageSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaPatternDialectProvider extends PatternDialectProvider {
  @NonNls private static final String PACKAGES = "Packages";
  private static final Logger LOG = Logger.getInstance("#" + JavaPatternDialectProvider.class.getName());

  @Nullable
  private static GeneralGroupNode getGroupParent(PackageDependenciesNode node) {
    if (node instanceof GeneralGroupNode) return (GeneralGroupNode)node;
    if (node == null || node instanceof RootNode) return null;
    return getGroupParent((PackageDependenciesNode)node.getParent());
  }

  public PackageSet createPackageSet(final PackageDependenciesNode node, final boolean recursively) {
    GeneralGroupNode groupParent = getGroupParent(node);
    String scope1 = PatternPackageSet.SCOPE_ANY;
    if (groupParent != null) {
      String name = groupParent.toString();
      if (TreeModelBuilder.PRODUCTION_NAME.equals(name)) {
        scope1 = PatternPackageSet.SCOPE_SOURCE;
      }
      else if (TreeModelBuilder.TEST_NAME.equals(name)) {
        scope1 = PatternPackageSet.SCOPE_TEST;
      }
      else if (TreeModelBuilder.LIBRARY_NAME.equals(name)) {
        scope1 = PatternPackageSet.SCOPE_LIBRARY;
      }
    }
    final String scope = scope1;
    if (node instanceof ModuleGroupNode){
      if (!recursively) return null;
      @NonNls final String modulePattern = "group:" + ((ModuleGroupNode)node).getModuleGroup().toString();
      return new PatternPackageSet("*..*", scope, modulePattern);
    } else if (node instanceof ModuleNode) {
      if (!recursively) return null;
      final String modulePattern = ((ModuleNode)node).getModuleName();
      return new PatternPackageSet("*..*", scope, modulePattern);
    }
    else if (node instanceof PackageNode) {
      String pattern = ((PackageNode)node).getPackageQName();
      if (pattern != null) {
        pattern += recursively ? "..*" : ".*";
      }
      else {
        pattern = recursively ? "*..*" : ".*";
      }

      return new PatternPackageSet(pattern, scope, getModulePattern(node));
    }
    else if (node instanceof FileNode) {
      if (recursively) return null;
      FileNode fNode = (FileNode)node;
      final PsiElement element = fNode.getPsiElement();
      StringBuffer buf = new StringBuffer(20);
      if (element instanceof PsiJavaFile) {
        final PsiJavaFile javaFile = (PsiJavaFile)element;
        String packageName = javaFile.getPackageName();
        buf.append(packageName);
        if (buf.length() > 0) {
          buf.append('.');
        }
        final VirtualFile virtualFile = javaFile.getVirtualFile();
        LOG.assertTrue(virtualFile != null);
        buf.append(virtualFile.getNameWithoutExtension());
      }
      if (buf.length() > 0) return new PatternPackageSet(buf.toString(), scope, getModulePattern(node));
    }
    else if (node instanceof GeneralGroupNode) {
      return new PatternPackageSet("*..*", scope, null);
    }

    return null;
  }

  public TreeModel createTreeModel(final Project project, final Marker marker) {
    return TreeModelBuilder.createTreeModel(project, false, false, marker);
  }

  public String getDisplayName() {
    return getShortName();
  }

  @NotNull
  public String getShortName() {
    return PACKAGES;
  }

  public AnAction[] createActions(final Runnable update) {
    return new AnAction[]{new GroupByScopeTypeAction(update)};
  }
}