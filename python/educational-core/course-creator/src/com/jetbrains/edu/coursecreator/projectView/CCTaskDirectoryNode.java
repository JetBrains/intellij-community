package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyLanguageManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.projectView.TaskDirectoryNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCTaskDirectoryNode extends TaskDirectoryNode {
  public CCTaskDirectoryNode(@NotNull Project project,
                             PsiDirectory value,
                             ViewSettings viewSettings,
                             @NotNull Task task) {
    super(project, value, viewSettings, task);
  }

  @Nullable
  @Override
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    AbstractTreeNode node = super.modifyChildNode(childNode);
    if (node != null) {
      return node;
    }
    Object value = childNode.getValue();
    if (value instanceof PsiElement) {
      PsiFile psiFile = ((PsiElement)value).getContainingFile();
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      if (StudyUtils.isTaskDescriptionFile(virtualFile.getName())) {
        return null;
      }
      if (!CCUtils.isTestsFile(myProject, virtualFile) || !myTask.hasSubtasks()) {
        return new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings);
      }

      Course course = StudyTaskManager.getInstance(myProject).getCourse();
      if (course == null) {
        return null;
      }
      StudyLanguageManager manager = StudyUtils.getLanguageManager(course);
      if (manager == null) {
        return new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings);
      }
      String testFileName = manager.getTestFileName();
      return isActiveSubtaskTest(virtualFile) ? new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings, testFileName) : null;
    }
    return null;
  }

  private boolean isActiveSubtaskTest(VirtualFile virtualFile) {
    if (!myTask.hasSubtasks()) {
      return true;
    }

    boolean isSubtaskTestFile = virtualFile.getName().contains(EduNames.SUBTASK_MARKER);
    if (myTask.getActiveSubtaskIndex() == 0) {
      return !isSubtaskTestFile;
    }
    if (!isSubtaskTestFile) {
      return false;
    }
    String nameWithoutExtension = virtualFile.getNameWithoutExtension();
    int stepMarkerStart = nameWithoutExtension.indexOf(EduNames.SUBTASK_MARKER);
    int stepIndex = Integer.valueOf(nameWithoutExtension.substring(EduNames.SUBTASK_MARKER.length() + stepMarkerStart));
    return stepIndex == myTask.getActiveSubtaskIndex();
  }
}
