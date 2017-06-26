package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
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
      PsiElement psiElement = (PsiElement) value;
      PsiFile psiFile = psiElement.getContainingFile();
      VirtualFile virtualFile = psiFile.getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      Course course = StudyTaskManager.getInstance(myProject).getCourse();
      if (course == null) {
        return null;
      }
      EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());
      if (configurator == null) {
        return new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings);
      }
      if (!CCUtils.isTestsFile(myProject, virtualFile)) {
        return new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings);
      }
      if (!(myTask instanceof TaskWithSubtasks)) {
        return new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings, getTestNodeName(configurator, psiElement));
      }
      String testFileName = getTestNodeName(configurator, psiElement);
      return isActiveSubtaskTest(virtualFile) ? new CCStudentInvisibleFileNode(myProject, psiFile, myViewSettings, testFileName) : null;
    }
    return null;
  }

  @NotNull
  private static String getTestNodeName(EduPluginConfigurator configurator, PsiElement psiElement) {
    String defaultTestName = configurator.getTestFileName();
    if (psiElement instanceof PsiFile) {
      return defaultTestName;
    }
    if (psiElement instanceof PsiNamedElement) {
      String name = ((PsiNamedElement)psiElement).getName();
      return name != null ? name : defaultTestName;
    }
    return defaultTestName;
  }

  private boolean isActiveSubtaskTest(VirtualFile virtualFile) {
    if (!(myTask instanceof TaskWithSubtasks)) {
      return true;
    }

    if (!virtualFile.getName().contains(EduNames.SUBTASK_MARKER)) {
      return false;
    }
    String nameWithoutExtension = virtualFile.getNameWithoutExtension();
    int stepMarkerStart = nameWithoutExtension.indexOf(EduNames.SUBTASK_MARKER);
    int stepIndex = Integer.valueOf(nameWithoutExtension.substring(EduNames.SUBTASK_MARKER.length() + stepMarkerStart));
    return stepIndex == ((TaskWithSubtasks)myTask).getActiveSubtaskIndex();
  }

  @Override
  public PsiDirectoryNode createChildDirectoryNode(StudyItem item, PsiDirectory value) {
    return new CCDirectoryNode(myProject, value, myViewSettings);
  }
}
