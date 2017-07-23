package com.jetbrains.edu.learning.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.JBColor;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Lesson;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import icons.EducationalCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LessonDirectoryNode extends StudyDirectoryNode {
  @NotNull protected final Project myProject;
  protected final ViewSettings myViewSettings;
  @NotNull protected final Lesson myLesson;

  public LessonDirectoryNode(@NotNull Project project,
                             PsiDirectory value,
                             ViewSettings viewSettings,
                             @NotNull Lesson lesson) {
    super(project, value, viewSettings);
    myProject = project;
    myViewSettings = viewSettings;
    myLesson = lesson;
  }

  @Override
  protected void updateImpl(PresentationData data) {
    StudyStatus status = myLesson.getStatus();
    boolean isSolved = status != StudyStatus.Solved;
    JBColor color = isSolved ? JBColor.BLACK : LIGHT_GREEN;
    Icon icon = isSolved ? EducationalCoreIcons.Lesson : EducationalCoreIcons.LessonCompl;
    updatePresentation(data, myLesson.getName(), color, icon, null);
  }

  @Override
  public int getWeight() {
    return myLesson.getIndex();
  }

  @Nullable
  @Override
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    Object value = childNode.getValue();
    if (value instanceof PsiDirectory) {
      PsiDirectory directory = (PsiDirectory)value;
      Task task = myLesson.getTask(directory.getName());
      if (task == null) {
        return null;
      }
      VirtualFile srcDir = directory.getVirtualFile().findChild(EduNames.SRC);
      if (srcDir != null) {
        directory = PsiManager.getInstance(myProject).findDirectory(srcDir);
        if (directory == null) {
          return null;
        }
      }
      return createChildDirectoryNode(task, directory);
    }
    return null;
  }

  @Override
  public PsiDirectoryNode createChildDirectoryNode(StudyItem item, PsiDirectory directory) {
    return new TaskDirectoryNode(myProject, directory, myViewSettings, ((Task)item));
  }
}
