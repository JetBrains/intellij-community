package com.jetbrains.edu.coursecreator.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.EduPluginConfigurator;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyItem;
import com.jetbrains.edu.learning.projectView.DirectoryNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CCDirectoryNode extends DirectoryNode {
  public CCDirectoryNode(@NotNull Project project,
                         PsiDirectory value,
                         ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Nullable
  @Override
  public AbstractTreeNode modifyChildNode(AbstractTreeNode childNode) {
    final AbstractTreeNode node = super.modifyChildNode(childNode);
    if (node != null) return node;
    Object value = childNode.getValue();
    if (value instanceof PsiElement) {
      PsiFile psiFile = ((PsiElement) value).getContainingFile();
      VirtualFile virtualFile = psiFile.getVirtualFile();

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
    }
    return null;
  }

  @Override
  public PsiDirectoryNode createChildDirectoryNode(StudyItem item, PsiDirectory value) {
    return new CCDirectoryNode(myProject, value, myViewSettings);
  }
}
