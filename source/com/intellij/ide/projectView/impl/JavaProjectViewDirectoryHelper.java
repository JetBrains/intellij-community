/*
 * User: anna
 * Date: 23-Jan-2008
 */
package com.intellij.ide.projectView.impl;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class JavaProjectViewDirectoryHelper extends ProjectViewDirectoryHelper {
  public JavaProjectViewDirectoryHelper(Project project) {
    super(project);
  }

  @Override
  public String getLocationString(@NotNull final PsiDirectory directory, final boolean forceLocation) {
    if (!forceLocation) {
      final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(getProject());
      final String informationString = coverageDataManager.getDirCoverageInformationString(directory);
      if (informationString != null) return informationString;
    }
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (ProjectRootsUtil.isSourceRoot(directory) && aPackage != null) {   //package prefix
      return aPackage.getQualifiedName();
    }
    return super.getLocationString(directory, forceLocation);
  }

  @Override
  public boolean isShowFQName(final ViewSettings settings, final Object parentValue, final PsiDirectory value) {
    PsiPackage aPackage;
        return value != null
               && !(parentValue instanceof Project)
               && settings.isFlattenPackages()
               && (aPackage = JavaDirectoryService.getInstance().getPackage(value)) != null
               && aPackage.getQualifiedName().length() > 0;

  }

  @Nullable
  @Override
  public String getNodeName(final ViewSettings settings, final Object parentValue, final PsiDirectory directory) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);

    if (aPackage != null
        && !ProjectRootsUtil.isSourceRoot(directory)
        && !settings.isFlattenPackages()
        && settings.isHideEmptyMiddlePackages()
        && !ProjectRootsUtil.isLibraryRoot(directory.getVirtualFile(), getProject())
        && TreeViewUtil.isEmptyMiddlePackage(directory, true)) {
      return null;
    }


    PsiPackage parentPackage;
    if (!ProjectRootsUtil.isSourceRoot(directory) && aPackage != null && aPackage.getQualifiedName().length() > 0 &&
                              parentValue instanceof PsiDirectory) {

      parentPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)parentValue));
    }
    else {
      parentPackage = null;
    }

    return PackageUtil.getNodeName(settings, aPackage, parentPackage, directory.getName(), isShowFQName(settings, parentValue, directory));

  }

  @Override
  public boolean skipDirectory(final PsiDirectory directory) {
    return JavaDirectoryService.getInstance().getPackage(directory) == null;
  }

  @Override
  public boolean showFileInLibClasses(final VirtualFile vFile) {
    return !FileIndexUtil.isJavaSourceFile(getProject(), vFile);
  }

  @Override
  public boolean isEmptyMiddleDirectory(final PsiDirectory directory, final boolean strictlyEmpty) {
    return JavaDirectoryService.getInstance().getPackage(directory) != null && TreeViewUtil.isEmptyMiddlePackage(directory, strictlyEmpty);
  }

  @Override
  public boolean canRepresent(final Object element, final PsiDirectory directory) {
    if (super.canRepresent(element, directory)) return true;
    if (element instanceof PackageElement) {
      final PackageElement packageElement = (PackageElement)element;
      return Arrays.asList(packageElement.getPackage().getDirectories()).contains(directory);
    }
    return false;
  }
}