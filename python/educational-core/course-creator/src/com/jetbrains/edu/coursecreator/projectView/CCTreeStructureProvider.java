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
import com.jetbrains.edu.learning.StudyLanguageManager;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;
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
        boolean handled = handleTests(project, virtualFile, psiFile, modifiedChildren, settings);
        if (!handled) {
          modifiedChildren.add(new CCStudentInvisibleFileNode(project, psiFile, settings));
        }
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

  private static boolean handleTests(Project project,
                                     VirtualFile virtualFile,
                                     PsiFile psiFile,
                                     Collection<AbstractTreeNode> modifiedChildren,
                                     ViewSettings settings) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return false;
    }
    if (!CCUtils.isTestsFile(project, virtualFile)) {
      return false;
    }
    VirtualFile taskDir = StudyUtils.getTaskDir(virtualFile);
    if (taskDir == null) {
      return false;
    }
    Task task = StudyUtils.getTask(project, taskDir);
    if (task == null) {
      return false;
    }
    if (isActiveSubtaskTest(task, virtualFile)) {
      StudyLanguageManager manager = StudyUtils.getLanguageManager(course);
      String testsFileName = manager != null ? manager.getTestFileName() : psiFile.getName();
      modifiedChildren.add(new CCStudentInvisibleFileNode(project, psiFile, settings,
                                                          testsFileName));
    }
    return true;
  }

  private static boolean isActiveSubtaskTest(Task task, VirtualFile virtualFile) {
    if (!task.hasSubtasks()) {
      return true;
    }

    boolean isSubtaskTestFile = virtualFile.getName().contains(EduNames.SUBTASK_MARKER);
    if (task.getActiveSubtaskIndex() == 0) {
      return !isSubtaskTestFile;
    }
    if (!isSubtaskTestFile) {
      return false;
    }
    String nameWithoutExtension = virtualFile.getNameWithoutExtension();
    int stepMarkerStart = nameWithoutExtension.indexOf(EduNames.SUBTASK_MARKER);
    int stepIndex = Integer.valueOf(nameWithoutExtension.substring(EduNames.SUBTASK_MARKER.length() + stepMarkerStart));
    return stepIndex == task.getActiveSubtaskIndex();
  }
}
