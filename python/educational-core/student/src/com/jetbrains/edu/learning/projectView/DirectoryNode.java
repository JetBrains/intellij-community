package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DirectoryNode extends StudyDirectoryNode {

  @NotNull protected final Project myProject;
  protected final ViewSettings myViewSettings;

  public DirectoryNode(@NotNull Project project,
                       PsiDirectory value,
                       ViewSettings viewSettings) {
    super(project, value, viewSettings);
    myProject = project;
    myViewSettings = viewSettings;
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Nullable
  @Override
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    Object value = childNode.getValue();
    if (value instanceof PsiDirectory) {
      return createChildDirectoryNode(null, (PsiDirectory)value);
    }
    if (value instanceof PsiElement) {
      PsiFile psiFile = ((PsiElement) value).getContainingFile();
      if (psiFile == null) return null;
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      return StudyUtils.getTaskFile(myProject, virtualFile) != null ? childNode : null;
    }
    return null;
  }

  @Override
  public PsiDirectoryNode createChildDirectoryNode(StudyItem item, PsiDirectory value) {
    return new DirectoryNode(myProject, value, myViewSettings);
  }
}
