/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.TreeViewUtil;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PackageElementNode extends ProjectViewNode<PackageElement> {
  protected static final Icon PACKAGE_OPEN_ICON = IconLoader.getIcon("/nodes/packageOpen.png");
  protected static final Icon PACKAGE_CLOSED_ICON = IconLoader.getIcon("/nodes/packageClosed.png");

  public PackageElementNode(final Project project,
                          final PackageElement value,
                          final ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public boolean contains(final VirtualFile file) {
    if (!isUnderContent(file)) {
      return false;
    }

    final PsiDirectory[] directories = getValue().getPackage().getDirectories();
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      if (VfsUtil.isAncestor(directory.getVirtualFile(), file, false)) return true;
    }
    return false;
  }

  private boolean isUnderContent(final VirtualFile file) {
    final Module module = getValue().getModule();
    if (module == null) {
      return PackageUtil.projectContainsFile(getProject(), file, getSettings(), isLibraryElement());
    }
    else {
      return PackageUtil.moduleContainsFile(module, file, getSettings(), isLibraryElement());
    }
  }

  private boolean isLibraryElement() {
    return getValue().isLibraryElement();
  }

  public Collection<AbstractTreeNode> getChildren() {
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();

    final PackageElement value = getValue();
    final Module module = value.getModule();
    final PsiPackage aPackage = value.getPackage();

    if (!getSettings().isFlattenPackages()) {

      final PsiPackage[] subpackages = PackageUtil.getSubpackages(aPackage, module, myProject, isLibraryElement());
      for (int idx = 0; idx < subpackages.length; idx++) {
        PackageUtil.addPackageAsChild(children, subpackages[idx], module, getSettings(), isLibraryElement());
      }
    }
    // process only files in package's drectories
    final GlobalSearchScope scopeToShow = PackageUtil.getScopeToShow(myProject, module, isLibraryElement());
    final PsiDirectory[] dirs = aPackage.getDirectories(scopeToShow);
    for (int idx = 0; idx < dirs.length; idx++) {
      final PsiDirectory dir = dirs[idx];
      children.addAll(PackageUtil.getDirectoryChildren(dir, getSettings(), false));
    }
    return children;
  }


  public void update(final PresentationData presentation) {
    if (getValue() != null && getValue().getPackage().isValid()) {
      updateValidData(presentation);
    }
    else {
      setValue(null);
    }
  }

  private void updateValidData(final PresentationData presentation) {
    final PsiPackage aPackage = getValue().getPackage();

    if (!getSettings().isFlattenPackages()) {
      if (getSettings().isHideEmptyMiddlePackages()) {
        if (PackageUtil.isPackageEmpty(aPackage, getValue().getModule(), true, isLibraryElement())) {
          setValue(null);
          return;
        }
      }
    }

    if (showFwName(aPackage)) {
      presentation.setPresentableText((getSettings().isAbbreviatePackageNames()
                                       ? TreeViewUtil.calcAbbreviatedPackageFQName(aPackage)
                                       : aPackage.getQualifiedName()));
    }
    else {
      if (!(getParentValue() instanceof PackageElement)) {
        presentation.setPresentableText(aPackage.getQualifiedName());
      }
      else {
        final PsiPackage parentPackageInTree = ((PackageElement)getParentValue()).getPackage();
        PsiPackage parentPackage = aPackage.getParentPackage();
        final StringBuffer buf = new StringBuffer();
        buf.append(aPackage.getName());
        while (parentPackage != null && !parentPackage.equals(parentPackageInTree)) {
          buf.insert(0, ".");
          buf.insert(0, parentPackage.getName());
          parentPackage = parentPackage.getParentPackage();
        }
        presentation.setPresentableText(buf.toString());
      }
    }

    presentation.setOpenIcon(PackageUtil.PACKAGE_OPEN_ICON);
    presentation.setClosedIcon(PackageUtil.PACKAGE_CLOSED_ICON);
  }

  private boolean showFwName(final PsiPackage aPackage) {
    final boolean showFqName;
    if (!getSettings().isFlattenPackages()) {
      showFqName = false;
    }
    else {
      showFqName = aPackage.getQualifiedName().length() > 0;
    }
    return showFqName;
  }

  public String getTestPresentation() {
    return "PsiPackage: " + getValue().getPackage().getQualifiedName();
  }

  public boolean isFQNameShown() {
    return getValue() == null ? false : showFwName(getValue().getPackage());
  }

  public boolean valueIsCut() {
    return getValue() != null && CopyPasteManager.getInstance().isCutElement(getValue().getPackage());
  }

  public VirtualFile[] getVirtualFiles() {
    final PsiDirectory[] directories = getValue().getPackage().getDirectories(PackageUtil.getScopeToShow(getProject(), getValue().getModule(), isLibraryElement()));
    final VirtualFile[] result = new VirtualFile[directories.length];
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      result[i] = directory.getVirtualFile();
    }
    return result;
  }
}
