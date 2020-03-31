/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class PyUserSkeletonsNode extends PsiDirectoryNode {
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
