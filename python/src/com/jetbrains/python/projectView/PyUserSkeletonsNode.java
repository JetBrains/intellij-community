// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public final class PyUserSkeletonsNode extends PsiDirectoryNode {
  private PyUserSkeletonsNode(Project project, @NotNull PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    data.setPresentableText(PyBundle.message("python.project.view.user.skeletons.node"));
    data.setIcon(PlatformIcons.LIBRARY_ICON);
  }

  @Nullable
  public static PyUserSkeletonsNode create(@NotNull Project project, ViewSettings viewSettings) {
    final VirtualFile userSkeletonsVirtualFile = PyUserSkeletonsUtil.getUserSkeletonsDirectory();
    if (userSkeletonsVirtualFile != null) {
      final PsiDirectory userSkeletonsDirectory = PsiManager.getInstance(project).findDirectory(userSkeletonsVirtualFile);
      if (userSkeletonsDirectory != null) {
        return new PyUserSkeletonsNode(project, userSkeletonsDirectory, viewSettings);
      }
    }
    return null;
  }
}
