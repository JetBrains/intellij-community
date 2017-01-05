package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SandboxDirectoryNode extends StudyDirectoryNode {
  public SandboxDirectoryNode(@NotNull Project project,
                              PsiDirectory value,
                              ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Nullable
  @Override
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    return childNode;
  }

  @Override
  public PsiDirectoryNode createChildDirectoryNode(StudyItem item, PsiDirectory value) {
    return null;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    setPresentation(data, EduNames.SANDBOX_DIR, InteractiveLearningIcons.Sandbox);
  }

  @Override
  public int getWeight() {
    return Integer.MAX_VALUE;
  }
}
