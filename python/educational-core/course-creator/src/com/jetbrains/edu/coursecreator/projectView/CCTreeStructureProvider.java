package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.projectView.StudyTreeStructureProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class CCTreeStructureProvider extends StudyTreeStructureProvider {
  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    if (!needModify(parent)) {
      return children;
    }
    Collection<AbstractTreeNode> modifiedChildren = super.modify(parent, children, settings);

    for (AbstractTreeNode node : children) {
      Project project = node.getProject();
      if (project == null) {
        continue;
      }
      if (node.getValue() instanceof PsiDirectory) {
        String name = ((PsiDirectory)node.getValue()).getName();
        if ("zip".equals(FileUtilRt.getExtension(name))) {
          modifiedChildren.add(node);
          continue;
        }
      }
      if (node instanceof PsiFileNode) {
        PsiFileNode fileNode = (PsiFileNode)node;
        VirtualFile virtualFile = fileNode.getVirtualFile();
        if (virtualFile == null) {
          continue;
        }
        if (StudyUtils.getTaskFile(project, virtualFile) != null || StudyUtils.isTaskDescriptionFile(virtualFile.getName())) {
          continue;
        }
        PsiFile psiFile = ((PsiFileNode)node).getValue();
        modifiedChildren.add(new CCStudentInvisibleFileNode(project, psiFile, settings));
      }
    }
    return modifiedChildren;
  }

  protected boolean needModify(@NotNull final AbstractTreeNode parent) {
    Project project = parent.getProject();
    if (project == null) {
      return false;
    }
    return CCUtils.isCourseCreator(project);
  }
}
