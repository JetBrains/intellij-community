package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.EduNames;
import com.jetbrains.edu.coursecreator.CCProjectService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class CCTreeStructureProvider implements TreeStructureProvider, DumbAware {
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
          PsiDirectory directory = (PsiDirectory)node.getValue();
          nodes.add(new CCDirectoryNode(project, directory, settings));
          continue;
        }
        if (node instanceof PsiFileNode) {
          PsiFileNode fileNode = (PsiFileNode)node;
          VirtualFile virtualFile = fileNode.getVirtualFile();
          if (virtualFile == null) {
            continue;
          }
          if (CCProjectService.getInstance(project).isTaskFile(virtualFile)
              || virtualFile.getName().contains(EduNames.WINDOWS_POSTFIX)) {
            continue;
          }
        }
        nodes.add(node);
      }
    }
    return nodes;
  }

  private static boolean needModify(@NotNull final AbstractTreeNode parent) {
    Project project = parent.getProject();
    if (project != null) {
      if (CCProjectService.getInstance(project).getCourse() == null) {
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
