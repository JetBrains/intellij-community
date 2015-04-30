package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class StudyTreeStructureProvider implements TreeStructureProvider, DumbAware {
  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    if (!isCourseBasedProject(parent)) {
      return children;
    }
    Collection<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
    for (AbstractTreeNode node : children) {
      final Project project = node.getProject();
      if (project != null) {
        if (node.getValue() instanceof PsiDirectory) {
          final PsiDirectory nodeValue = (PsiDirectory)node.getValue();
          if (!nodeValue.getName().contains(EduNames.USER_TESTS) && !nodeValue.getName().equals(".idea")) {
            StudyDirectoryNode newNode = new StudyDirectoryNode(project, nodeValue, settings);
            nodes.add(newNode);
          }
        }
        else {
          if (parent instanceof StudyDirectoryNode && node instanceof PsiFileNode) {
            final PsiFileNode psiFileNode = (PsiFileNode)node;
            final VirtualFile virtualFile = psiFileNode.getVirtualFile();
            if (virtualFile == null) {
              return nodes;
            }
            final TaskFile taskFile = StudyUtils.getTaskFile(project, virtualFile);
            if (taskFile != null) {
              nodes.add(node);
            }
            final String parentName = parent.getName();
            if (parentName != null) {
              if (parentName.equals(EduNames.SANDBOX_DIR)) {
                nodes.add(node);
              }
            }
          }
        }
      }
    }
    return nodes;
  }

  protected boolean isCourseBasedProject(@NotNull final AbstractTreeNode parent) {
    final Project project = parent.getProject();
    if (project != null) {
      final StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(project);
      if (studyTaskManager.getCourse() == null) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}
