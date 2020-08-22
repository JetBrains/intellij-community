// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author vlan
 */
public final class PySkeletonsNode extends PsiDirectoryNode {
  private PySkeletonsNode(Project project, @NotNull PsiDirectory value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    data.setPresentableText(PyBundle.message("python.project.view.py.skeletons"));
    data.setIcon(PlatformIcons.LIBRARY_ICON);
  }

  @Nullable
  public static PySkeletonsNode create(@NotNull Project project, @NotNull Sdk sdk, ViewSettings settings) {
    final VirtualFile skeletonsVirtualFile = PythonSdkUtil.findSkeletonsDir(sdk);
    if (skeletonsVirtualFile != null) {
      final PsiDirectory skeletonsDirectory = PsiManager.getInstance(project).findDirectory(skeletonsVirtualFile);
      if (skeletonsDirectory != null) {
        return new PySkeletonsNode(project, skeletonsDirectory, settings);
      }
    }
    return null;
  }
}
