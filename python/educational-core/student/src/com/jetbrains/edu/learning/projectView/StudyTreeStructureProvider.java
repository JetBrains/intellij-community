package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.learning.courseFormat.Course;
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
    Project project = parent.getProject();
    if (project == null || !shouldModify(project)) {
      return children;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return children;
    }
    Collection<AbstractTreeNode> modifiedNodes = new ArrayList<>();
    for (AbstractTreeNode node : children) {
      if (parent instanceof ProjectViewProjectNode && node instanceof PsiDirectoryNode) {
        modifiedNodes.add(createCourseNode(project, node, settings, course));
        continue;
      }
      if (parent instanceof StudyDirectoryNode) {
        AbstractTreeNode modifiedNode = ((StudyDirectoryNode)parent).modifyChildNode(node);
        if (modifiedNode != null) {
          modifiedNodes.add(modifiedNode);
        }
      }
    }
    return modifiedNodes;
  }

  @NotNull
  protected CourseDirectoryNode createCourseNode(Project project, AbstractTreeNode node, ViewSettings settings, Course course) {
    return new CourseDirectoryNode(project, ((PsiDirectory)node.getValue()), settings, course);
  }

  protected boolean shouldModify(@NotNull final Project project) {
    return StudyUtils.isStudentProject(project);
  }

  @Nullable
  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}
