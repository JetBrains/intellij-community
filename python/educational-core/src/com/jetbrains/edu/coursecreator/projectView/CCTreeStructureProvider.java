package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.projectView.CourseDirectoryNode;
import com.jetbrains.edu.learning.projectView.StudyTreeStructureProvider;
import org.jetbrains.annotations.NotNull;

public class CCTreeStructureProvider extends StudyTreeStructureProvider {
  @Override
  protected boolean shouldModify(@NotNull Project project) {
    return CCUtils.isCourseCreator(project);
  }

  @NotNull
  @Override
  protected CourseDirectoryNode createCourseNode(Project project, AbstractTreeNode node, ViewSettings settings, Course course) {
    return new CCCourseDirectoryNode(project, ((PsiDirectory)node.getValue()), settings, course);
  }
}
