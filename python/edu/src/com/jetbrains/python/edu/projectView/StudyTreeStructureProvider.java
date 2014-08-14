package com.jetbrains.python.edu.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.Task;
import com.jetbrains.python.edu.course.TaskFile;
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
    if (!needModify(parent)) {
      return children;
    }
    Collection<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
    for (AbstractTreeNode node : children) {
      Project project = node.getProject();
      if (project != null) {
        if (node.getValue() instanceof PsiDirectory) {
          PsiDirectory nodeValue = (PsiDirectory)node.getValue();
          if (!nodeValue.getName().contains(Task.USER_TESTS)) {
            StudyDirectoryNode newNode = new StudyDirectoryNode(project, nodeValue, settings);
            nodes.add(newNode);
          }
        }
        else {
          if (parent instanceof StudyDirectoryNode) {
            if (node instanceof PsiFileNode) {
              PsiFileNode psiFileNode = (PsiFileNode)node;
              VirtualFile virtualFile = psiFileNode.getVirtualFile();
              if (virtualFile == null) {
                return nodes;
              }
              TaskFile taskFile = StudyTaskManager.getInstance(project).getTaskFile(virtualFile);
              if (taskFile != null) {
                nodes.add(node);
              }
              String parentName = parent.getName();
              if (parentName != null) {
                if (parentName.equals(Course.PLAYGROUND_DIR)) {
                  nodes.add(node);
                }
              }
            }
          }
        }
      }
    }
    return nodes;
  }

  private static boolean needModify(AbstractTreeNode parent) {
    Project project = parent.getProject();
    if (project != null) {
      StudyTaskManager studyTaskManager = StudyTaskManager.getInstance(project);
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
