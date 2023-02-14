// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileSystemItemFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.merge;

public class MavenModuleDirectoryNode extends PsiDirectoryNode {
  private final @NlsSafe String myModuleShortName;
  private final boolean appendModuleName;

  MavenModuleDirectoryNode(Project project,
                           @NotNull PsiDirectory psiDirectory,
                           ViewSettings settings,
                           String moduleShortName,
                           PsiFileSystemItemFilter filter) {
    super(project, psiDirectory, settings, filter);
    myModuleShortName = moduleShortName;
    VirtualFile directoryFile = psiDirectory.getVirtualFile();
    appendModuleName = StringUtil.isNotEmpty(myModuleShortName) &&
                       !StringUtil.equalsIgnoreCase(myModuleShortName.replace("-", ""), directoryFile.getName().replace("-", ""));
  }

  @Override
  protected boolean shouldShowModuleName() {
    return !appendModuleName || canRealModuleNameBeHidden();
  }

  @Override
  protected void updateImpl(@NotNull PresentationData data) {
    super.updateImpl(data);
    if (appendModuleName) {
      if (!canRealModuleNameBeHidden()) {
        data.addText("[" + myModuleShortName + "]", REGULAR_BOLD_ATTRIBUTES);
      }
    }
    else {
      List<ColoredFragment> fragments = data.getColoredText();
      ColoredFragment fragment = fragments.iterator().next();
      data.clearText();
      data.addText(fragment.getText().trim(), merge(fragment.getAttributes(), REGULAR_BOLD_ATTRIBUTES));
    }
  }
}