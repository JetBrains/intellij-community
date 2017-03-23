package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.JBColor;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.projectView.CourseDirectoryNode;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCCourseDirectoryNode extends CourseDirectoryNode {

  public CCCourseDirectoryNode(@NotNull Project project,
                               PsiDirectory value,
                               ViewSettings viewSettings,
                               @NotNull Course course) {
    super(project, value, viewSettings, course);
  }

  @Nullable
  @Override
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    AbstractTreeNode node = super.modifyChildNode(childNode);
    if (node != null) {
      return node;
    }
    if (childNode instanceof PsiFileNode) {
      VirtualFile virtualFile = ((PsiFileNode)childNode).getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      if (FileUtilRt.getExtension(virtualFile.getName()).equals("iml")) {
        return null;
      }
      return new CCStudentInvisibleFileNode(myProject, ((PsiFileNode)childNode).getValue(), myViewSettings);
    }
    return null;
  }

  @Override
  public PsiDirectoryNode createChildDirectoryNode(StudyItem item, PsiDirectory directory) {
    return new CCLessonDirectoryNode(myProject, directory, myViewSettings, ((Lesson)item));
  }

  @Override
  protected void updateImpl(PresentationData data) {
    updatePresentation(data, myCourse.getName(), JBColor.black, EducationalCoreIcons.Course, "Course Creation");
  }
}
